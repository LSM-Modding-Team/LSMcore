package net.nicomar2009.lsmcore.window.geometry;

import java.util.ArrayList;
import java.util.List;
import net.nicomar2009.lsmcore.window.model.*;

/** Closed, pose-independent geometry. Consumers supply materials and opening transforms. */
public final class WindowGeometry {
    public static final double FRAME = 1.0/16;
    public static final double BACK = 14.0/16;
    public static final double FRONT = 1;
    private WindowGeometry() {}
    public enum Material { FRAME, GLASS }
    public record Surface(Rect bounds, double back, double front, Material material) {}
    public record Leaf(int column, int row, Rect bounds, Rect glass, List<Surface> surfaces) {
        public Leaf { surfaces = List.copyOf(surfaces); }
    }
    public record Geometry(WindowLayout layout, List<Leaf> leaves) {
        public Geometry { leaves = List.copyOf(leaves); }
    }
    /** Matches LSM's sized_pane_models: 1px rails on EACH sash, adjacent rails total 2px.
     * There are no extra gaps or overlaps in the catalog layouts. Legacy phases are separate.
     * Source: LSMMod26.2Neoforge 826c791, SchoolProjectingWindowLayout and
     * tools/windows_and_enclosures/create_school_projecting_window_assets.py.
     */
    public static Geometry calculate(WindowLayout layout, LayoutLimits limits) {
        LayoutValidator.validate(layout, limits).requireValid();
        List<Leaf> leaves = new ArrayList<>();
        double w=layout.leafWidth(), h=layout.leafHeight();
        for (int row=0; row<layout.rows(); row++) for (int col=0; col<layout.columns(); col++) {
            double x=col*w, y=row*h;
            Rect bounds=new Rect(x,y,x+w,y+h), glass=new Rect(x+FRAME,y+FRAME,x+w-FRAME,y+h-FRAME);
            var surfaces=List.of(surface(new Rect(x,y,x+FRAME,y+h),Material.FRAME),
                    surface(new Rect(x+w-FRAME,y,x+w,y+h),Material.FRAME),
                    surface(new Rect(x+FRAME,y,x+w-FRAME,y+FRAME),Material.FRAME),
                    surface(new Rect(x+FRAME,y+h-FRAME,x+w-FRAME,y+h),Material.FRAME),surface(glass,Material.GLASS));
            leaves.add(new Leaf(col,row,bounds,glass,surfaces));
        }
        return new Geometry(layout,leaves);
    }
    /** Existing saved geometry remains valid after catalog deletion or stricter server policy. */
    public static Geometry restore(SavedSelection selection) { return calculate(selection.layout(),LayoutLimits.HARD); }
    private static Surface surface(Rect bounds, Material material) { return new Surface(bounds,BACK,FRONT,material); }
    /** Clip in assembly coordinates, then translate into one Minecraft cell. No models are generated. */
    public static List<Surface> fragments(Geometry geometry, double offsetX, double offsetY,
                                          int cellX, int cellY, Rect available) {
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || offsetX<0 || offsetX>=1 || offsetY<0 || offsetY>=1)
            throw new IllegalArgumentException("Invalid grid offset");
        if (!new Rect(0,0,1,1).contains(available)) throw new IllegalArgumentException("Surface outside cell");
        List<Surface> result=new ArrayList<>();
        double dx=offsetX-cellX,dy=offsetY-cellY;
        for (Leaf leaf:geometry.leaves()) {
            Rect bounds=leaf.bounds();
            if (bounds.maxX()+dx<=available.minX() || bounds.minX()+dx>=available.maxX()
                    || bounds.maxY()+dy<=available.minY() || bounds.minY()+dy>=available.maxY()) continue;
            for (Surface surface:leaf.surfaces()) surface.bounds().move(dx,dy).intersect(available).ifPresent(
                    fragment -> result.add(new Surface(fragment,surface.back(),surface.front(),surface.material())));
        }
        return List.copyOf(result);
    }
    public record Occupancy(int columns, int rows) { public int cells() { return columns*rows; } }
    public static Occupancy occupancy(WindowLayout layout, double offsetX, double offsetY) {
        LayoutValidator.validate(layout,LayoutLimits.HARD).requireValid();
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || offsetX<0 || offsetX>=1 || offsetY<0 || offsetY>=1)
            throw new IllegalArgumentException("Invalid grid offset");
        return new Occupancy((int)Math.ceil(offsetX+layout.width()), (int)Math.ceil(offsetY+layout.height()));
    }
}
