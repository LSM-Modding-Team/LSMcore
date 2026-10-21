package net.nicomar2009.lsmcore.window.geometry;

import java.util.ArrayList;
import net.nicomar2009.lsmcore.window.model.*;

public final class LayoutValidator {
    private LayoutValidator() {}
    public static ValidationResult validate(WindowLayout layout, LayoutLimits limits) {
        var errors = new ArrayList<String>();
        if (!Double.isFinite(layout.width()) || !Double.isFinite(layout.height())
                || layout.width() <= 0 || layout.height() <= 0) errors.add("positive_finite");
        if (layout.columns() < 1 || layout.rows() < 1) errors.add("positive_integer");
        if (!errors.isEmpty()) return new ValidationResult(errors);
        if (layout.width() > limits.maxWidth() || layout.height() > limits.maxHeight()) errors.add("total_limit");
        if (layout.columns() > limits.maxColumns() || layout.rows() > limits.maxRows()
                || (long)layout.columns()*layout.rows() > limits.maxLeaves()) errors.add("leaf_count_limit");
        if (layout.leafWidth() > LayoutLimits.MAX_LEAF_SIZE || layout.leafHeight() > LayoutLimits.MAX_LEAF_SIZE)
            errors.add("leaf_size_limit");
        if (layout.leafWidth() <= 2*WindowGeometry.FRAME + Rect.EPSILON || layout.leafHeight() <= 2*WindowGeometry.FRAME + Rect.EPSILON)
            errors.add("frame_clearance");
        // Worst placement offset is strictly less than one cell, including half-block profiles.
        if ((Math.ceil(layout.width())+1)*(Math.ceil(layout.height())+1) > limits.maxCells()) errors.add("cell_limit");
        return new ValidationResult(errors);
    }
}
