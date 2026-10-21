package net.nicomar2009.lsmcore.window.detection;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.window.integration.WindowCell;

/** A bounded read-only world or render snapshot. Unknown membership must fail closed upstream. */
public interface SurfaceAccess {
    boolean loaded(BlockPos pos);
    Optional<WindowCell> cell(BlockPos pos);
    Optional<UUID> membership(BlockPos pos);
    boolean permits(BlockPos a,WindowCell ac,BlockPos b,WindowCell bc);
}
