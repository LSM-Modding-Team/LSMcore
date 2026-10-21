package net.nicomar2009.lsmcore.window.client;

import java.util.List;
import net.nicomar2009.lsmcore.window.geometry.*;
import net.nicomar2009.lsmcore.window.model.*;

/** Keeps invalid/incomplete text while typing; never allocates geometry before validation. */
final class LayoutForm {
    String id="lsmcore:unit_1x1";
    final String[] values={"1","1","1","1"};
    WindowLayout layout;
    WindowGeometry.Geometry geometry;
    List<String> errors=List.of();
    void load(WindowLayout value) {
        id=value.id(); values[0]=Double.toString(value.width()); values[1]=Double.toString(value.height());
        values[2]=Integer.toString(value.columns()); values[3]=Integer.toString(value.rows());
    }
    void calculate(LayoutLimits limits) {
        layout=null; geometry=null;
        try {
            layout=new WindowLayout(id,Double.parseDouble(values[0].trim()),Double.parseDouble(values[1].trim()),
                    Integer.parseInt(values[2].trim()),Integer.parseInt(values[3].trim()));
            errors=LayoutValidator.validate(layout,limits).errors();
            if (LayoutValidator.validate(layout,LayoutLimits.HARD).valid()) geometry=WindowGeometry.calculate(layout,LayoutLimits.HARD);
        } catch (IllegalArgumentException error) { errors=List.of("invalid_numbers"); }
    }
    boolean valid() { return geometry!=null && errors.isEmpty(); }
}
