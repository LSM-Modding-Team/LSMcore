package net.nicomar2009.lsmcore.door.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.nicomar2009.lsmcore.door.server.DoorRequests;

public final class DoorNetwork {
    private DoorNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1"); // MAIN handlers, never touch world state on network threads.
        registrar.playToServer(DoorRequest.TYPE,DoorRequest.CODEC,(packet,context)-> {
            if (context.player() instanceof ServerPlayer player) DoorRequests.handle(player,packet);
        });
        registrar.playToClient(DoorUpdate.TYPE,DoorUpdate.CODEC);
        registrar.playToClient(DoorReply.TYPE,DoorReply.CODEC);
    }
}
