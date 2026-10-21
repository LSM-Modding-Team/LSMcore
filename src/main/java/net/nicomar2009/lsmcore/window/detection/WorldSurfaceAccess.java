package net.nicomar2009.lsmcore.window.detection;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.nicomar2009.lsmcore.window.integration.*;

public record WorldSurfaceAccess(BlockGetter level,Function<BlockPos,Optional<UUID>> members) implements SurfaceAccess {
    public boolean loaded(BlockPos pos) { return !(level instanceof LevelReader reader) || reader.hasChunkAt(pos); }
    public Optional<WindowCell> cell(BlockPos pos) {
        if (!loaded(pos)) return Optional.empty();
        return WindowIntegration.adapter(level.getBlockState(pos).getBlock()).flatMap(adapter-> {
            if (adapter.hasUnmanagedConfiguration(level,pos)) throw new IllegalArgumentException("unknown_data");
            return adapter.cell(level,pos);
        });
    }
    public Optional<UUID> membership(BlockPos pos) { return members.apply(pos); }
    public boolean permits(BlockPos a,WindowCell ac,BlockPos b,WindowCell bc) {
        var aa=WindowIntegration.adapter(level.getBlockState(a).getBlock());
        var ba=WindowIntegration.adapter(level.getBlockState(b).getBlock());
        return aa.isPresent() && ba.isPresent() && aa.get().allowsConnection(ac,bc) && ba.get().allowsConnection(bc,ac);
    }
}
