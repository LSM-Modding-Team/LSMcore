package net.nicomar2009.lsmcore;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.nicomar2009.lsmcore.window.catalog.BaseLayouts;
import net.nicomar2009.lsmcore.window.network.WindowNetwork;
import net.nicomar2009.lsmcore.window.server.*;
import net.nicomar2009.lsmcore.door.network.DoorNetwork;
import net.nicomar2009.lsmcore.door.server.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(LSMCore.MOD_ID)
public final class LSMCore {
    public static final String MOD_ID="lsmcore";
    public LSMCore(IEventBus modBus) {
        net.nicomar2009.lsmcore.door.entity.DoorBodies.register(modBus);
        NeoForge.EVENT_BUS.addListener(net.nicomar2009.lsmcore.door.entity.DoorBodies::joined);
        NeoForge.EVENT_BUS.addListener(net.nicomar2009.lsmcore.door.entity.DoorBodies::left);
        NeoForge.EVENT_BUS.addListener(net.nicomar2009.lsmcore.door.entity.DoorBodies::unloaded);
        BaseLayouts.entries(); modBus.addListener(WindowNetwork::register); modBus.addListener(DoorNetwork::register);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->DoorRequests.stopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Sent event)->DoorSync.sent(event));
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.UnWatch event)->DoorSync.unwatch(event));
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event)->DoorRecovery.loaded(event));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->DoorRecovery.tick(event));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->WindowServer.stopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Sent event)->WindowSync.sent(event));
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.UnWatch event)->WindowSync.unwatch(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event)-> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                WindowServer.logout(player); DoorRequests.logout(player);
            }
        });
    }
}
