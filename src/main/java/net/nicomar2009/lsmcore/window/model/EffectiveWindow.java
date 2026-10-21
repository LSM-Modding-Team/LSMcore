package net.nicomar2009.lsmcore.window.model;

import net.nicomar2009.lsmcore.window.detection.DetectionResult;
import net.nicomar2009.lsmcore.window.storage.PlacedAssembly;

/** Absence and explicit unit layout are different states. An individual fallback has NO aggregate layout.
 * A broken/incomplete explicit record remains explicit and retains its full original snapshot.
 */
public record EffectiveWindow(Mode mode,DetectionResult detection,PlacedAssembly explicit,WindowLayout layout) {
    public EffectiveWindow {
        if (mode==null || detection==null) throw new IllegalArgumentException("Missing effective state");
        if (mode==Mode.EXPLICIT) {
            if (explicit==null || !explicit.selection().layout().equals(layout)) throw new IllegalArgumentException("Invalid explicit state");
        } else if (explicit!=null) throw new IllegalArgumentException("Unexpected explicit selection");
        if (mode==Mode.AUTOMATIC && (layout==null || !detection.valid() || detection.assembly()==null))
            throw new IllegalArgumentException("Invalid automatic state");
        if ((mode==Mode.INDIVIDUAL || mode==Mode.UNAVAILABLE) && layout!=null)
            throw new IllegalArgumentException("Unconfigured state cannot contain a fabricated aggregate layout");
    }
    public enum Mode { AUTOMATIC, INDIVIDUAL, EXPLICIT, UNAVAILABLE }
}
