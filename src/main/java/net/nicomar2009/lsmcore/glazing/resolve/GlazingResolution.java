package net.nicomar2009.lsmcore.glazing.resolve;

import net.nicomar2009.lsmcore.glazing.row.RowScan;

/** On INCOMPLETE, snapshot is only the caller's last valid mesh input, possibly null.
 * Never use its stale culling decisions to remove faces from newly changed geometry.
 * Resume BUDGET with cursor; restart after changing revision or availability.
 */
public record GlazingResolution(Status status,Reason reason,GlazingSnapshot snapshot,RowScan.Cursor cursor) {
    public enum Status { COMPLETE, INCOMPLETE, ABSENT }
    public enum Reason { NONE, BUDGET, UNAVAILABLE, STALE, COORDINATE_LIMIT }
}
