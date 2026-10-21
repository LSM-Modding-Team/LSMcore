package net.nicomar2009.lsmcore.glazing.row;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.glazing.profile.GlazingProfile;
import net.nicomar2009.lsmcore.glazing.mask.GlazingMask;

/** Complete immutable row; reuse it for every cell during model reconstruction. */
public record GlazingRow(Object source,long revision,BlockPos first,long length,
                         GlazingProfile profile,int firstOffset,int lastOffset) {
    public GlazingRow {
        first=first.immutable();
        if (length<1 || length>(1L<<32) || firstOffset<0 || firstOffset>=16
                || lastOffset<=0 || lastOffset>16 || !profile.normalRow()) throw new IllegalArgumentException("Not a normal row");
    }
    public long index(BlockPos pos) {
        Direction right=profile.facing().getClockWise();
        long dx=(long)pos.getX()-first.getX(),dz=(long)pos.getZ()-first.getZ();
        if (pos.getY()!=first.getY() || dx*right.getStepZ()!=dz*right.getStepX()) return -1;
        long index=dx*right.getStepX()+dz*right.getStepZ();
        return index>=0 && index<length?index:-1;
    }
    public boolean contains(BlockPos pos) { return index(pos)>=0; }
    public boolean accepts(GlazingProfile pane) {
        return pane.normalRow() && pane.facing()==profile.facing()
                && pane.family().equals(profile.family()) && pane.vertical().equals(profile.vertical());
    }
    /** Original layout alignment: relative to first glass edge, not global block coordinates. */
    public GlazingMask mask(BlockPos pos,GlazingProfile pane) {
        long index=index(pos);
        if (index<0 || !accepts(pane)) throw new IllegalArgumentException("Cell outside row");
        long x=index*16, end=(length-1)*16+lastOffset;
        int spacing=length%2==0?32:24;
        long left=x+pane.horizontal().min(),right=x+pane.horizontal().max();
        int bits=(left==firstOffset || Math.floorMod(left-firstOffset,spacing)==0?1:0)
                | (right==end || Math.floorMod(right-firstOffset,spacing)==0?2:0)
                | (pane.horizontal().min()<8 && pane.horizontal().max()>8
                   && Math.floorMod(x+8-firstOffset,spacing)==0?4:0);
        return new GlazingMask(bits);
    }
}
