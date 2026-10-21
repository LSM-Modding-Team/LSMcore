package net.nicomar2009.lsmcore.glazing.view;

/** Per-operation reads, including unavailable cells. No whole-row length limit is inferred.
 * Eight reads permit a complete local query or the final local stage of a resumed row.
 */
public final class GlazingBudget {
    public static final int DEFAULT=256,MAX=65536;
    private int remaining;
    public GlazingBudget(int reads) {
        if (reads<8 || reads>MAX) throw new IllegalArgumentException("Glazing budget must be 8..65536 reads");
        remaining=reads;
    }
    public boolean take() { if (remaining==0) return false; remaining--; return true; }
    public int remaining() { return remaining; }
}
