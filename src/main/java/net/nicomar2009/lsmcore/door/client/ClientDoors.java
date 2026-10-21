package net.nicomar2009.lsmcore.door.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicomar2009.lsmcore.door.model.*;
import net.nicomar2009.lsmcore.door.storage.DoorJson;
import net.nicomar2009.lsmcore.door.network.*;

/** Client-only derived cache. Atomic immutable chunk publication permits render worker reads.
 * Mutations/request submission run on the client thread. No geometry is recalculated per frame.
 */
public final class ClientDoors {
    public enum Availability { UNWATCHED, SYNCHRONIZING, READY, BLOCKED }
    private static final Set<ChunkPos> SYNCING=ConcurrentHashMap.newKeySet(),BLOCKED=ConcurrentHashMap.newKeySet();
    private record ChunkView(long revision,Map<UUID,DoorView> entries,Map<BlockPos,DoorView> members) {}
    private record Pending(long revision,Map<UUID,DoorView> entries) {}
    private static final Map<ChunkPos,ChunkView> CHUNKS=new ConcurrentHashMap<>();
    private static final Map<ChunkPos,Pending> STAGING=new HashMap<>();
    private static final Map<UUID,DoorView> LATEST=new HashMap<>();
    private static final Map<Long,UUID> REQUESTS=new HashMap<>();
    private static UUID session=UUID.randomUUID(); private static long serial;
    private ClientDoors() {}
    /** Distinguishes unavailable/blocked storage from a ready chunk with no door. */
    public static Availability availability(BlockPos pos) {
        var chunk=new ChunkPos(pos);
        return BLOCKED.contains(chunk)?Availability.BLOCKED:SYNCING.contains(chunk)?Availability.SYNCHRONIZING
                :CHUNKS.containsKey(chunk)?Availability.READY:Availability.UNWATCHED;
    }
    /** May return the LAST valid view while synchronizing/blocked; check availability before applying actions. */
    public static Optional<DoorView> at(BlockPos pos) {
        var chunk=CHUNKS.get(new ChunkPos(pos)); return chunk==null?Optional.empty():Optional.ofNullable(chunk.members().get(pos));
    }
    /** Owner client use hook. Pick either this network route OR a server-only vanilla use hook,
     * never intentionally invoke both. Server recomputes ray, permissions and selected leaf.
     */
    public static long requestUse(BlockPos clicked) { return requestUse(clicked,InteractionHand.MAIN_HAND); }
    public static long requestUse(BlockPos clicked,InteractionHand hand) {
        var client=Minecraft.getInstance(); if (client.level==null || client.player==null) throw new IllegalStateException("no_world");
        if (availability(clicked)!=Availability.READY) throw new IllegalArgumentException("not_synchronized");
        var view=at(clicked).orElseThrow(()->new IllegalArgumentException("not_synchronized"));
        if (REQUESTS.size()>=32 || serial==Long.MAX_VALUE) throw new IllegalStateException("too_many_requests");
        Objects.requireNonNull(hand); long number=++serial; REQUESTS.put(number,view.record().id());
        try { PacketDistributor.sendToServer(new DoorRequest(session,number,client.level.dimension().identifier().toString(),view.record().id(),clicked,view.record().revision(),hand)); }
        catch (RuntimeException error) { REQUESTS.remove(number); throw error; }
        return number;
    }
    public static void reply(DoorReply packet) {
        if (!dimension(packet.dimension()) || !session.equals(packet.session()) || !packet.structure().equals(REQUESTS.get(packet.serial()))) return;
        REQUESTS.remove(packet.serial()); NeoForge.EVENT_BUS.post(new DoorReplyEvent(packet));
    }
    public static void accept(DoorUpdate packet) {
        if (!dimension(packet.dimension())) return;
        ChunkPos chunk=new ChunkPos(packet.x(),packet.z()); var current=CHUNKS.get(chunk); var pending=STAGING.get(chunk);
        if (packet.kind()==DoorUpdate.UNWATCH) {
            CHUNKS.remove(chunk); STAGING.remove(chunk); SYNCING.remove(chunk); BLOCKED.remove(chunk); prune(); changed(chunk); return;
        }
        long known=Math.max(current==null?-1:current.revision(),pending==null?-1:pending.revision());
        if (packet.revision()<known) return;
        try {
            switch (packet.kind()) {
                case DoorUpdate.BEGIN -> { STAGING.put(chunk,new Pending(packet.revision(),new HashMap<>())); SYNCING.add(chunk); BLOCKED.remove(chunk); }
                case DoorUpdate.ENTRY -> {
                    if (pending==null || pending.revision()!=packet.revision()) return;
                    var view=decode(packet.json(),chunk);
                    if (pending.entries().size()>=256 || pending.entries().putIfAbsent(view.record().id(),view)!=null) throw new IllegalArgumentException("duplicate_or_excess_entry");
                }
                case DoorUpdate.END -> {
                    if (pending==null || pending.revision()!=packet.revision()) return;
                    publish(chunk,packet.revision(),pending.entries()); STAGING.remove(chunk); prune();
                }
                case DoorUpdate.UPSERT -> {
                    if (current==null) return;
                    var view=decode(packet.json(),chunk); var entries=new HashMap<>(current.entries()); entries.put(view.record().id(),view);
                    publish(chunk,packet.revision(),entries); prune();
                }
                case DoorUpdate.STATE -> {
                    if (current==null) return;
                    var header=com.google.gson.JsonParser.parseString(packet.json()).getAsJsonObject();
                    UUID id=UUID.fromString(header.get("id").getAsString()); var old=current.entries().get(id);
                    if (old==null) throw new IllegalArgumentException("state_without_snapshot");
                    var next=DoorJson.readState(packet.json(),old.record());
                    if (next.revision()<old.record().revision()) throw new IllegalArgumentException("stale_state");
                    var cached=LATEST.get(id);
                    if (cached!=null && cached.record().revision()>next.revision()) throw new IllegalArgumentException("stale_state");
                    var view=cached!=null && cached.record().revision()==next.revision()?cached:DoorView.updated(next,old);
                    LATEST.put(id,view); var entries=new HashMap<>(current.entries()); entries.put(id,view);
                    publish(chunk,packet.revision(),entries); prune();
                }
                case DoorUpdate.REMOVE -> {
                    if (current==null) return;
                    UUID id=UUID.fromString(packet.json()); var entries=new HashMap<>(current.entries()); entries.remove(id);
                    publish(chunk,packet.revision(),entries); prune();
                }
                case DoorUpdate.BLOCKED -> { STAGING.remove(chunk); SYNCING.remove(chunk); BLOCKED.add(chunk); prune(); changed(chunk); }
                default -> throw new IllegalArgumentException("unknown_door_packet");
            }
        } catch (RuntimeException error) {
            // Retain the last valid published chunk; malformed/unknown data is never installed as absence.
            STAGING.remove(chunk); SYNCING.remove(chunk); BLOCKED.add(chunk); prune(); changed(chunk); com.mojang.logging.LogUtils.getLogger().error("Rejected door synchronization",error);
        }
    }
    private static DoorView decode(String json,ChunkPos chunk) {
        var record=DoorJson.read(json); if (!record.structure().plan().chunks().contains(chunk)) throw new IllegalArgumentException("wrong_chunk");
        var old=LATEST.get(record.id());
        if (old!=null && old.record().revision()>record.revision()) throw new IllegalArgumentException("stale_door_record");
        if (old!=null && old.record().revision()==record.revision()) return old;
        var view=DoorView.of(record); LATEST.put(record.id(),view); return view;
    }
    private static void publish(ChunkPos chunk,long revision,Map<UUID,DoorView> entries) {
        var members=new HashMap<BlockPos,DoorView>();
        for (var view:entries.values()) for (BlockPos pos:view.record().structure().plan().slots().keySet()) if (new ChunkPos(pos).equals(chunk)) {
            if (members.putIfAbsent(pos,view)!=null) throw new IllegalArgumentException("overlapping_doors");
        }
        CHUNKS.put(chunk,new ChunkView(revision,Map.copyOf(entries),Map.copyOf(members)));
        SYNCING.remove(chunk); BLOCKED.remove(chunk); changed(chunk);
    }
    private static void prune() {
        LATEST.keySet().removeIf(id->CHUNKS.values().stream().noneMatch(c->c.entries().containsKey(id))
                && STAGING.values().stream().noneMatch(c->c.entries().containsKey(id)));
    }
    private static boolean dimension(String id) {
        var level=Minecraft.getInstance().level; return level!=null && level.dimension().identifier().toString().equals(id);
    }
    private static void changed(ChunkPos chunk) {
        try { NeoForge.EVENT_BUS.post(new DoorClientChangedEvent(chunk)); }
        catch (RuntimeException error) { com.mojang.logging.LogUtils.getLogger().error("Door client invalidation listener failed",error); }
    }
    public static void clear() {
        CHUNKS.clear(); STAGING.clear(); LATEST.clear(); REQUESTS.clear(); SYNCING.clear(); BLOCKED.clear(); session=UUID.randomUUID(); serial=0; changed(null);
    }
}
