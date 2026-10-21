package net.nicomar2009.lsmcore;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.nicomar2009.lsmcore.window.catalog.BaseLayouts;
import net.nicomar2009.lsmcore.window.config.WindowConfig;
import net.nicomar2009.lsmcore.window.network.WindowNetwork;
import net.nicomar2009.lsmcore.window.server.WindowServer;

@Mod(LSMCore.MOD_ID)
public final class LSMCore {
    public static final String MOD_ID = "lsmcore";

    public LSMCore(IEventBus modBus, ModContainer container) {
        BaseLayouts.entries(); // Fail early if the immutable internal resource is invalid.
        container.registerConfig(ModConfig.Type.SERVER,WindowConfig.SPEC,"lsmcore-server.toml");
        modBus.addListener(WindowNetwork::register);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->WindowServer.stopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event)-> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) WindowServer.logout(player);
        });
    }
}
