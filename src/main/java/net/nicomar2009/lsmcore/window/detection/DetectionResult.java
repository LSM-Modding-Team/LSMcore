package net.nicomar2009.lsmcore.window.detection;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.window.integration.WindowAssembly;

public record DetectionResult(String status,WindowAssembly assembly,List<BlockPos> cells) {
    public DetectionResult {
        status=status!=null && status.matches("[a-z_]{1,64}")?status:"integration_error";
        cells=List.copyOf(cells);
    }
    public boolean valid() { return status.equals("ok"); }
    public static DetectionResult error(String code) { return new DetectionResult(code,null,List.of()); }
}
