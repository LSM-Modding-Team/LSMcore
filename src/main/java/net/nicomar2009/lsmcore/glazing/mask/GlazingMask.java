package net.nicomar2009.lsmcore.glazing.mask;

/** Exact legacy conversion: 1/2 are VISIBLE side frames, 4 a center post;
 * 8/16 are CONNECTED top/bottom. These meanings must not be inverted.
 */
public record GlazingMask(int bits) {
    public static final int LEFT=1,RIGHT=2,CENTER=4,UP=8,DOWN=16;
    public GlazingMask { if (bits<0 || bits>31) throw new IllegalArgumentException("Invalid glazing mask"); }
    public boolean leftVisible() { return (bits&LEFT)!=0; }
    public boolean rightVisible() { return (bits&RIGHT)!=0; }
    public boolean centerPost() { return (bits&CENTER)!=0; }
    public boolean topConnected() { return (bits&UP)!=0; }
    public boolean bottomConnected() { return (bits&DOWN)!=0; }
    public GlazingMask reflected() { return new GlazingMask(((bits&LEFT)<<1)|((bits&RIGHT)>>1)|(bits&(CENTER|UP|DOWN))); }
}
