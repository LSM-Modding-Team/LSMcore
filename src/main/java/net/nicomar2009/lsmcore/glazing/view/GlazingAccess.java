package net.nicomar2009.lsmcore.glazing.view;

/** Custom render/world snapshots can implement this without any live-world lookup.
 * A stable source identity and revision must cover ALL glass profiles, policies and chunk
 * availability. Change the revision before exposing changed data; frozen snapshots stay immutable.
 */
public interface GlazingAccess {
    Object source();
    long revision();
    Cell read(net.minecraft.core.BlockPos pos);
    record Cell(boolean available,GlazingSample glass) {
        public Cell { if (!available && glass!=null) throw new IllegalArgumentException("Unavailable cell cannot have glass"); }
        public static Cell missing() { return new Cell(false,null); }
        public static Cell other() { return new Cell(true,null); }
    }
}
