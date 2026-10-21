package net.nicomar2009.lsmcore.window.integration;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.Event;

/** Consumers invalidate only their cached window meshes/queries intersecting this chunk.
 * Delivered after local block-change hooks and after authoritative client replica changes.
 * Do not scan the world; use the consumer's existing render/block-entity index.
 */
public final class WindowGeometryChangedEvent extends Event {
    private final Level level;
    private final ChunkPos chunk;
    public WindowGeometryChangedEvent(Level level,ChunkPos chunk) { this.level=level; this.chunk=chunk; }
    public Level level() { return level; }
    public ChunkPos chunk() { return chunk; }
}
