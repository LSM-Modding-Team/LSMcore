package net.nicomar2009.lsmcore.window.integration;

import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Pure adjacency rules: no flood fill, world loads, caches, opening states or per-tick work. */
public final class WindowConnections {
    private WindowConnections() {}
    public static boolean compatible(WindowCell a,WindowCell b) {
        return a.family().equals(b.family()) && a.facing()==b.facing()
                && Math.abs(a.plane()-b.plane())<Rect.EPSILON;
    }
    public static boolean touches(BlockPos aPos,WindowCell a,BlockPos bPos,WindowCell b) {
        if (!compatible(a,b)) return false;
        var right=a.facing().getClockWise();
        int dx=bPos.getX()-aPos.getX(), dy=bPos.getY()-aPos.getY(), dz=bPos.getZ()-aPos.getZ();
        int u=dx*right.getStepX()+dz*right.getStepZ();
        if (Math.abs(dx)+Math.abs(dy)+Math.abs(dz)!=1 || Math.abs(u)+Math.abs(dy)!=1) return false;
        Rect ar=a.available(), br=b.available().move(u,dy);
        if (u!=0) return Math.abs((u>0?ar.maxX():ar.minX())-(u>0?br.minX():br.maxX()))<Rect.EPSILON
                && Math.min(ar.maxY(),br.maxY())-Math.max(ar.minY(),br.minY())>Rect.EPSILON;
        return Math.abs((dy>0?ar.maxY():ar.minY())-(dy>0?br.minY():br.maxY()))<Rect.EPSILON
                && Math.min(ar.maxX(),br.maxX())-Math.max(ar.minX(),br.minX())>Rect.EPSILON;
    }
}
