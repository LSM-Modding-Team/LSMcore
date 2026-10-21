package net.nicomar2009.lsmcore.door.integration;

import java.util.UUID;

/** ok, unchanged, pending_chunks or a specific rejection; revision identifies the durable state. */
public record DoorResult(String code,UUID structure,long revision) {
    public boolean accepted() { return code.equals("ok") || code.equals("unchanged") || code.equals("pending_chunks"); }
}
