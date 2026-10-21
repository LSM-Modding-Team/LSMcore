package net.nicomar2009.lsmcore.glazing;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.nicomar2009.lsmcore.glazing.profile.GlazingProfile;

/** Register for consumer-owned fixed glass blocks only. Methods must be read-only and snapshot-safe.
 * Describe the glass from the supplied state, never the painted/carved solid occupancy.
 * No callback may load chunks or perform another row scan. Policy changes invalidate the view epoch.
 */
public interface GlazingAdapter {
    Optional<GlazingProfile> describe(BlockGetter level,BlockPos pos,BlockState state);
    /** BOTH adapters consent. Mixed block IDs/materials are allowed; no same-ID default is imposed. */
    boolean allowsConnection(GlazingProfile self,GlazingProfile other,Direction side);
    /** Source-owner veto for WHOLE block-face suppression, separate from transparent cap removal.
     * Default false is essential for paintable/carved stairs: their solid faces must survive.
     * Owner-specific same-ID fallbacks and super.skipRendering remain in the owner block.
     */
    default boolean allowsWholeFaceCulling(BlockState self,GlazingProfile profile,BlockState neighbor,
                                           GlazingProfile other,Direction side) { return false; }
}
