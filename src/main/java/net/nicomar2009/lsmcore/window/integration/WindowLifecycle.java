package net.nicomar2009.lsmcore.window.integration;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.nicomar2009.lsmcore.window.detection.*;
import net.nicomar2009.lsmcore.window.server.*;
import net.nicomar2009.lsmcore.window.storage.PlacedAssembly;

/** Call AFTER placement, removal or any facing/available-surface change, not after paint/open changes.
 * Automatic components may join/split on the next query. Explicit membership NEVER joins/splits:
 * it remains reserved and recoverable until reset/reconfiguration. Chunk unload is NOT removal.
 */
public final class WindowLifecycle {
    private WindowLifecycle() {}
    public static void changed(ServerLevel level,BlockPos pos) {
        var store=WindowState.of(level.getServer()).assemblies(level);
        var affected=new LinkedHashSet<BlockPos>(); affected.add(pos.immutable());
        var neighbors=new LinkedHashSet<BlockPos>(); neighbors.add(pos.immutable());
        for (Direction direction:Direction.values()) neighbors.add(pos.relative(direction));
        affected.addAll(neighbors);
        if (!store.readable()) { WindowSync.invalidate(level,affected); return; }
        var access=new WorldSurfaceAccess(level,store::membership); var work=new DetectionWork();
        var explicit=new LinkedHashMap<UUID,PlacedAssembly>();
        for (BlockPos neighbor:neighbors) store.at(neighbor).ifPresent(record->explicit.put(record.id(),record));
        for (var old:explicit.values()) {
            var result=WindowDetection.validate(access,old.assembly(),old.id(),work,true);
            // Missing chunks never damage health or selections. Real edits retain the original snapshot/membership.
            String health=result.status().equals("incomplete")?old.health():result.status();
            var next=new PlacedAssembly(old.id(),old.assembly(),old.selection(),health);
            affected.addAll(old.cells());
            if (!old.equals(next)) {
                try { store.replace(old.id(),next,store.revision()); WindowSync.changed(level,old,next,new LinkedHashSet<>(old.cells())); }
                catch (IOException error) { com.mojang.logging.LogUtils.getLogger().error("Cannot persist assembly health; original selection retained",error); }
            }
        }
        for (BlockPos neighbor:neighbors) {
            if (store.membership(neighbor).isPresent() || !level.hasChunkAt(neighbor)) continue;
            var result=WindowDetection.discover(access,neighbor,work); affected.addAll(result.cells());
        }
        WindowSync.invalidate(level,affected);
    }
}
