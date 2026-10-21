package net.nicomar2009.lsmcore.glazing.profile;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/** Immutable glass connection data; no paint, curtain, collision or block identity requirement.
 * family gates semantic compatibility; material lets policies accept/reject mixed materials.
 * reachesBottom is independently false for lower-sill profiles even when their original
 * connection envelope has low=0. It is never inferred from solid occupancy.
 */
public record GlazingProfile(Direction facing,GlazingMode mode,Identifier family,Identifier material,
                             PixelSpan vertical,PixelSpan horizontal,GlassFootprint footprint,
                             boolean reachesBottom,GlazingShape shape) {
    public GlazingProfile {
        if (facing==null || facing.getAxis()==Direction.Axis.Y || mode==null || family==null || material==null
                || vertical==null || horizontal==null || footprint==null || shape==null)
            throw new IllegalArgumentException("Missing glazing profile data");
        if (family.toString().length()>256 || material.toString().length()>256 || reachesBottom && vertical.min()!=0)
            throw new IllegalArgumentException("Invalid glazing profile");
        if (shape!=GlazingShape.CUSTOM && footprint.bits()!=shape.footprint()) throw new IllegalArgumentException("Shape/footprint mismatch");
        if (shape.stair() && (!horizontal.equals(PixelSpan.FULL)
                || !vertical.equals(PixelSpan.LOWER) && !vertical.equals(PixelSpan.UPPER)))
            throw new IllegalArgumentException("Stair glass occupies one opposite vertical half");
    }
    public enum SolidHalf { TOP, BOTTOM }
    /** Full fixed panel, including connected tinted materials in NORMAL mode. */
    public static GlazingProfile panel(Direction facing,GlazingMode mode,Identifier family,Identifier material,boolean reachesBottom) {
        return new GlazingProfile(facing,mode,family,material,PixelSpan.FULL,PixelSpan.FULL,GlassFootprint.STRAIGHT,reachesBottom,GlazingShape.PANEL);
    }
    /** Same factory for ordinary and paintable stairs; carving is deliberately not an argument.
     * TOP solid -> LOWER glass, BOTTOM solid -> UPPER glass, for all five vanilla corners.
     */
    public static GlazingProfile stair(Direction facing,GlazingMode mode,Identifier family,Identifier material,
                                        SolidHalf solidHalf,GlazingShape corner) {
        if (solidHalf==null || corner==null || !corner.stair()) throw new IllegalArgumentException("Expected stair half/corner");
        boolean lower=solidHalf==SolidHalf.TOP;
        return new GlazingProfile(facing,mode,family,material,lower?PixelSpan.LOWER:PixelSpan.UPPER,PixelSpan.FULL,
                new GlassFootprint(corner.footprint()),lower,corner);
    }
    /** Only the straight footprint participates in the original normal horizontal row grouping. */
    public boolean normalRow() { return mode==GlazingMode.NORMAL && footprint.bits()==3; }
    public GlassFootprint.Edge leftEdge() { return horizontal.min()==0?footprint.left():GlassFootprint.Edge.EMPTY; }
    public GlassFootprint.Edge rightEdge() { return horizontal.max()==16?footprint.right():GlassFootprint.Edge.EMPTY; }
}
