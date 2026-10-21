package net.nicomar2009.lsmcore.window.geometry;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.nicomar2009.lsmcore.window.catalog.BaseLayouts;
import net.nicomar2009.lsmcore.window.detection.*;
import net.nicomar2009.lsmcore.window.integration.*;
import net.nicomar2009.lsmcore.window.model.*;
import net.nicomar2009.lsmcore.window.storage.PlacedAssembly;

/** The same resolution applies to every construction. No age/version-of-placement branch exists. */
public final class WindowResolver {
    private WindowResolver() {}
    public static EffectiveWindow resolve(SurfaceAccess access,BlockPos start,PlacedAssembly saved,
                                           boolean manual,BlockPos first,BlockPos last,DetectionWork work) {
        DetectionResult result;
        if (manual) result=WindowDetection.corners(access,start,first,last,work);
        else if (saved!=null) result=WindowDetection.validate(access,saved.assembly(),saved.id(),work,true);
        else result=WindowDetection.discover(access,start,work);
        if (saved==null && (result.status().equals("unknown_data") || result.status().equals("incomplete") || result.status().equals("storage_unavailable")))
            return new EffectiveWindow(EffectiveWindow.Mode.UNAVAILABLE,result,null,null);
        if (saved!=null) return new EffectiveWindow(EffectiveWindow.Mode.EXPLICIT,result,saved,saved.selection().layout());
        if (result.valid()) {
            var base=BaseLayouts.entries().values().stream().filter(layout->LayoutValidator.fits(layout,result.assembly().available())).findFirst();
            if (base.isPresent()) return new EffectiveWindow(EffectiveWindow.Mode.AUTOMATIC,result,null,base.get());
        }
        return new EffectiveWindow(EffectiveWindow.Mode.INDIVIDUAL,result,null,null);
    }
    /** Consumer caches this result until geometryChanged. Never perform discovery per frame or per tick.
     * Invalid explicit records retain their original coordinate system; only compatible current surfaces
     * are clipped for display. They never fall through to automatic/individual redistribution.
     */
    public record Prepared(EffectiveWindow window,WindowGeometry.Geometry geometry) {}
    /** Build once when the query/selection changes; immutable result may be published to a renderer worker. */
    public static Prepared prepare(EffectiveWindow result) {
        WindowGeometry.Geometry geometry=null;
        if (result.layout()!=null) geometry=result.explicit()!=null?WindowGeometry.restore(result.explicit().selection())
                :WindowGeometry.calculate(result.layout(),LayoutLimits.HARD);
        return new Prepared(result,geometry);
    }
    public static List<WindowGeometry.Surface> fragments(BlockGetter level,BlockPos pos,Prepared prepared) {
        EffectiveWindow result=prepared.window();
        if (result.mode()==EffectiveWindow.Mode.UNAVAILABLE) throw new IllegalArgumentException(result.detection().status());
        var adapter=WindowIntegration.adapter(level.getBlockState(pos).getBlock()).orElse(null);
        if (adapter==null) return List.of();
        if (adapter.hasUnmanagedConfiguration(level,pos)) throw new IllegalArgumentException("unknown_data");
        var cell=adapter.cell(level,pos).orElse(null); if (cell==null) return List.of();
        if (result.mode()==EffectiveWindow.Mode.INDIVIDUAL) {
            var surfaces=List.copyOf(adapter.individual(level,pos,cell));
            if (surfaces.size()>32 || surfaces.stream().anyMatch(surface->!cell.available().contains(surface.bounds())
                    || !Double.isFinite(surface.back()) || !Double.isFinite(surface.front()) || surface.back()<0
                    || surface.front()>1 || surface.back()>=surface.front() || surface.material()==null))
                throw new IllegalArgumentException("Invalid individual geometry");
            return surfaces;
        }
        WindowAssembly assembly=result.explicit()!=null?result.explicit().assembly():result.detection().assembly();
        if (assembly==null || !WindowConnections.compatible(assembly.identity(),cell)) return List.of();
        var right=assembly.identity().facing().getClockWise();
        int x=(pos.getX()-assembly.anchor().getX())*right.getStepX()+(pos.getZ()-assembly.anchor().getZ())*right.getStepZ();
        int y=pos.getY()-assembly.anchor().getY();
        if ((pos.getX()-assembly.anchor().getX())*assembly.identity().facing().getStepX()
                +(pos.getZ()-assembly.anchor().getZ())*assembly.identity().facing().getStepZ()!=0) return List.of();
        return WindowGeometry.fragments(prepared.geometry(),
                assembly.available().minX(),assembly.available().minY(),x,y,cell.available());
    }
}
