package net.nicomar2009.lsmcore.glazing;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.nicomar2009.lsmcore.glazing.change.*;
import net.nicomar2009.lsmcore.glazing.profile.GlazingProfile;
import net.nicomar2009.lsmcore.glazing.view.GlazingAccess;

/** Independent fixed-glass registry. No owner blocks or projecting-window adapters are registered here. */
public final class FixedGlazing {
    public static final Identifier FAMILY=Identifier.fromNamespaceAndPath("lsmcore","fixed_glazing");
    private static final ConcurrentHashMap<Block,GlazingAdapter> ADAPTERS=new ConcurrentHashMap<>();
    private FixedGlazing() {}
    /** Register once during common setup, before exposing world/render snapshots. */
    public static void register(Block block,GlazingAdapter adapter) {
        if (ADAPTERS.putIfAbsent(Objects.requireNonNull(block),Objects.requireNonNull(adapter))!=null)
            throw new IllegalStateException("Fixed-glass adapter already registered");
    }
    public static GlazingAdapter adapter(Block block) { return ADAPTERS.get(block); }
    /** Call after placement/removal/profile changes, on the owning world thread, on both logical sides.
     * Advance the access revision FIRST. Supply the previous glass profile even on removal/rotation.
     * Event subscribers invalidate intersecting cached rows, and schedule bounded continuation work.
     */
    public static GlazingInvalidation.Plan changed(GlazingAccess access,BlockPos position,GlazingProfile before,int reads) {
        var plan=GlazingInvalidation.begin(access,position,before,reads);
        NeoForge.EVENT_BUS.post(new GlazingChangedEvent(access,plan));
        return plan;
    }
    public static GlazingInvalidation.Plan resumeChange(GlazingAccess access,GlazingInvalidation.Cursor cursor,int reads) {
        var plan=GlazingInvalidation.resume(access,cursor,reads);
        NeoForge.EVENT_BUS.post(new GlazingChangedEvent(access,plan));
        return plan;
    }
}
