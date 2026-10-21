package net.nicomar2009.lsmcore.glazing.profile;

/** Cell-local connection envelope in model pixels, not solid/paint occupancy. */
public record PixelSpan(int min,int max) {
    public static final int CELL=16;
    public static final PixelSpan FULL=new PixelSpan(0,16);
    public static final PixelSpan LOWER=new PixelSpan(0,8);
    public static final PixelSpan UPPER=new PixelSpan(8,16);
    public PixelSpan {
        if (min<0 || max>16 || min>=max) throw new IllegalArgumentException("Invalid glazing span");
    }
}
