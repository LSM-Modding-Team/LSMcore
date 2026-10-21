package net.nicomar2009.lsmcore.door.geometry;

import net.minecraft.world.phys.Vec3;

public record DoorPoint(double x,double y,double z) {
    public DoorPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("non_finite_geometry");
    }
    public Vec3 vector() { return new Vec3(x,y,z); }
}
