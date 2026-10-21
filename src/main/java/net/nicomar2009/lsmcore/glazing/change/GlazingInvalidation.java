package net.nicomar2009.lsmcore.glazing.change;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.glazing.profile.*;
import net.nicomar2009.lsmcore.glazing.row.*;
import net.nicomar2009.lsmcore.glazing.view.*;

/** Bounded invalidation discovery. Continuous glass NEVER enters a row scan. Normal rows may
 * split/merge, so old/new horizontal neighbors are seeds. Cached old rows intersecting local
 * positions must be invalidated immediately, independently of successfully discovering new rows.
 * No tick hook or retained world/cache is installed; owners schedule retries and clear caches
 * on unload. Missing chunks preserve old valid mesh inputs, not fabricated row endpoints.
 */
public final class GlazingInvalidation {
    private GlazingInvalidation() {}
    public record Cursor(Object source,long revision,List<BlockPos> local,List<BlockPos> seeds,
                         int next,List<GlazingRow> rows,RowScan.Cursor scan) {
        public Cursor { local=List.copyOf(local); seeds=List.copyOf(seeds); rows=List.copyOf(rows); }
    }
    /** continuation is null on completion or when begin() must be retried (unknown changed cell).
     * STALE always requires a fresh begin against current data, never resuming the old epoch.
     */
    public record Plan(List<BlockPos> local,List<GlazingRow> rows,RowScan.State state,Cursor continuation) {
        public Plan { local=List.copyOf(local); rows=List.copyOf(rows); }
        public boolean complete() { return state==RowScan.State.COMPLETE; }
    }
    public static Plan begin(GlazingAccess access,BlockPos position,GlazingProfile before,int reads) {
        var budget=new GlazingBudget(reads); long revision=access.revision();
        budget.take(); var cell=access.read(position);
        var local=new LinkedHashSet<BlockPos>(); local.add(position.immutable());
        var seeds=new LinkedHashSet<BlockPos>();
        addProfile(position,before,local,seeds);
        if (cell.available() && cell.glass()!=null) addProfile(position,cell.glass().profile(),local,seeds);
        // An unavailable changed cell has unknown new orientation: notify every immediate dependency.
        if (!cell.available()) for (Direction side:Direction.values()) add(position,side,local);
        var cursor=new Cursor(access.source(),revision,List.copyOf(local),List.copyOf(seeds),0,List.of(),null);
        if (access.revision()!=revision) return plan(cursor,RowScan.State.STALE);
        // Unknown new orientation requires begin() again once the changed cell is available.
        if (!cell.available()) return new Plan(cursor.local(),List.of(),RowScan.State.UNAVAILABLE,null);
        return advance(access,cursor,budget);
    }
    public static Plan resume(GlazingAccess access,Cursor cursor,int reads) {
        return advance(access,cursor,new GlazingBudget(reads));
    }
    private static Plan advance(GlazingAccess access,Cursor start,GlazingBudget budget) {
        Cursor c=start;
        while (true) {
            if (access.source()!=c.source() || access.revision()!=c.revision()) return plan(c,RowScan.State.STALE);
            if (c.scan()!=null) {
                var result=RowScan.advance(access,c.scan(),budget);
                c=new Cursor(c.source(),c.revision(),c.local(),c.seeds(),c.next(),c.rows(),result.cursor());
                if (result.state()!=RowScan.State.COMPLETE) return plan(c,result.state());
                var rows=new ArrayList<>(c.rows()); rows.add(result.row());
                c=new Cursor(c.source(),c.revision(),c.local(),c.seeds(),c.next(),rows,null);
            }
            if (c.next()==c.seeds().size()) return plan(c,RowScan.State.COMPLETE);
            BlockPos seed=c.seeds().get(c.next());
            boolean known=false;
            for (GlazingRow row:c.rows()) if (row.contains(seed)) { known=true; break; }
            if (known) {
                c=new Cursor(c.source(),c.revision(),c.local(),c.seeds(),c.next()+1,c.rows(),null);
                continue;
            }
            if (!budget.take()) return plan(c,RowScan.State.BUDGET);
            var cell=access.read(seed);
            if (!cell.available()) return plan(c,RowScan.State.UNAVAILABLE);
            if (access.revision()!=c.revision()) return plan(c,RowScan.State.STALE);
            var scan=cell.glass()!=null && cell.glass().profile().normalRow()?RowScan.Cursor.start(access,seed,cell.glass()):null;
            c=new Cursor(c.source(),c.revision(),c.local(),c.seeds(),c.next()+1,c.rows(),scan);
        }
    }
    private static Plan plan(Cursor c,RowScan.State state) {
        return new Plan(c.local(),c.rows(),state,state==RowScan.State.COMPLETE?null:c);
    }
    private static void addProfile(BlockPos pos,GlazingProfile profile,Set<BlockPos> local,Set<BlockPos> seeds) {
        if (profile==null) return;
        add(pos,Direction.UP,local); add(pos,Direction.DOWN,local);
        Direction right=profile.facing().getClockWise();
        add(pos,right,local); add(pos,right.getOpposite(),local);
        if (profile.mode()==GlazingMode.NORMAL) {
            seeds.add(pos.immutable()); add(pos,right,seeds); add(pos,right.getOpposite(),seeds);
        }
    }
    private static void add(BlockPos pos,Direction side,Set<BlockPos> target) {
        try { target.add(RowScan.step(pos,side)); }
        catch (ArithmeticException ignored) { /* Outside representable BlockPos, never a query/load. */ }
    }
}
