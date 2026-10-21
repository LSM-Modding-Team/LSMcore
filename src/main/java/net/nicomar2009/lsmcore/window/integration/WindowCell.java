package net.nicomar2009.lsmcore.window.integration;

import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Surface in cell-local coordinates along facing.getClockWise() and UP.
 * plane is the absolute signed coordinate along facing, NOT a block-local depth.
 * available excludes paintable solid wall/slab/stair parts, not the window's frame.
 */
public record WindowCell(UUID assemblyId, Identifier family, Direction facing, double plane,
                         Rect available, boolean paintable) {
    public WindowCell {
        if (assemblyId==null || family==null || facing==null || facing.getAxis()==Direction.Axis.Y
                || !Double.isFinite(plane) || available==null || !new Rect(0,0,1,1).contains(available))
            throw new IllegalArgumentException("Invalid window cell");
    }
}
