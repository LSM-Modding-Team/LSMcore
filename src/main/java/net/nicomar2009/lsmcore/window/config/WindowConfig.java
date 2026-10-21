package net.nicomar2009.lsmcore.window.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.nicomar2009.lsmcore.window.model.LayoutLimits;

/** SERVER config: stored per world, synchronized by NeoForge. Cannot redefine the base catalog. */
public final class WindowConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue WIDTH,HEIGHT,COLUMNS,ROWS,LEAVES,CELLS,CUSTOM;
    static {
        var builder=new ModConfigSpec.Builder(); builder.push("projectingWindows");
        WIDTH=builder.comment("Maximum total width in blocks, separate from the 3-block limit per leaf.").defineInRange("maxTotalWidth",64,1,64);
        HEIGHT=builder.defineInRange("maxTotalHeight",64,1,64);
        COLUMNS=builder.defineInRange("maxColumns",64,1,64);
        ROWS=builder.defineInRange("maxRows",64,1,64);
        LEAVES=builder.defineInRange("maxLeaves",256,1,256);
        CELLS=builder.defineInRange("maxOccupiedCells",4096,4,4096);
        CUSTOM=builder.defineInRange("maxCustomLayouts",128,1,128);
        builder.pop(); SPEC=builder.build();
    }
    private WindowConfig() {}
    public static LayoutLimits limits() {
        return new LayoutLimits(WIDTH.get(),HEIGHT.get(),COLUMNS.get(),ROWS.get(),LEAVES.get(),CELLS.get(),CUSTOM.get());
    }
}
