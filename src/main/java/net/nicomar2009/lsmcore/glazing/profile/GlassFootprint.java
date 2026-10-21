package net.nicomar2009.lsmcore.glazing.profile;

/** Four glass contact quarters, NEVER the eight-cube painted/carved solid mask.
 * Bits 0/2 contribute to the left boundary; bits 1/3 to the right boundary.
 * Pair 0/1 is the first depth band, 2/3 the second. Original sparse edge codes
 * (mask & 5) and ((mask >> 1) & 5) are represented by matching Edge values.
 * These are SchoolGlazing's contact conventions, not a reconstruction of solid
 * stair collision shapes or generator UV coordinates.
 */
public record GlassFootprint(int bits) {
    public static final GlassFootprint STRAIGHT=new GlassFootprint(3);
    public GlassFootprint { if (bits<1 || bits>15) throw new IllegalArgumentException("Empty/invalid glass footprint"); }
    public record Edge(boolean firstBand,boolean secondBand) {
        public static final Edge EMPTY=new Edge(false,false);
        public boolean empty() { return !firstBand && !secondBand; }
    }
    public Edge left() { return new Edge((bits&1)!=0,(bits&4)!=0); }
    public Edge right() { return new Edge((bits&2)!=0,(bits&8)!=0); }
    public GlassFootprint reflected() {
        return new GlassFootprint(((bits&1)<<1)|((bits&2)>>1)|((bits&4)<<1)|((bits&8)>>1));
    }
}
