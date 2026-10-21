package net.nicomar2009.lsmcore.window.geometry;

import java.util.ArrayList;
import java.util.List;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Explicit opt-in compatibility math; never reads/writes legacy states or migrates worlds.
 * The original 24px lattice repeats every 3 cells: whole / two half fragments / whole.
 * Phase 9 is an uninitialized legacy sentinel, not a tenth geometric phase.
 */
public final class LegacyGrid {
    public static final double LEAF_SIZE = 1.5;
    private LegacyGrid() {}
    public record Fragment(Rect cellBounds, double leafOriginX, double leafOriginY) {}
    private static final double[][][] AXES = {
        {{0,1,0}}, {{0,.5,-1},{.5,1,.5}}, {{0,1,-.5}}
    };
    public static int phaseAt(int anchorPhase, int x, int y) {
        check(anchorPhase);
        return Math.floorMod(anchorPhase%3+x,3)+3*Math.floorMod(anchorPhase/3+y,3);
    }
    public static List<Fragment> fragments(int phase, Rect available) {
        check(phase);
        var result=new ArrayList<Fragment>();
        for (double[] y:AXES[phase/3]) for (double[] x:AXES[phase%3])
            new Rect(x[0],y[0],x[1],y[1]).intersect(available)
                    .ifPresent(r -> result.add(new Fragment(r,x[2],y[2])));
        return List.copyOf(result);
    }
    private static void check(int phase) {
        if (phase<0 || phase>8) throw new IllegalArgumentException("Uninitialized or invalid grid phase");
    }
}
