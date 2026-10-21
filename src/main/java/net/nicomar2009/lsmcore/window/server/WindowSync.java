package net.nicomar2009.lsmcore.window.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicomar2009.lsmcore.window.integration.*;
import net.nicomar2009.lsmcore.window.network.AssemblyUpdate;
import net.nicomar2009.lsmcore.window.storage.*;

/** Only watchers of affected chunks receive placed records. Catalogs/policy are editor responses only. */
public final class WindowSync {
    private static final Map<ServerPlayer,Map<String,Set<ChunkPos>>> WATCHES=new IdentityHashMap<>();
    private WindowSync() {}
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    public static void sent(ChunkWatchEvent.Sent event) {
        var level=event.getLevel(); var player=event.getPlayer(); var chunk=event.getPos();
        WATCHES.computeIfAbsent(player,p->new HashMap<>()).computeIfAbsent(dimension(level),d->new HashSet<>()).add(chunk);
        var store=WindowState.of(level.getServer()).assemblies(level); long revision=store.revision();
        if (!store.readable()) { send(player,level,chunk,revision,AssemblyUpdate.BLOCKED,""); return; }
        send(player,level,chunk,revision,AssemblyUpdate.BEGIN,"");
        for (var record:store.inChunk(chunk)) send(player,level,chunk,revision,AssemblyUpdate.ENTRY,AssemblyJson.write(record).toString());
        send(player,level,chunk,revision,AssemblyUpdate.END,"");
    }
    public static void unwatch(ChunkWatchEvent.UnWatch event) {
        var dimensions=WATCHES.get(event.getPlayer());
        if (dimensions!=null) {
            var chunks=dimensions.get(dimension(event.getLevel())); if (chunks!=null) chunks.remove(event.getPos());
        }
        send(event.getPlayer(),event.getLevel(),event.getPos(),0,AssemblyUpdate.UNWATCH,"");
    }
    public static void logout(ServerPlayer player) { WATCHES.remove(player); }
    public static void stopped(MinecraftServer server) { WATCHES.keySet().removeIf(player->player.level().getServer()==server); }
    private static void send(ServerPlayer player,ServerLevel level,ChunkPos chunk,long revision,int kind,String json) {
        PacketDistributor.sendToPlayer(player,new AssemblyUpdate(dimension(level),chunk.x(),chunk.z(),revision,kind,json));
    }
    private static void watchers(ServerLevel level,ChunkPos chunk,int kind,String json) {
        long revision=WindowState.of(level.getServer()).assemblies(level).revision();
        WATCHES.forEach((player,dimensions)-> {
            if (player.level()==level && dimensions.getOrDefault(dimension(level),Set.of()).contains(chunk)) send(player,level,chunk,revision,kind,json);
        });
    }
    public static void changed(ServerLevel level,PlacedAssembly old,PlacedAssembly next,Set<BlockPos> affected) {
        Set<ChunkPos> previous=old==null?Set.of():old.chunks(),current=next==null?Set.of():next.chunks();
        if (old!=null) for (ChunkPos chunk:previous) if (!current.contains(chunk)) watchers(level,chunk,AssemblyUpdate.REMOVE,old.id().toString());
        if (next!=null) for (ChunkPos chunk:current) watchers(level,chunk,AssemblyUpdate.UPSERT,AssemblyJson.write(next).toString());
        invalidate(level,affected);
    }
    public static void invalidate(ServerLevel level,Collection<BlockPos> affected) {
        Set<ChunkPos> chunks=new HashSet<>();
        for (BlockPos pos:affected) {
            chunks.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
            if (level.hasChunkAt(pos)) WindowIntegration.adapter(level.getBlockState(pos).getBlock()).ifPresent(adapter->adapter.geometryChanged(level,pos));
        }
        for (ChunkPos chunk:chunks) {
            watchers(level,chunk,AssemblyUpdate.INVALIDATE,""); NeoForge.EVENT_BUS.post(new WindowGeometryChangedEvent(level,chunk));
        }
    }
}
