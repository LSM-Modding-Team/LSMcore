package net.nicomar2009.lsmcore.window.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.nicomar2009.lsmcore.LSMCore;
import net.nicomar2009.lsmcore.window.network.WindowReply;

/** Only this Dist.CLIENT subscriber references screens or Minecraft client classes. */
@EventBusSubscriber(modid=LSMCore.MOD_ID,value=Dist.CLIENT)
public final class WindowClient {
    private static boolean openRequested;
    private WindowClient() {}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lsmcore_windows").executes(context-> {
            openRequested=true; return 1;
        }));
    }
    @SubscribeEvent public static void nextFrame(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level==null) ClientWindowViews.clear();
        if (!openRequested) return;
        openRequested=false;
        openEditor(); // Defer past ChatScreen closing after command execution.
    }
    /** Consumer-side entry point for a button/key/item. The server still validates every target. */
    public static void openEditor() {
        Minecraft client=Minecraft.getInstance();
        if (client.player==null || client.level==null) return;
        boolean target=client.hitResult instanceof BlockHitResult;
        BlockPos pos=target?((BlockHitResult)client.hitResult).getBlockPos():BlockPos.ZERO;
        client.setScreenAndShow(new WindowEditorScreen(target,pos,client.level.dimension().identifier().toString()));
    }
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(net.nicomar2009.lsmcore.window.network.AssemblyUpdate.TYPE,(packet,context)->ClientWindowViews.accept(packet));
        event.register(WindowReply.TYPE,(reply,context)-> {
            Minecraft client=Minecraft.getInstance();
            if (client.gui.screen() instanceof WindowResponseScreen screen && client.level!=null
                    && client.level.dimension().identifier().toString().equals(reply.dimension())) screen.accept(reply);
        });
    }
}
