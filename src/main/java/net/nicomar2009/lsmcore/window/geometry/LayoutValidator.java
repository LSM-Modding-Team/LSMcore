package net.nicomar2009.lsmcore.window.geometry;

import java.util.ArrayList;
import net.nicomar2009.lsmcore.window.model.*;

/** Intrinsic shape, new-application policy and actual placement are separate checks. */
public final class LayoutValidator {
    private LayoutValidator() {}
    public static ValidationResult intrinsic(WindowLayout layout) {
        var errors=new ArrayList<String>();
        if (!Double.isFinite(layout.width()) || !Double.isFinite(layout.height()) || layout.width()<=0 || layout.height()<=0)
            errors.add("positive_finite");
        if (layout.columns()<1 || layout.rows()<1) errors.add("positive_integer");
        if (!errors.isEmpty()) return new ValidationResult(errors);
        if (layout.width()>64 || layout.height()>64) errors.add("total_limit");
        if (layout.columns()>64 || layout.rows()>64 || (long)layout.columns()*layout.rows()>256) errors.add("leaf_count_limit");
        if (layout.leafWidth()>LayoutLimits.MAX_LEAF_SIZE || layout.leafHeight()>LayoutLimits.MAX_LEAF_SIZE) errors.add("leaf_size_limit");
        if (layout.leafWidth()<=2*WindowGeometry.FRAME+Rect.EPSILON || layout.leafHeight()<=2*WindowGeometry.FRAME+Rect.EPSILON)
            errors.add("frame_clearance");
        return new ValidationResult(errors);
    }
    public static ValidationResult validate(WindowLayout layout,LayoutLimits limits) {
        var errors=new ArrayList<>(intrinsic(layout).errors());
        if (!errors.isEmpty()) return new ValidationResult(errors);
        if (layout.width()>limits.maxWidth() || layout.height()>limits.maxHeight()) errors.add("total_limit");
        if (layout.columns()>limits.maxColumns() || layout.rows()>limits.maxRows()
                || (long)layout.columns()*layout.rows()>limits.maxLeaves()) errors.add("leaf_count_limit");
        return new ValidationResult(errors);
    }
    public static ValidationResult placement(WindowLayout layout,double x,double y,LayoutLimits limits) {
        var errors=new ArrayList<>(validate(layout,limits).errors());
        if (!errors.isEmpty()) return new ValidationResult(errors);
        try {
            if (WindowGeometry.occupancy(layout,x,y).cells()>limits.maxCells()) errors.add("cell_limit");
        } catch (IllegalArgumentException invalid) { errors.add("cell_limit"); }
        return new ValidationResult(errors);
    }
    public static boolean fits(WindowLayout layout,Rect target) {
        return Math.abs(layout.width()-target.width())<=Rect.EPSILON && Math.abs(layout.height()-target.height())<=Rect.EPSILON;
    }
}
