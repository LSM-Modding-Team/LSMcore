package net.nicomar2009.lsmcore.window.model;

/** Server policy is separate from the immutable 3 x 3 limit per leaf. */
public record LayoutLimits(double maxWidth, double maxHeight, int maxColumns, int maxRows,
                           int maxLeaves, int maxCells, int maxCustomLayouts) {
    public static final double MAX_LEAF_SIZE = 3;
    public static final LayoutLimits HARD = new LayoutLimits(64,64,64,64,256,4096,128);
    public LayoutLimits {
        if (!Double.isFinite(maxWidth) || !Double.isFinite(maxHeight) || maxWidth <= 0 || maxWidth > 64
                || maxHeight <= 0 || maxHeight > 64 || maxColumns < 1 || maxColumns > 64
                || maxRows < 1 || maxRows > 64 || maxLeaves < 1 || maxLeaves > 256
                || maxCells < 1 || maxCells > 4096 || maxCustomLayouts < 1 || maxCustomLayouts > 128)
            throw new IllegalArgumentException("Unsafe layout limits");
    }
}
