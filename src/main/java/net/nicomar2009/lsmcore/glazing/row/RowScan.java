package net.nicomar2009.lsmcore.glazing.row;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.glazing.contact.GlazingContact;
import net.nicomar2009.lsmcore.glazing.view.*;

/** Resumable constant-memory scan. Neither missing chunks nor budget exhaustion is a row end. */
public final class RowScan {
    private RowScan() {}
    public enum State { COMPLETE, BUDGET, UNAVAILABLE, STALE, COORDINATE_LIMIT }
    public record Cursor(Object source,long revision,BlockPos origin,GlazingSample seed,
                         BlockPos first,GlazingSample left,BlockPos last,GlazingSample right,
                         long length,boolean scanningRight) {
        public Cursor { origin=origin.immutable(); first=first.immutable(); last=last.immutable(); }
        public static Cursor start(GlazingAccess access,BlockPos pos,GlazingSample sample) {
            if (!sample.profile().normalRow()) throw new IllegalArgumentException("Not a normal row");
            return new Cursor(access.source(),access.revision(),pos,sample,pos,sample,pos,sample,1,false);
        }
    }
    public record Result(State state,Cursor cursor,GlazingRow row) {}
    public static Result advance(GlazingAccess access,Cursor initial,GlazingBudget budget) {
        Cursor c=initial;
        while (true) {
            if (access.source()!=c.source() || access.revision()!=c.revision()) return new Result(State.STALE,c,null);
            Direction direction=c.seed().profile().facing().getClockWise();
            if (!c.scanningRight()) direction=direction.getOpposite();
            BlockPos edge=c.scanningRight()?c.last():c.first();
            GlazingSample sample=c.scanningRight()?c.right():c.left();
            boolean end=!GlazingContact.reaches(sample.profile(),direction);
            GlazingSample next=null; BlockPos nextPos=null;
            if (!end) {
                if (!budget.take()) return new Result(State.BUDGET,c,null);
                try { nextPos=step(edge,direction); }
                catch (ArithmeticException ex) { return new Result(State.COORDINATE_LIMIT,c,null); }
                var cell=access.read(nextPos);
                if (access.revision()!=c.revision()) return new Result(State.STALE,c,null);
                if (!cell.available()) return new Result(State.UNAVAILABLE,c,null);
                next=cell.glass();
                end=next==null || !GlazingContact.connected(sample,next,direction);
            }
            if (end) {
                if (c.scanningRight()) return new Result(State.COMPLETE,c,new GlazingRow(c.source(),c.revision(),
                        c.first(),c.length(),c.seed().profile(),c.left().profile().horizontal().min(),
                        c.right().profile().horizontal().max()));
                c=new Cursor(c.source(),c.revision(),c.origin(),c.seed(),c.first(),c.left(),c.last(),c.right(),c.length(),true);
            } else if (c.scanningRight()) {
                c=new Cursor(c.source(),c.revision(),c.origin(),c.seed(),c.first(),c.left(),nextPos,next,c.length()+1,true);
            } else {
                c=new Cursor(c.source(),c.revision(),c.origin(),c.seed(),nextPos,next,c.last(),c.right(),c.length()+1,false);
            }
        }
    }
    public static BlockPos step(BlockPos pos,Direction side) {
        return new BlockPos(Math.addExact(pos.getX(),side.getStepX()),Math.addExact(pos.getY(),side.getStepY()),
                Math.addExact(pos.getZ(),side.getStepZ()));
    }
}
