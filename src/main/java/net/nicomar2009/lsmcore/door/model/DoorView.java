package net.nicomar2009.lsmcore.door.model;

import net.nicomar2009.lsmcore.door.geometry.DoorGeometry;

/** Derived cache, not a second authority or persisted controller copy. */
public record DoorView(DoorRecord record,DoorGeometry geometry) {
    /** Reuse posed geometry for power-memory/lifecycle-only updates. */
    public static DoorView updated(DoorRecord next,DoorView previous) {
        if (previous!=null && previous.record().id().equals(next.id()) && previous.record().structure()==next.structure()
                && next.leaves().entrySet().stream().allMatch(e->previous.record().leaves().get(e.getKey()).open()==e.getValue().open()))
            return new DoorView(next,previous.geometry());
        return of(next);
    }
    public static DoorView of(DoorRecord record) { return new DoorView(record,DoorGeometry.resolve(record)); }
}
