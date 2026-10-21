package net.nicomar2009.lsmcore.door;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.door.integration.DoorAdapter;

/** Register owner type adapters, not blocks. No LSMMod/PaintingMod or projecting-window dependency. */
public final class Doors {
    private static final Map<Identifier,DoorAdapter> ADAPTERS=new ConcurrentHashMap<>();
    private Doors() {}
    public static void register(Identifier type,DoorAdapter adapter) {
        if (ADAPTERS.putIfAbsent(Objects.requireNonNull(type),Objects.requireNonNull(adapter))!=null)
            throw new IllegalStateException("Door type already registered");
    }
    public static Optional<DoorAdapter> adapter(Identifier type) { return Optional.ofNullable(ADAPTERS.get(type)); }
}
