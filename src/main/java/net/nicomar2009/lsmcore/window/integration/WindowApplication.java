package net.nicomar2009.lsmcore.window.integration;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.nicomar2009.lsmcore.window.detection.*;
import net.nicomar2009.lsmcore.window.geometry.*;
import net.nicomar2009.lsmcore.window.model.*;
import net.nicomar2009.lsmcore.window.server.WindowState;
import net.nicomar2009.lsmcore.window.server.WindowSync;
import net.nicomar2009.lsmcore.window.storage.*;

/** Durable Core-owned selection transaction. Owners are never asked to copy selection data. */
public final class WindowApplication {
    private WindowApplication() {}
    public static EffectiveWindow inspect(ServerPlayer player,BlockPos clicked,boolean manual,BlockPos first,BlockPos last) {
        return inspect(player,clicked,manual,first,last,new DetectionWork());
    }
    private static EffectiveWindow inspect(ServerPlayer player,BlockPos clicked,boolean manual,BlockPos first,BlockPos last,DetectionWork work) {
        var level=player.level(); var store=WindowState.of(level.getServer()).assemblies(level); store.requireReadable();
        if (!level.hasChunkAt(clicked) || !player.isWithinBlockInteractionRange(clicked,0)) throw new IllegalArgumentException("target_unavailable");
        return WindowResolver.resolve(new WorldSurfaceAccess(level,store::membership),clicked,store.at(clicked).orElse(null),manual,first,last,work);
    }
    public static void apply(ServerPlayer player,BlockPos clicked,boolean manual,BlockPos first,BlockPos last,
                              WindowLayout layout,long expected) throws IOException {
        Direction face=interaction(player,clicked,false);
        var state=WindowState.of(player.level().getServer()); var store=state.assemblies(player.level());
        store.requireReadable(); if (expected!=store.revision()) throw new IllegalArgumentException("stale_target");
        var work=new DetectionWork();
        var result=inspect(player,clicked,manual,first,last,work);
        if (!result.detection().valid()) throw new IllegalArgumentException(result.detection().status());
        var assembly=result.detection().assembly(); var limits=state.policy.limits();
        LayoutValidator.placement(layout,assembly.available().minX(),assembly.available().minY(),limits).requireValid();
        if (!LayoutValidator.fits(layout,assembly.available())) throw new IllegalArgumentException("exact_fit");
        var old=store.at(clicked).orElse(null);
        UUID id=old==null?UUID.randomUUID():old.id();
        var affected=new LinkedHashSet<>(PlacedAssembly.cells(assembly)); if (old!=null) affected.addAll(old.cells());
        authorize(player,affected,face);
        var selection=SavedSelection.of(layout);
        if (NeoForge.EVENT_BUS.post(new WindowApplyEvent(player,assembly,selection,List.copyOf(affected))).isCanceled())
            throw new IllegalArgumentException("permission");
        var access=new WorldSurfaceAccess(player.level(),store::membership);
        var seeds=new LinkedHashSet<>(affected);
        var right=assembly.identity().facing().getClockWise();
        for (BlockPos pos:affected) {
            seeds.add(pos.relative(right)); seeds.add(pos.relative(right.getOpposite())); seeds.add(pos.above()); seeds.add(pos.below());
        }
        var invalidated=new LinkedHashSet<>(AutomaticInvalidation.expand(access,seeds,work));
        var next=new PlacedAssembly(id,assembly,selection,"ok");
        store.replace(id,next,expected);
        invalidated.addAll(AutomaticInvalidation.expand(access,seeds,work));
        WindowSync.changed(player.level(),old,next,invalidated);
    }
    /** Reset removes the explicit record, even when its rectangle is broken. It never writes a unit snapshot. */
    public static void reset(ServerPlayer player,BlockPos clicked,long expected) throws IOException {
        Direction face=interaction(player,clicked,true); var store=WindowState.of(player.level().getServer()).assemblies(player.level());
        store.requireReadable(); if (expected!=store.revision()) throw new IllegalArgumentException("stale_target");
        var old=store.at(clicked).orElse(null);
        if (old==null) return;
        authorize(player,new LinkedHashSet<>(old.cells()),face);
        // Reset also passes the protection veto. Null selection explicitly means deletion.
        if (NeoForge.EVENT_BUS.post(new WindowApplyEvent(player,old.assembly(),null,old.cells())).isCanceled())
            throw new IllegalArgumentException("permission");
        store.replace(old.id(),null,expected);
        var seeds=new LinkedHashSet<>(old.cells());
        var right=old.assembly().identity().facing().getClockWise();
        for (BlockPos pos:old.cells()) {
            seeds.add(pos.relative(right)); seeds.add(pos.relative(right.getOpposite())); seeds.add(pos.above()); seeds.add(pos.below());
        }
        var invalidated=AutomaticInvalidation.expand(new WorldSurfaceAccess(player.level(),store::membership),seeds,new DetectionWork());
        WindowSync.changed(player.level(),old,null,invalidated);
    }
    private static Direction interaction(ServerPlayer player,BlockPos clicked,boolean reset) {
        if (player.isSpectator() || !player.mayBuild() || !player.isWithinBlockInteractionRange(clicked,0)) throw new IllegalArgumentException("permission");
        if (reset) {
            var level=player.level();
            if (!level.hasChunkAt(clicked)) throw new IllegalArgumentException("incomplete");
            // Recovery also works when the last window was removed. Validate visibility to the
            // reserved cell instead of requiring a ray hit against a now nonexistent window.
            var hit=level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
                    net.minecraft.world.phys.Vec3.atCenterOf(clicked),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            if (hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS && !hit.getBlockPos().equals(clicked))
                throw new IllegalArgumentException("target_unavailable");
            return hit.getDirection();
        }
        var hit=player.pick(player.blockInteractionRange(),1,false);
        if (!(hit instanceof BlockHitResult block) || !block.getBlockPos().equals(clicked)) throw new IllegalArgumentException("target_unavailable");
        return ((BlockHitResult)hit).getDirection();
    }
    private static void authorize(ServerPlayer player,Set<BlockPos> cells,Direction face) {
        var level=player.level();
        for (BlockPos pos:cells) {
            if (!level.hasChunkAt(pos)) throw new IllegalArgumentException("incomplete");
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player,pos)
                    || !player.mayUseItemAt(pos,face,player.getItemInHand(InteractionHand.MAIN_HAND))) throw new IllegalArgumentException("permission");
            var adapter=WindowIntegration.adapter(level.getBlockState(pos).getBlock());
            if (adapter.isPresent() && adapter.get().hasUnmanagedConfiguration(level,pos)) throw new IllegalArgumentException("unknown_data");
            if (adapter.isPresent() && !adapter.get().mayConfigure(player,pos,face)) throw new IllegalArgumentException("permission");
        }
    }
}
