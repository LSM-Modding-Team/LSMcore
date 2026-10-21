package net.nicomar2009.lsmcore.door.model;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.door.geometry.DoorBox;

/** Complete immutable geometry snapshot, not a live registry reference. Materials stay with owners.
 * Absolute bounds are computational safeguards, unrelated to projecting-window leaf limits.
 */
public record DoorDefinition(Identifier type,DoorBox envelope,String controller,List<DoorCell> cells,
                             List<DoorLeaf> leaves,List<DoorSurface> surfaces,DoorRemovalPolicy removalPolicy,MotionPolicy motionPolicy,int schema) {
    public enum MotionPolicy { RESTRICTIVE, VANILLA }
    public DoorDefinition(Identifier type,DoorBox envelope,String controller,List<DoorCell> cells,List<DoorLeaf> leaves,List<DoorSurface> surfaces,DoorRemovalPolicy policy) {
        this(type,envelope,controller,cells,leaves,surfaces,policy,MotionPolicy.RESTRICTIVE,1);
    }
    public DoorDefinition(Identifier type,DoorBox envelope,String controller,List<DoorCell> cells,List<DoorLeaf> leaves,List<DoorSurface> surfaces) {
        this(type,envelope,controller,cells,leaves,surfaces,DoorRemovalPolicy.PLAYER_ONLY);
    }
    public DoorDefinition {
        if(schema<1)throw new IllegalArgumentException("invalid_schema"); Objects.requireNonNull(motionPolicy); Objects.requireNonNull(type); Objects.requireNonNull(envelope); Objects.requireNonNull(removalPolicy); identifier(controller);
        if (type.toString().length()>128 || Math.max(Math.max(Math.abs(envelope.minX()),Math.abs(envelope.maxX())),
                Math.max(Math.max(Math.abs(envelope.minY()),Math.abs(envelope.maxY())),Math.max(Math.abs(envelope.minZ()),Math.abs(envelope.maxZ()))))>32)
            throw new IllegalArgumentException("envelope_limit");
        cells=List.copyOf(cells); leaves=List.copyOf(leaves); surfaces=List.copyOf(surfaces);
        if (cells.isEmpty() || cells.size()>128 || leaves.isEmpty() || leaves.size()>8 || surfaces.isEmpty() || surfaces.size()>128)
            throw new IllegalArgumentException("definition_limit");
        if (surfaces.stream().mapToInt(s->1+s.collision().size()).sum()>384) throw new IllegalArgumentException("geometry_limit");
        Set<String> parts=new HashSet<>(),leafIds=new HashSet<>(),surfaceIds=new HashSet<>();
        Set<net.minecraft.core.BlockPos> positions=new HashSet<>();
        for (var cell:cells) if (!parts.add(cell.id()) || !positions.add(cell.offset())) throw new IllegalArgumentException("duplicate_cell");
        if (!parts.contains(controller)) throw new IllegalArgumentException("missing_controller");
        for (var leaf:leaves) if (!leafIds.add(leaf.id())) throw new IllegalArgumentException("duplicate_leaf");
        for (var surface:surfaces) if (!surfaceIds.add(surface.id()) || !parts.contains(surface.part())
                || surface.leaf()!=null && !leafIds.contains(surface.leaf()) || !envelope.contains(surface.selection()))
            throw new IllegalArgumentException("invalid_surface");
        for (String leaf:leafIds) if (surfaces.stream().noneMatch(s->leaf.equals(s.leaf()))) throw new IllegalArgumentException("empty_leaf");
    }
    public static void identifier(String value) {
        if (value==null || !value.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("invalid_local_id");
    }
    public DoorLeaf leaf(String id) { return leaves.stream().filter(l->l.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("unknown_leaf")); }
}
