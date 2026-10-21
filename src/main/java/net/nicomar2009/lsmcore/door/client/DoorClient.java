package net.nicomar2009.lsmcore.door.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.nicomar2009.lsmcore.LSMCore;
import net.nicomar2009.lsmcore.door.network.*;

/** Only a Dist.CLIENT subscriber loads Minecraft client types. */
@EventBusSubscriber(modid=LSMCore.MOD_ID,value=Dist.CLIENT)
public final class DoorClient {
    private DoorClient() {}
    @SubscribeEvent public static void changed(DoorClientChangedEvent event) {net.nicomar2009.lsmcore.door.entity.DoorBodies.clientView=ClientDoors::get;net.nicomar2009.lsmcore.door.entity.DoorBodies.clientChanged(event.chunk==null?java.util.Set.of():ClientDoors.owners(event.chunk));}
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {event.registerEntityRenderer(net.nicomar2009.lsmcore.door.entity.DoorBodies.TYPE.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);}
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(DoorUpdate.TYPE,(packet,context)->ClientDoors.accept(packet));
        event.register(DoorReply.TYPE,(packet,context)->ClientDoors.reply(packet));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientDoors.clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ClientLevel) ClientDoors.clear(); }
}
