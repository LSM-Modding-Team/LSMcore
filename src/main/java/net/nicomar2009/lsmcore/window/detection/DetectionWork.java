package net.nicomar2009.lsmcore.window.detection;

/** Shared across one entire user/block-change operation, not reset per neighboring scan. */
public final class DetectionWork {
    public static final int ABSOLUTE_MAX=32768;
    private int left=ABSOLUTE_MAX;
    public void use() { if (--left<0) throw new IllegalArgumentException("detection_limit"); }
}
