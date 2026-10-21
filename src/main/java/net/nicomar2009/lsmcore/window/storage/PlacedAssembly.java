package net.nicomar2009.lsmcore.window.storage;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.nicomar2009.lsmcore.window.integration.WindowAssembly;
import net.nicomar2009.lsmcore.window.model.SavedSelection;

/** Immutable explicit selection. Removing/changing pieces NEVER splits or redistributes this record.
 * Original membership remains reserved until authorized reset or explicit reconfiguration.
 */
public record PlacedAssembly(UUID id,WindowAssembly assembly,SavedSelection selection,String health) {
    public PlacedAssembly {
        if (id==null || assembly==null || selection==null || health==null || !health.matches("[a-z_]{1,64}"))
            throw new IllegalArgumentException("Invalid assembly record");
        if (!net.nicomar2009.lsmcore.window.geometry.LayoutValidator.fits(selection.layout(),assembly.available()))
            throw new IllegalArgumentException("Saved layout does not fit its rectangle");
        if (cells(assembly).size()>4096) throw new IllegalArgumentException("cell_limit");
    }
    public List<BlockPos> cells() { return cells(assembly); }
    public static List<BlockPos> cells(WindowAssembly assembly) {
        var bounds=assembly.available();
        int w=(int)Math.ceil(bounds.maxX()-1e-8),h=(int)Math.ceil(bounds.maxY()-1e-8);
        if ((long)w*h>4096) throw new IllegalArgumentException("cell_limit");
        var cells=new ArrayList<BlockPos>();
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) cells.add(assembly.cell(x,y).immutable());
        return List.copyOf(cells);
    }
    public Set<ChunkPos> chunks() {
        var chunks=new HashSet<ChunkPos>(); cells().forEach(pos->chunks.add(new ChunkPos(pos))); return Set.copyOf(chunks);
    }
}
