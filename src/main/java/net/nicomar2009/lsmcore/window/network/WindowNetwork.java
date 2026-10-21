package net.nicomar2009.lsmcore.window.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.nicomar2009.lsmcore.window.server.WindowServer;

public final class WindowNetwork {
    private WindowNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("2");
        registrar.playToServer(WindowRequest.TYPE,WindowRequest.CODEC,(request,context)-> {
            if (context.player() instanceof ServerPlayer player) WindowServer.handle(player,request);
        }); // Default registrar handler thread is MAIN.
        registrar.playToClient(WindowReply.TYPE,WindowReply.CODEC);
        registrar.playToClient(AssemblyUpdate.TYPE,AssemblyUpdate.CODEC);
    }
}
