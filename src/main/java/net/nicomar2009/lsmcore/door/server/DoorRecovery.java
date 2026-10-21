package net.nicomar2009.lsmcore.door.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Event-driven bounded recovery queue, EMPTY in steady state. Chunk Load fires before FULL:
 * enqueue only, then inspect next tick. At most FOUR structures (bounded cell checks) per tick, no
 * geometry recalculation/search or recurring poll. Missing chunks wait for another Load event.
 */
public final class DoorRecovery {
    private record Work(ServerLevel level,UUID id,int due) {}
    private static final Map<MinecraftServer,LinkedHashMap<String,Work>> QUEUES=new IdentityHashMap<>();
    private DoorRecovery() {}
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk=event.getChunk().getPos();
        level.getServer().execute(()-> {
            var store=DoorState.of(level); if (!store.readable()) return;
            var queue=QUEUES.computeIfAbsent(level.getServer(),key->new LinkedHashMap<>());
            for (var view:store.inChunk(chunk)) {
                String key=level.dimension().identifier()+"/"+view.record().id();
                queue.putIfAbsent(key,new Work(level,view.record().id(),level.getServer().getTickCount()+1));
            }
        });
    }
    public static void tick(ServerTickEvent.Post event) {
        var queue=QUEUES.get(event.getServer()); if (queue==null) return;
        var ready=new ArrayList<Work>(); var iterator=queue.entrySet().iterator();
        while (iterator.hasNext() && ready.size()<4) {
            var work=iterator.next().getValue(); if (event.getServer().getTickCount()-work.due()<0) break;
            ready.add(work); iterator.remove();
        }
        if (queue.isEmpty()) QUEUES.remove(event.getServer());
        for (var work:ready) try { DoorService.recover(work.level(),work.id()); }
        catch (java.io.IOException | RuntimeException error) {
            // Preserve the journal. Retry explicitly after fixing I/O/protection/adapter failures.
            com.mojang.logging.LogUtils.getLogger().error("Door recovery remains pending for {}",work.id(),error);
        }
    }
    public static void stopped(MinecraftServer server) { QUEUES.remove(server); }
}
