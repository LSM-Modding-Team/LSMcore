package net.nicomar2009.lsmcore.window.client;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.nicomar2009.lsmcore.window.detection.*;
import net.nicomar2009.lsmcore.window.geometry.WindowResolver;
import net.nicomar2009.lsmcore.window.integration.WindowGeometryChangedEvent;
import net.nicomar2009.lsmcore.window.model.EffectiveWindow;
import net.nicomar2009.lsmcore.window.network.AssemblyUpdate;
import net.nicomar2009.lsmcore.window.storage.*;

/** Observed chunks only. Consumers cache query/fragments until WindowGeometryChangedEvent.
 * Never call query once per frame/tick. Missing/in-flight/invalid replica is incomplete, not empty.
 */
public final class ClientWindowViews {
    private static ClientLevel world;
    private static final Map<ChunkPos,View> CHUNKS=new HashMap<>();
    private static final class View {
        long revision; boolean ready,blocked;
        final Map<UUID,PlacedAssembly> records=new LinkedHashMap<>();
        final Map<BlockPos,UUID> members=new HashMap<>();
        void index(ChunkPos chunk) {
            members.clear();
            for (var record:records.values()) for (BlockPos pos:record.cells()) if (new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4).equals(chunk)) {
                if (members.putIfAbsent(pos,record.id())!=null) throw new IllegalArgumentException("Overlapping replica");
            }
        }
    }
    private ClientWindowViews() {}
    public static void clear() { world=null; CHUNKS.clear(); }
    private static void attach(ClientLevel level) { if (world!=level) { clear(); world=level; } }
    public static void accept(AssemblyUpdate packet) {
        var level=Minecraft.getInstance().level;
        if (level==null || !level.dimension().identifier().toString().equals(packet.dimension())) return;
        attach(level); ChunkPos chunk=new ChunkPos(packet.x(),packet.z());
        if (packet.kind()==AssemblyUpdate.UNWATCH) { CHUNKS.remove(chunk); invalidate(level,chunk); availability(level,chunk); return; }
        if (CHUNKS.size()>=8192 && !CHUNKS.containsKey(chunk)) return;
        View view=CHUNKS.computeIfAbsent(chunk,key->new View());
        if (packet.revision()<view.revision) return;
        view.revision=packet.revision();
        try {
            switch (packet.kind()) {
                case AssemblyUpdate.BEGIN -> { view.ready=false; view.blocked=false; view.records.clear(); view.members.clear(); }
                case AssemblyUpdate.ENTRY,AssemblyUpdate.UPSERT -> {
                    var record=AssemblyJson.read(JsonParser.parseString(packet.json()).getAsJsonObject());
                    if (!record.chunks().contains(chunk) || view.records.size()>=4096 && !view.records.containsKey(record.id()))
                        throw new IllegalArgumentException("Invalid replica bounds");
                    var old=view.records.put(record.id(),record);
                    if (packet.kind()==AssemblyUpdate.UPSERT) {
                        if (old!=null) for (BlockPos pos:old.cells()) view.members.remove(pos,old.id());
                        for (BlockPos pos:record.cells()) if (new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4).equals(chunk)) {
                            UUID member=view.members.putIfAbsent(pos,record.id());
                            if (member!=null && !member.equals(record.id())) throw new IllegalArgumentException("Overlapping replica");
                        }
                    }
                }
                case AssemblyUpdate.END -> { view.index(chunk); view.ready=true; }
                case AssemblyUpdate.REMOVE -> {
                    var old=view.records.remove(UUID.fromString(packet.json()));
                    if (old!=null) for (BlockPos pos:old.cells()) view.members.remove(pos,old.id());
                }
                case AssemblyUpdate.BLOCKED -> { view.blocked=true; view.ready=false; view.records.clear(); view.members.clear(); }
                case AssemblyUpdate.INVALIDATE -> { }
                default -> throw new IllegalArgumentException("Unknown replica operation");
            }
        } catch (RuntimeException invalid) { view.blocked=true; view.ready=false; }
        if (packet.kind()!=AssemblyUpdate.ENTRY && packet.kind()!=AssemblyUpdate.BEGIN) {
            invalidate(level,chunk);
            if (packet.kind()==AssemblyUpdate.END || packet.kind()==AssemblyUpdate.BLOCKED) availability(level,chunk);
        }
    }
    private static void invalidate(ClientLevel level,ChunkPos chunk) { NeoForge.EVENT_BUS.post(new WindowGeometryChangedEvent(level,chunk)); }
    private static void availability(ClientLevel level,ChunkPos chunk) {
        // Automatic base designs span less than one chunk per axis. A newly known boundary
        // can resolve cached incomplete queries in adjacent chunks without a world scan.
        for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) if (x!=0 || z!=0) {
            ChunkPos neighbor=new ChunkPos(chunk.x()+x,chunk.z()+z);
            if (CHUNKS.containsKey(neighbor)) invalidate(level,neighbor);
        }
    }
    public static EffectiveWindow query(ClientLevel level,BlockPos pos) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Query on the client thread and publish an immutable prepared geometry snapshot");
        attach(level); View start=CHUNKS.get(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        if (start==null || !start.ready || start.blocked)
            return new EffectiveWindow(EffectiveWindow.Mode.UNAVAILABLE,DetectionResult.error(start!=null && start.blocked?"unknown_data":"incomplete"),null,null);
        var base=new WorldSurfaceAccess(level,p-> {
            var view=CHUNKS.get(new ChunkPos(p.getX() >> 4, p.getZ() >> 4));
            if (view==null || !view.ready || view.blocked) throw new IllegalArgumentException("incomplete");
            return Optional.ofNullable(view.members.get(p));
        });
        SurfaceAccess access=new SurfaceAccess() {
            public boolean loaded(BlockPos p) {
                var v=CHUNKS.get(new ChunkPos(p.getX() >> 4, p.getZ() >> 4)); return base.loaded(p) && v!=null && v.ready && !v.blocked;
            }
            public Optional<net.nicomar2009.lsmcore.window.integration.WindowCell> cell(BlockPos p) { return base.cell(p); }
            public Optional<UUID> membership(BlockPos p) { return base.membership(p); }
            public boolean permits(BlockPos a,net.nicomar2009.lsmcore.window.integration.WindowCell ac,BlockPos b,net.nicomar2009.lsmcore.window.integration.WindowCell bc) {
                return base.permits(a,ac,b,bc);
            }
        };
        var saved=Optional.ofNullable(start.members.get(pos)).map(start.records::get).orElse(null);
        return WindowResolver.resolve(access,pos,saved,false,pos,pos,new DetectionWork());
    }
}
