package net.nicomar2009.lsmcore.door.geometry;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.*;
import net.nicomar2009.lsmcore.door.model.*;

/** Immutable resolved pose, built only for changed poses and cached by server/client storage.
 * Consumers render each surface using DoorTransform with the same boolean open state.
 */
public record DoorGeometry(Map<BlockPos,Cell> cells,List<Surface> surfaces) {
    public record Surface(String id,String part,String leaf,DoorBox world) {}
    public record Fragment(String surface,String part,String leaf,DoorBox local) {}
    public record Cell(List<Fragment> selection,List<DoorBox> collision,VoxelShape outline,VoxelShape solid) {
        public Cell { selection=List.copyOf(selection); collision=List.copyOf(collision); }
        public static final Cell EMPTY=new Cell(List.of(),List.of(),Shapes.empty(),Shapes.empty());
    }
    public DoorGeometry { cells=Map.copyOf(cells); surfaces=List.copyOf(surfaces); }
    public Cell at(BlockPos pos) { return cells.getOrDefault(pos,Cell.EMPTY); }
    public static DoorGeometry resolve(DoorRecord record) {
        var definition=record.structure().definition(); var placement=record.structure().placement();
        var selections=new HashMap<BlockPos,List<Fragment>>(); var collisions=new HashMap<BlockPos,List<DoorBox>>();
        var surfaces=new ArrayList<Surface>();
        for (var surface:definition.surfaces()) {
            DoorLeaf leaf=surface.leaf()==null?null:definition.leaf(surface.leaf());
            boolean open=leaf!=null && record.leaves().get(leaf.id()).open();
            var box=DoorTransform.box(placement,leaf,open,surface.selection());
            surfaces.add(new Surface(surface.id(),surface.part(),surface.leaf(),box));
            for (BlockPos pos:DoorPlan.cells(box)) selections.computeIfAbsent(pos,p->new ArrayList<>()).add(
                    new Fragment(surface.id(),surface.part(),surface.leaf(),clip(box,pos)));
            for (DoorBox collision:surface.collision()) {
                var world=DoorTransform.box(placement,leaf,open,collision);
                for (BlockPos pos:DoorPlan.cells(world)) collisions.computeIfAbsent(pos,p->new ArrayList<>()).add(clip(world,pos));
            }
        }
        var cells=new HashMap<BlockPos,Cell>();
        for (BlockPos pos:record.structure().plan().slots().keySet()) {
            var selection=selections.getOrDefault(pos,List.of()); var collision=collisions.getOrDefault(pos,List.of());
            cells.put(pos,new Cell(selection,collision,shape(selection.stream().map(Fragment::local).toList()),shape(collision)));
        }
        if (!cells.keySet().containsAll(selections.keySet()) || !cells.keySet().containsAll(collisions.keySet()))
            throw new IllegalArgumentException("geometry_outside_reservation");
        return new DoorGeometry(cells,surfaces);
    }
    private static DoorBox clip(DoorBox b,BlockPos p) {
        return new DoorBox(Math.max(0,b.minX()-p.getX()),Math.max(0,b.minY()-p.getY()),Math.max(0,b.minZ()-p.getZ()),
                Math.min(1,b.maxX()-p.getX()),Math.min(1,b.maxY()-p.getY()),Math.min(1,b.maxZ()-p.getZ()));
    }
    private static VoxelShape shape(List<DoorBox> boxes) {
        if (boxes.size()>64) throw new IllegalArgumentException("cell_geometry_limit");
        VoxelShape result=Shapes.empty();
        for (DoorBox b:boxes) result=Shapes.or(result,Shapes.box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));
        return result;
    }
}
