package net.nicomar2009.lsmcore.door.model;

import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.door.geometry.DoorOrientation;

/** Grid-aligned local origin; controller may be a different declared cell. */
public record DoorPlacement(BlockPos origin,DoorOrientation orientation) {
    public DoorPlacement { origin=origin.immutable(); java.util.Objects.requireNonNull(orientation); }
}
