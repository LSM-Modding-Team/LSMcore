package net.nicomar2009.lsmcore.window.detection;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Expand invalidations, not selections. All scans in one operation share one absolute work budget. */
public final class AutomaticInvalidation {
    private AutomaticInvalidation() {}
    public static Set<BlockPos> expand(SurfaceAccess access,Collection<BlockPos> seeds,DetectionWork work) {
        var result=new LinkedHashSet<BlockPos>(seeds); var visited=new HashSet<BlockPos>();
        for (BlockPos seed:seeds) {
            if (!visited.add(seed) || !access.loaded(seed) || access.membership(seed).isPresent()) continue;
            var detection=WindowDetection.discover(access,seed,work);
            result.addAll(detection.cells()); visited.addAll(detection.cells());
        }
        return result;
    }
}
