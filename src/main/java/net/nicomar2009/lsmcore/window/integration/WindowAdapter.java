package net.nicomar2009.lsmcore.window.integration;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.nicomar2009.lsmcore.window.geometry.WindowGeometry;

/** Register on common setup for consumer-owned blocks. No consumer-to-consumer imports needed.
 * cell/individual are read-only, bounded, usable with client snapshots, and MUST NOT load chunks.
 * Core alone owns assembly identity, membership and selections. Owners retain paint/solid/open data.
 */
public interface WindowAdapter {
    /** Return true for unknown/invalid owner-specific selection data; Core will fail closed.
     * This is a format/integrity check for any piece, never an age-based geometry rule.
     */
    default boolean hasUnmanagedConfiguration(BlockGetter level,BlockPos pos) { return false; }
    Optional<WindowCell> cell(BlockGetter level,BlockPos pos);
    boolean mayConfigure(ServerPlayer player,BlockPos pos,Direction interactionFace);
    /** Both adapters must consent, including normal/paintable connections sharing a family. */
    boolean allowsConnection(WindowCell local,WindowCell neighbor);
    /** Default: a closed unit sash clipped to the piece's original available surface.
     * Override for original partial geometry; return cell-local closed surfaces, at most 32.
     * Do not connect individual fallback pieces visually to an inferred larger opening.
     */
    default List<WindowGeometry.Surface> individual(BlockGetter level,BlockPos pos,WindowCell cell) {
        return WindowGeometry.individual(cell.available());
    }
    /** Invalidate this cell's owner mesh/model data only; never change blocks or opening/paint data. */
    void geometryChanged(Level level,BlockPos pos);
}
