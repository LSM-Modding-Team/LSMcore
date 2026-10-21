package net.nicomar2009.lsmcore.door.model;

import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.door.geometry.DoorOrientation;

/** Grid-aligned local origin; controller may be a different declared cell. */
public record DoorPlacement(BlockPos origin,DoorOrientation orientation) {
    /** Normalize using the ENTIRE transformed anchor cube, never a point or a sweep bound. */
    public static DoorPlacement anchored(BlockPos destination,BlockPos anchor,DoorOrientation orientation) {
        var p=new DoorPlacement(BlockPos.ZERO,orientation);
        var b=net.nicomar2009.lsmcore.door.geometry.DoorTransform.box(p,null,false,
            new net.nicomar2009.lsmcore.door.geometry.DoorBox(anchor.getX(),anchor.getY(),anchor.getZ(),anchor.getX()+1,anchor.getY()+1,anchor.getZ()+1));
        return new DoorPlacement(destination.offset(-(int)Math.round(b.minX()),-(int)Math.round(b.minY()),-(int)Math.round(b.minZ())),orientation);
    }
    public DoorPlacement { origin=origin.immutable(); java.util.Objects.requireNonNull(orientation); }
}
