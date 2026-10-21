package net.nicomar2009.lsmcore.window.integration;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Geometry only. Membership belongs to Core storage, never to an adapter-provided UUID.
 * Available is a single cell-local rectangle along facing.getClockWise() and UP,
 * excluding every solid painted part. Plane is absolute along the facing normal.
 */
public record WindowCell(Identifier family, Direction facing, double plane, Rect available, boolean paintable) {
    public WindowCell {
        if (family==null || family.toString().length()>256 || facing==null || facing.getAxis()==Direction.Axis.Y || !Double.isFinite(plane)
                || available==null || available.width()<=Rect.EPSILON || available.height()<=Rect.EPSILON || !new Rect(0,0,1,1).contains(available))
            throw new IllegalArgumentException("Invalid window surface");
    }
}
