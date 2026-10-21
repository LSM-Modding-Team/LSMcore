package net.nicomar2009.lsmcore.window.integration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.nicomar2009.lsmcore.window.geometry.LayoutValidator;
import net.nicomar2009.lsmcore.window.model.*;

/** Server-authorized application entry point for the editor and consumer integrations. */
public final class WindowApplication {
    private WindowApplication() {}
    public record Target(WindowAdapter owner,WindowAssembly assembly) {}
    public static Target target(ServerPlayer player,BlockPos pos) {
        var level=player.level();
        if (!level.hasChunkAt(pos) || !player.isWithinBlockInteractionRange(pos,0)) fail("target_unavailable");
        var owner=WindowIntegration.adapter(level.getBlockState(pos).getBlock())
                .orElseThrow(()->new IllegalArgumentException("no_integration"));
        var assembly=owner.describe(level,pos).orElseThrow(()->new IllegalArgumentException("target_unavailable"));
        // Anchor must also have the owner adapter, even for a cross-block family.
        if (!level.hasChunkAt(assembly.anchor()) || WindowIntegration.adapter(level.getBlockState(assembly.anchor()).getBlock()).orElse(null)!=owner)
            fail("target_unavailable");
        return new Target(owner,assembly);
    }
    public static void apply(ServerPlayer player,BlockPos clicked,WindowLayout layout) {
        LayoutLimits limits=net.nicomar2009.lsmcore.window.config.WindowConfig.limits();
        LayoutValidator.validate(layout,limits).requireValid();
        if (player.isSpectator() || !player.mayBuild()) fail("permission");
        var hit=player.pick(player.blockInteractionRange(),1,false);
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(clicked)) fail("target_unavailable");
        var interactionFace=((BlockHitResult)hit).getDirection();
        Target target=target(player,clicked); WindowAssembly assembly=target.assembly();
        Rect bounds=assembly.available();
        if (bounds.width()>limits.maxWidth() || bounds.height()>limits.maxHeight()) fail("total_limit");
        if (layout.width()>bounds.width()+Rect.EPSILON || layout.height()>bounds.height()+Rect.EPSILON) fail("surface_too_small");
        int columns=(int)Math.ceil(bounds.maxX()), rows=(int)Math.ceil(bounds.maxY());
        if ((long)columns*rows>limits.maxCells()) fail("cell_limit");
        var level=player.level(); List<BlockPos> affected=new ArrayList<>();
        WindowCell[][] cells=new WindowCell[rows][columns];
        WindowAdapter[][] adapters=new WindowAdapter[rows][columns];
        boolean containsClicked=false;
        for (int y=0;y<rows;y++) for (int x=0;x<columns;x++) {
            BlockPos pos=assembly.cell(x,y);
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) fail("target_unavailable");
            WindowAdapter adapter=WindowIntegration.adapter(level.getBlockState(pos).getBlock())
                    .orElseThrow(()->new IllegalArgumentException("surface_gap"));
            WindowCell cell=adapter.cell(level,pos).orElseThrow(()->new IllegalArgumentException("surface_gap"));
            if (!WindowConnections.compatible(assembly.identity(),cell)) fail("incompatible");
            Rect expected=bounds.intersect(new Rect(x,y,x+1,y+1)).orElseThrow().move(-x,-y);
            if (!cell.available().contains(expected)) fail("surface_gap");
            if (!level.mayInteract(player,pos) || !player.mayUseItemAt(pos,interactionFace,player.getItemInHand(InteractionHand.MAIN_HAND))
                    || !adapter.mayConfigure(player,pos,interactionFace)) fail("permission");
            cells[y][x]=cell; adapters[y][x]=adapter; affected.add(pos.immutable());
            containsClicked |= pos.equals(clicked);
            if (x>0) checkEdge(assembly.cell(x-1,y),cells[y][x-1],adapters[y][x-1],pos,cell,adapter);
            if (x==0) checkBoundary(player,pos,cell,adapter,assembly.identity().facing().getCounterClockWise());
            if (x==columns-1) checkBoundary(player,pos,cell,adapter,assembly.identity().facing().getClockWise());
            if (y==0) checkBoundary(player,pos,cell,adapter,net.minecraft.core.Direction.DOWN);
            if (y==rows-1) checkBoundary(player,pos,cell,adapter,net.minecraft.core.Direction.UP);
            if (y>0) checkEdge(assembly.cell(x,y-1),cells[y-1][x],adapters[y-1][x],pos,cell,adapter);
        }
        if (!containsClicked) fail("target_unavailable");
        var selection=SavedSelection.of(layout);
        var event=new WindowApplyEvent(player,assembly,selection,affected);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) fail("permission");
        target.owner().writeSelection(level,assembly,selection);
        target.owner().refreshRendering(level,assembly,List.copyOf(affected));
    }
    private static void checkBoundary(ServerPlayer player,BlockPos pos,WindowCell cell,
                                      WindowAdapter adapter,net.minecraft.core.Direction direction) {
        BlockPos neighbor=pos.relative(direction);
        var level=player.level();
        if (level.isOutsideBuildHeight(neighbor) || !level.getWorldBorder().isWithinBounds(neighbor)) return;
        if (!level.hasChunkAt(neighbor)) fail("target_unavailable");
        WindowIntegration.adapter(level.getBlockState(neighbor).getBlock()).ifPresent(otherAdapter->
            otherAdapter.cell(level,neighbor).ifPresent(other-> {
                if (WindowConnections.touches(pos,cell,neighbor,other) && adapter.allowsConnection(cell,other)
                        && otherAdapter.allowsConnection(other,cell)) fail("assembly_boundary");
            }));
    }
    private static void checkEdge(BlockPos a,WindowCell ac,WindowAdapter aa,BlockPos b,WindowCell bc,WindowAdapter ba) {
        if (!WindowConnections.touches(a,ac,b,bc) || !aa.allowsConnection(ac,bc) || !ba.allowsConnection(bc,ac)) fail("incompatible");
    }
    private static void fail(String key) { throw new IllegalArgumentException(key); }
}
