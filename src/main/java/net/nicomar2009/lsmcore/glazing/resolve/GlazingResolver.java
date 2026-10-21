package net.nicomar2009.lsmcore.glazing.resolve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.glazing.contact.*;
import net.nicomar2009.lsmcore.glazing.mask.GlazingMask;
import net.nicomar2009.lsmcore.glazing.profile.GlazingMode;
import net.nicomar2009.lsmcore.glazing.row.*;
import net.nicomar2009.lsmcore.glazing.view.*;
import static net.nicomar2009.lsmcore.glazing.resolve.GlazingResolution.*;

public final class GlazingResolver {
    private GlazingResolver() {}
    public static GlazingResolution resolve(GlazingAccess access,BlockPos pos,GlazingSnapshot previous,int reads) {
        return resolveInRow(access,pos,null,previous,reads);
    }
    /** A complete cached row makes each additional cell an O(1), at most five-read query. */
    public static GlazingResolution resolveInRow(GlazingAccess access,BlockPos pos,GlazingRow row,
                                                GlazingSnapshot previous,int reads) {
        var budget=new GlazingBudget(reads); long revision=access.revision();
        budget.take(); var cell=access.read(pos);
        if (access.revision()!=revision) return incomplete(access,pos,previous,Reason.STALE,null);
        if (!cell.available()) return incomplete(access,pos,previous,Reason.UNAVAILABLE,null);
        if (cell.glass()==null) return new GlazingResolution(Status.ABSENT,Reason.NONE,null,null);
        var sample=cell.glass();
        if (sample.profile().normalRow()) {
            if (row!=null && row.source()==access.source() && row.revision()==revision
                    && row.contains(pos) && row.accepts(sample.profile()))
                return local(access,pos,sample,row,previous,budget,revision,null);
            return scan(access,RowScan.Cursor.start(access,pos,sample),previous,budget);
        }
        return local(access,pos,sample,null,previous,budget,revision,null);
    }
    public static GlazingResolution resume(GlazingAccess access,RowScan.Cursor cursor,GlazingSnapshot previous,int reads) {
        return scan(access,cursor,previous,new GlazingBudget(reads));
    }
    private static GlazingResolution scan(GlazingAccess access,RowScan.Cursor cursor,GlazingSnapshot previous,GlazingBudget budget) {
        var result=RowScan.advance(access,cursor,budget);
        if (result.state()!=RowScan.State.COMPLETE)
            return incomplete(access,cursor.origin(),previous,Reason.valueOf(result.state().name()),result.cursor());
        return local(access,cursor.origin(),cursor.seed(),result.row(),previous,budget,cursor.revision(),result.cursor());
    }
    private static GlazingResolution local(GlazingAccess access,BlockPos pos,GlazingSample sample,GlazingRow row,
            GlazingSnapshot previous,GlazingBudget budget,long revision,RowScan.Cursor cursor) {
        if (access.revision()!=revision) return incomplete(access,pos,previous,Reason.STALE,cursor);
        // Reserve the complete local stage; otherwise restarting it could starve forever.
        if (budget.remaining()<4) return incomplete(access,pos,previous,Reason.BUDGET,cursor);
        Direction right=sample.profile().facing().getClockWise();
        Direction[] sides={right.getOpposite(),right,Direction.UP,Direction.DOWN};
        GlazingCulling.Face[] faces=new GlazingCulling.Face[4];
        for (int i=0;i<4;i++) {
            faces[i]=GlazingCulling.Face.SEPARATE;
            if (!GlazingContact.reaches(sample.profile(),sides[i])) continue;
            BlockPos neighbor;
            try { neighbor=RowScan.step(pos,sides[i]); }
            catch (ArithmeticException ex) { return incomplete(access,pos,previous,Reason.COORDINATE_LIMIT,cursor); }
            budget.take(); var cell=access.read(neighbor);
            if (!cell.available()) return incomplete(access,pos,previous,Reason.UNAVAILABLE,cursor);
            if (cell.glass()!=null) faces[i]=GlazingCulling.resolve(sample,cell.glass(),sides[i]);
        }
        if (access.revision()!=revision) return incomplete(access,pos,previous,Reason.STALE,cursor);
        int bits=sample.profile().mode()==GlazingMode.CONTINUOUS
                ?(faces[0].contact()?0:1)|(faces[1].contact()?0:2)
                :row==null?3:row.mask(pos,sample.profile()).bits();
        if (faces[2].contact()) bits|=8;
        if (faces[3].contact()) bits|=16;
        var snapshot=new GlazingSnapshot(access.source(),revision,pos,sample.profile(),new GlazingMask(bits),
                faces[0],faces[1],faces[2],faces[3],row);
        return new GlazingResolution(Status.COMPLETE,Reason.NONE,snapshot,null);
    }
    private static GlazingResolution incomplete(GlazingAccess access,BlockPos pos,GlazingSnapshot previous,
                                                Reason reason,RowScan.Cursor cursor) {
        if (previous!=null && (previous.source()!=access.source() || !previous.position().equals(pos))) previous=null;
        return new GlazingResolution(Status.INCOMPLETE,reason,previous,cursor);
    }
}
