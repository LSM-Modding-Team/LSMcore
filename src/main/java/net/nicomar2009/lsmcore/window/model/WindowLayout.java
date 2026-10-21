package net.nicomar2009.lsmcore.window.model;

/** Total opening size, not cell count. Leaf bounds include their own frame. Units are blocks. */
public record WindowLayout(String id, double width, double height, int columns, int rows) {
    public static final int FORMAT_VERSION = 1;
    public static final String PROFILE = "uniform_framed_v1";
    public WindowLayout {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 96)
            throw new IllegalArgumentException("Invalid layout ID");
    }
    public double leafWidth() { return width / columns; }
    public double leafHeight() { return height / rows; }
}
