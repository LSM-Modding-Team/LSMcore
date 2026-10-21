package net.nicomar2009.lsmcore.window.integration;

import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Server-owned rectangular available opening; coordinates relative to bottom-left anchor cell.
 * minX/minY are in [0,1), allowing half-block starts. The owner defines explicit set boundaries.
 */
public record WindowAssembly(BlockPos anchor, WindowCell identity, Rect available) {
    public WindowAssembly {
        if (anchor==null || identity==null || available==null || available.minX()<0 || available.minX()>=1
                || available.minY()<0 || available.minY()>=1 || available.width()>64 || available.height()>64)
            throw new IllegalArgumentException("Invalid assembly bounds");
        anchor=anchor.immutable();
    }
    public BlockPos cell(int x,int y) { return anchor.relative(identity.facing().getClockWise(),x).above(y); }
}
