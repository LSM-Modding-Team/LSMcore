package net.nicomar2009.lsmcore.window.integration;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.nicomar2009.lsmcore.window.model.SavedSelection;

/** Register an adapter for consumer-owned blocks during common setup, on both logical sides.
 * All methods run on the server thread; never load chunks in these callbacks.
 * A renderer consumes SavedSelection + WindowGeometry; Core registers no blocks/models/materials.
 */
public interface WindowAdapter {
    Optional<WindowAssembly> describe(ServerLevel level,BlockPos clicked);
    Optional<WindowCell> cell(ServerLevel level,BlockPos pos);
    /** Must enforce the owner's claims/locks and paint-surface permissions. No permissive default. */
    boolean mayConfigure(ServerPlayer player,BlockPos pos,Direction interactionFace);
    /** Both adjacent adapters must consent. Core additionally requires actual touching surfaces. */
    boolean allowsConnection(WindowCell local,WindowCell neighbor);
    Optional<SavedSelection> readSelection(ServerLevel level,WindowAssembly assembly);
    /** Persist the complete versioned snapshot atomically in the owner's data and mark it dirty.
     * Do not change registry IDs, opening/hinge states, paint data, or neighboring assemblies.
     * Reject failures BEFORE modifying state. Do not resolve the ID again when rendering/loading.
     */
    void writeSelection(ServerLevel level,WindowAssembly assembly,SavedSelection selection);
    /** Invalidate only these affected cells and sync owner data to tracking clients.
     * Include block-entity packets/model data/mesh invalidation appropriate to the consumer renderer.
     */
    void refreshRendering(ServerLevel level,WindowAssembly assembly,List<BlockPos> affectedCells);
}
