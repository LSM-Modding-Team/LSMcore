package net.nicomar2009.lsmcore.door.model;

import net.nicomar2009.lsmcore.door.geometry.DoorPlan;

/** Geometry compilation is shared by state-only revisions; never rebuilt in a tick/frame loop. */
public final class DoorStructure {
    private final DoorDefinition definition;
    private final DoorPlacement placement;
    private final DoorPlan plan;
    public DoorStructure(DoorDefinition definition,DoorPlacement placement) {
        this.definition=definition; this.placement=placement; this.plan=DoorPlan.compile(definition,placement);
    }
    public DoorDefinition definition() { return definition; }
    public DoorPlacement placement() { return placement; }
    public DoorPlan plan() { return plan; }
}
