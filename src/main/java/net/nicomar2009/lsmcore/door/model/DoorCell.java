package net.nicomar2009.lsmcore.door.model;

import net.minecraft.core.BlockPos;

/** Owner-defined carrier role. Core additionally reserves swept-space proxy cells. */
public record DoorCell(String id,BlockPos offset) {
    public DoorCell {
        DoorDefinition.identifier(id); offset=offset.immutable();
        if (Math.abs((long)offset.getX())>32 || Math.abs((long)offset.getY())>32 || Math.abs((long)offset.getZ())>32)
            throw new IllegalArgumentException("cell_limit");
    }
}
