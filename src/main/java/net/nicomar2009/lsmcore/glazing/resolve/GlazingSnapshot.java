package net.nicomar2009.lsmcore.glazing.resolve;

import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.glazing.contact.GlazingCulling;
import net.nicomar2009.lsmcore.glazing.mask.GlazingMask;
import net.nicomar2009.lsmcore.glazing.profile.GlazingProfile;
import net.nicomar2009.lsmcore.glazing.row.GlazingRow;

/** Immutable model input. Frame visibility and contact are deliberately independent. */
public record GlazingSnapshot(Object source,long revision,BlockPos position,GlazingProfile profile,
        GlazingMask mask,GlazingCulling.Face left,GlazingCulling.Face right,
        GlazingCulling.Face up,GlazingCulling.Face down,GlazingRow row) {
    public GlazingSnapshot { position=position.immutable(); }
}
