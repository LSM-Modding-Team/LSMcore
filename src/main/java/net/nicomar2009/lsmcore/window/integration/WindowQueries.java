package net.nicomar2009.lsmcore.window.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.nicomar2009.lsmcore.window.detection.*;
import net.nicomar2009.lsmcore.window.geometry.WindowResolver;
import net.nicomar2009.lsmcore.window.model.EffectiveWindow;
import net.nicomar2009.lsmcore.window.server.WindowState;

public final class WindowQueries {
    private WindowQueries() {}
    public static EffectiveWindow effective(ServerLevel level,BlockPos pos) {
        var store=WindowState.of(level.getServer()).assemblies(level);
        if (!store.readable()) return new EffectiveWindow(EffectiveWindow.Mode.UNAVAILABLE,DetectionResult.error("unknown_data"),null,null);
        var access=new WorldSurfaceAccess(level,store::membership);
        return WindowResolver.resolve(access,pos,store.at(pos).orElse(null),false,pos,pos,new DetectionWork());
    }
}
