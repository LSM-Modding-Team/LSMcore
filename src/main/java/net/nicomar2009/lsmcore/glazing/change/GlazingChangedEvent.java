package net.nicomar2009.lsmcore.glazing.change;

import net.neoforged.bus.api.Event;
import net.nicomar2009.lsmcore.glazing.view.GlazingAccess;

/** Common-side notification; no rendering, scans, chunk loads or client classes in the event.
 * Consumers index cached rows by their occupied ranges and immediate dependencies. Mark those
 * intersecting plan.local() dirty immediately, even if row discovery is still incomplete.
 * Rows are ranges, never an instruction to iterate an arbitrarily large range synchronously.
 */
public final class GlazingChangedEvent extends Event {
    private final GlazingAccess access;
    private final GlazingInvalidation.Plan plan;
    public GlazingChangedEvent(GlazingAccess access,GlazingInvalidation.Plan plan) { this.access=access; this.plan=plan; }
    public GlazingAccess access() { return access; }
    public GlazingInvalidation.Plan plan() { return plan; }
}
