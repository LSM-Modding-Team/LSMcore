package net.nicomar2009.lsmcore.door.geometry;

import net.minecraft.world.phys.AABB;

/** Axis-aligned closed geometry, in BLOCK units. Never pass 0..16 model pixels directly. */
public record DoorBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
    public static final double EPS=1e-7;
    public DoorBox {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || maxX-minX<EPS || maxY-minY<EPS || maxZ-minZ<EPS) throw new IllegalArgumentException("invalid_box");
    }
    public boolean contains(DoorBox b) {
        return b.minX>=minX-EPS && b.minY>=minY-EPS && b.minZ>=minZ-EPS
                && b.maxX<=maxX+EPS && b.maxY<=maxY+EPS && b.maxZ<=maxZ+EPS;
    }
    public DoorBox move(double x,double y,double z) { return new DoorBox(minX+x,minY+y,minZ+z,maxX+x,maxY+y,maxZ+z); }
    public AABB aabb() { return new AABB(minX,minY,minZ,maxX,maxY,maxZ); }
}
