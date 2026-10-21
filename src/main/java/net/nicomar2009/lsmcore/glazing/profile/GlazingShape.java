package net.nicomar2009.lsmcore.glazing.profile;

/** Exact contact masks from SchoolGlazing.pane at LSMMod commit 826c791.
 * Do not derive these from PaintingMod's carved slab mask, model UVs or solid shape.
 */
public enum GlazingShape {
    PANEL(3), STRAIGHT(3), OUTER_LEFT(1), OUTER_RIGHT(2), INNER_LEFT(11), INNER_RIGHT(7), CUSTOM(0);
    private final int footprint;
    GlazingShape(int footprint) { this.footprint=footprint; }
    public int footprint() { return footprint; }
    public boolean stair() { return this!=PANEL && this!=CUSTOM; }
}
