package net.nicomar2009.lsmcore.door.geometry;

/** Mirror local X first, then quarter-turn about +Y: (x,z) -> (-z,x).
 * Reflecting the placement also reflects pivots and reverses WORLD opening handedness.
 * Stored closed geometry and local leaf turn never need destructive rewriting.
 */
public record DoorOrientation(int quarterTurns,boolean mirrored) {
    public DoorOrientation { if (quarterTurns<0 || quarterTurns>3) throw new IllegalArgumentException("invalid_orientation"); }
    public DoorPoint apply(DoorPoint p) {
        double x=mirrored?-p.x():p.x(),z=p.z();
        return switch (quarterTurns) {
            case 0->new DoorPoint(x,p.y(),z); case 1->new DoorPoint(-z,p.y(),x);
            case 2->new DoorPoint(-x,p.y(),-z); default->new DoorPoint(z,p.y(),-x);
        };
    }
    public DoorOrientation rotated(int turns) { return new DoorOrientation(Math.floorMod(quarterTurns+Math.floorMod(turns,4),4),mirrored); }
    public DoorOrientation reflected() { return new DoorOrientation(Math.floorMod(-quarterTurns,4),!mirrored); }
}
