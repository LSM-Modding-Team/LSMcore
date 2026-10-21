package net.nicomar2009.lsmcore.door.model;

import java.util.*;

/** Sole authoritative state, persisted by Core per dimension; controllers store at most a UUID/role link.
 * PLACING/REMOVING are durable journal states, not chunk load/unload or ordinary leaf updates.
 */
public record DoorRecord(UUID id,long revision,DoorStructure structure,Map<String,LeafState> leaves,
                         Phase phase,Removal removal,boolean dropsClaimed) {
    public enum Phase { PLACING, ACTIVE, REMOVING }
    public enum Removal { NONE, PLAYER, PLAYER_CREATIVE, INTEGRITY_LOSS, FAILED_PLACEMENT }
    public record LeafState(boolean open,boolean powered) {}
    public DoorRecord {
        Objects.requireNonNull(id); Objects.requireNonNull(structure); Objects.requireNonNull(phase); Objects.requireNonNull(removal);
        leaves=Map.copyOf(leaves);
        if (revision<1 || revision==Long.MAX_VALUE || leaves.size()!=structure.definition().leaves().size()) throw new IllegalArgumentException("invalid_record");
        for (var leaf:structure.definition().leaves()) if (!leaves.containsKey(leaf.id())) throw new IllegalArgumentException("missing_leaf");
        if ((phase==Phase.REMOVING)!=(removal!=Removal.NONE) || dropsClaimed && phase!=Phase.REMOVING) throw new IllegalArgumentException("invalid_lifecycle");
    }
    public DoorRecord next(Map<String,LeafState> states,Phase phase,Removal reason,boolean claimed) {
        return new DoorRecord(id,revision+1,structure,states,phase,reason,claimed);
    }
    public static DoorRecord placing(DoorDefinition definition,DoorPlacement placement) {
        var states=new LinkedHashMap<String,LeafState>();
        definition.leaves().forEach(leaf->states.put(leaf.id(),new LeafState(false,false)));
        return new DoorRecord(UUID.randomUUID(),1,new DoorStructure(definition,placement),states,Phase.PLACING,Removal.NONE,false);
    }
}
