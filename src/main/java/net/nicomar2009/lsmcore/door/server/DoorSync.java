package net.nicomar2009.lsmcore.door.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicomar2009.lsmcore.door.model.DoorRecord;
import net.nicomar2009.lsmcore.door.storage.DoorJson;
import net.nicomar2009.lsmcore.door.network.DoorUpdate;

/** No global broadcasts; watchers can reconstruct leaves even when their controller chunk is absent. */
public final class DoorSync {
    private static final Map<ServerPlayer,Map<String,Set<ChunkPos>>> WATCHES=new IdentityHashMap<>();
    private DoorSync() {}
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    public static void sent(ChunkWatchEvent.Sent event) {
        var level=event.getLevel(); var chunk=event.getPos(); var player=event.getPlayer();
        WATCHES.computeIfAbsent(player,p->new HashMap<>()).computeIfAbsent(dimension(level),d->new HashSet<>()).add(chunk);
        var store=DoorState.of(level); long revision=store.revision();
        if (!store.readable()) { send(player,level,chunk,revision,DoorUpdate.BLOCKED,""); return; }
        send(player,level,chunk,revision,DoorUpdate.BEGIN,"");
        for (var view:store.inChunk(chunk)) send(player,level,chunk,revision,DoorUpdate.ENTRY,DoorJson.write(view.record()).toString());
        send(player,level,chunk,revision,DoorUpdate.END,"");
    }
    public static void unwatch(ChunkWatchEvent.UnWatch event) {
        var dimensions=WATCHES.get(event.getPlayer());
        if (dimensions!=null) { var chunks=dimensions.get(dimension(event.getLevel())); if (chunks!=null) chunks.remove(event.getPos()); }
        send(event.getPlayer(),event.getLevel(),event.getPos(),0,DoorUpdate.UNWATCH,"");
    }
    private static void send(ServerPlayer player,ServerLevel level,ChunkPos chunk,long revision,int kind,String json) {
        PacketDistributor.sendToPlayer(player,new DoorUpdate(dimension(level),chunk.x,chunk.z,revision,kind,json));
    }
    public static void changed(ServerLevel level,DoorRecord old,DoorRecord next) {
        var chunks=new HashSet<ChunkPos>(); if (old!=null) chunks.addAll(old.structure().plan().chunks()); if (next!=null) chunks.addAll(next.structure().plan().chunks());
        int kind=next==null?DoorUpdate.REMOVE:old!=null && old.structure()==next.structure()?DoorUpdate.STATE:DoorUpdate.UPSERT;
        String body=next==null?old.id().toString():(kind==DoorUpdate.STATE?DoorJson.writeState(next):DoorJson.write(next)).toString();
        long revision=DoorState.of(level).revision();
        WATCHES.forEach((player,dimensions)-> {
            if (player.level()!=level) return;
            for (ChunkPos chunk:chunks) if (dimensions.getOrDefault(dimension(level),Set.of()).contains(chunk))
                send(player,level,chunk,revision,kind,body);
        });
    }
    public static void logout(ServerPlayer player) { WATCHES.remove(player); }
    public static void stopped(MinecraftServer server) { WATCHES.keySet().removeIf(player->player.level().getServer()==server); }
}
