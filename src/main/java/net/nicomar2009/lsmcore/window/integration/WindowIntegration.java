package net.nicomar2009.lsmcore.window.integration;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;

/** Small opt-in registry. LSMMod/PaintingMod keep ownership of all their registrations. */
public final class WindowIntegration {
    private static final ConcurrentHashMap<Block,WindowAdapter> ADAPTERS=new ConcurrentHashMap<>();
    private WindowIntegration() {}
    public static void register(Block block,WindowAdapter adapter) {
        if (block==null || adapter==null || ADAPTERS.putIfAbsent(block,adapter)!=null)
            throw new IllegalArgumentException("Missing or duplicate window adapter");
    }
    public static Optional<WindowAdapter> adapter(Block block) { return Optional.ofNullable(ADAPTERS.get(block)); }
}
