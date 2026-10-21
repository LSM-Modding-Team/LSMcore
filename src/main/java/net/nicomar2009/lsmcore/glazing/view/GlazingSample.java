package net.nicomar2009.lsmcore.glazing.view;

import net.minecraft.world.level.block.state.BlockState;
import net.nicomar2009.lsmcore.glazing.GlazingAdapter;
import net.nicomar2009.lsmcore.glazing.profile.GlazingProfile;

/** Retains a supplied immutable block state and immutable glass profile from one view epoch. */
public record GlazingSample(BlockState state,GlazingProfile profile,GlazingAdapter adapter) {
    public GlazingSample { if (state==null || profile==null || adapter==null) throw new IllegalArgumentException("Missing glazing sample"); }
}
