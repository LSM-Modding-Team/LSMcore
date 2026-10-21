package net.nicomar2009.lsmcore.glazing.view;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.nicomar2009.lsmcore.glazing.FixedGlazing;

public final class GlazingViews {
    private GlazingViews() {}
    /** Live LevelReader convenience. hasChunkAt is checked BEFORE querying any block state. */
    public static GlazingAccess loaded(LevelReader level,LongSupplier revision) {
        return of(level,level::hasChunkAt,level,revision);
    }
    /** For render regions/snapshots, available MUST report only positions readable without loading.
     * source is a stable identity for this world/snapshot. Revision must change for profiles,
     * policies and chunk availability; resume only with the same immutable view/revision.
     * All adapter reads must be pure and remain inside the supplied availability contract.
     */
    public static GlazingAccess of(BlockGetter level,Predicate<BlockPos> available,Object source,LongSupplier revision) {
        Objects.requireNonNull(level); Objects.requireNonNull(available);
        Objects.requireNonNull(source); Objects.requireNonNull(revision);
        return new GlazingAccess() {
            public Object source() { return source; }
            public long revision() { return revision.getAsLong(); }
            public Cell read(BlockPos pos) {
                if (!available.test(pos)) return Cell.missing();
                var state=level.getBlockState(pos);
                var adapter=FixedGlazing.adapter(state.getBlock());
                if (adapter==null) return Cell.other();
                var profile=adapter.describe(level,pos,state);
                return profile.map(value->new Cell(true,new GlazingSample(state,value,adapter))).orElseGet(Cell::other);
            }
        };
    }
}
