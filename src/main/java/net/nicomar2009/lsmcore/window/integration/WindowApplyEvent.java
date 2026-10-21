package net.nicomar2009.lsmcore.window.integration;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.nicomar2009.lsmcore.window.model.SavedSelection;

/** Main NeoForge bus, server thread, after validation and before owner writes.
 * Claim/protection integrations may veto. Cancellation never changes an assembly.
 */
public final class WindowApplyEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final WindowAssembly assembly;
    private final SavedSelection selection;
    private final List<BlockPos> cells;
    public WindowApplyEvent(ServerPlayer player,WindowAssembly assembly,SavedSelection selection,List<BlockPos> cells) {
        this.player=player; this.assembly=assembly; this.selection=selection; this.cells=List.copyOf(cells);
    }
    public ServerPlayer player() { return player; }
    public WindowAssembly assembly() { return assembly; }
    /** Null means authorized reset/removal, never a fabricated default snapshot. */
    public SavedSelection selection() { return selection; }
    public List<BlockPos> cells() { return cells; }
}
