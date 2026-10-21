package net.nicomar2009.lsmcore.door.model;

import net.nicomar2009.lsmcore.door.geometry.DoorPoint;

/** Stable leaf ID within structure UUID. Positive turn is (x,z)->(-z,x), always exactly 90 degrees.
 * Every surface with this leaf ID (glass, outline, handle, etc.) receives the SAME rigid transform.
 */
public record DoorLeaf(String id,DoorPoint pivot,int turn,boolean manual,Redstone redstone) {
    public enum Redstone { IGNORE, FOLLOW_POWER, TOGGLE_ON_RISING }
    public DoorLeaf {
        DoorDefinition.identifier(id); java.util.Objects.requireNonNull(pivot); java.util.Objects.requireNonNull(redstone);
        if (turn!=1 && turn!=-1) throw new IllegalArgumentException("invalid_turn");
        if (Math.abs(pivot.x())>32 || Math.abs(pivot.y())>32 || Math.abs(pivot.z())>32) throw new IllegalArgumentException("pivot_limit");
    }
}
