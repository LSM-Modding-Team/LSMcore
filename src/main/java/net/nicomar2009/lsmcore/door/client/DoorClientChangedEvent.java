package net.nicomar2009.lsmcore.door.client;

import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.Event;

/** CLIENT bus event: invalidate only meshes/selection caches for this observed chunk.
 * Null chunk means world reset/disconnect. No block renderer is registered by Core.
 */
public final class DoorClientChangedEvent extends Event {
    public final ChunkPos chunk;
    public DoorClientChangedEvent(ChunkPos chunk) { this.chunk=chunk; }
}
