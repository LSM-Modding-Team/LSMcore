package net.nicomar2009.lsmcore.door.server;

import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicomar2009.lsmcore.door.network.*;

public final class DoorRequests {
    private static final Map<ServerPlayer,Long> LAST=new IdentityHashMap<>();
    private DoorRequests() {}
    public static void handle(ServerPlayer player,DoorRequest request) {
        String code="ok"; long revision=0;
        try {
            if (!player.level().dimension().identifier().toString().equals(request.dimension())) throw new IllegalArgumentException("stale_dimension");
            long now=System.nanoTime(); Long previous=LAST.get(player);
            if (previous!=null && now-previous<100_000_000L) throw new IllegalArgumentException("rate_limited");
            LAST.put(player,now);
            var result=DoorService.manual(player,request.structure(),request.clicked(),request.expected(),request.hand()); code=result.code(); revision=result.revision();
        } catch (IllegalArgumentException error) { code=error.getMessage()!=null && error.getMessage().matches("[a-z_]{1,64}")?error.getMessage():"invalid"; }
        catch (IOException error) { code="storage_unavailable"; com.mojang.logging.LogUtils.getLogger().error("Door persistence failed",error); }
        catch (RuntimeException error) { code="integration_error"; com.mojang.logging.LogUtils.getLogger().error("Door interaction failed",error); }
        // Every legitimate request, including rate-limit/stale/permission errors, receives its correlated reply.
        PacketDistributor.sendToPlayer(player,new DoorReply(request.session(),request.serial(),request.dimension(),request.structure(),code,revision));
    }
    public static void logout(ServerPlayer player) { LAST.remove(player); DoorSync.logout(player); }
    public static void stopped(MinecraftServer server) {
        LAST.keySet().removeIf(player->player.level().getServer()==server); DoorSync.stopped(server); DoorRecovery.stopped(server); DoorState.stopped(server);
    }
}
