package net.nicomar2009.lsmcore.door.geometry;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.nicomar2009.lsmcore.door.model.*;

/** Compiled once per placement. Every closed/open/swept cell requires an owner carrier/proxy.
 * No moving shape is exposed outside [0,1] in a block's local VoxelShape.
 */
public record DoorPlan(Map<BlockPos,Slot> slots,Map<String,List<DoorBox>> sweeps,BlockPos controller,Set<ChunkPos> chunks) {
    public static final int MAX_CELLS=512;
    public record Slot(BlockPos position,String part) {
        public Slot { position=position.immutable(); }
        public boolean proxy() { return part==null; }
    }
    public DoorPlan { slots=Map.copyOf(slots); var immutable=new HashMap<String,List<DoorBox>>(); sweeps.forEach((key,value)->immutable.put(key,List.copyOf(value))); sweeps=Map.copyOf(immutable); controller=controller.immutable(); chunks=Set.copyOf(chunks); }
    public static DoorPlan compile(DoorDefinition definition,DoorPlacement placement) {
        var slots=new LinkedHashMap<BlockPos,Slot>(); var sweeps=new HashMap<String,List<DoorBox>>();
        BlockPos controller=null;
        for (var cell:definition.cells()) {
            var p=cell.offset();
            var box=DoorTransform.box(placement,null,false,new DoorBox(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+1,p.getZ()+1));
            BlockPos world=new BlockPos((int)Math.round(box.minX()),(int)Math.round(box.minY()),(int)Math.round(box.minZ()));
            if (slots.putIfAbsent(world,new Slot(world,cell.id()))!=null) throw new IllegalArgumentException("duplicate_cell");
            if (cell.id().equals(definition.controller())) controller=world;
        }
        for (var surface:definition.surfaces()) {
            var leaf=surface.leaf()==null?null:definition.leaf(surface.leaf());
            var swept=leaf==null?surface.selection():DoorTransform.sweep(surface.selection(),leaf);
            var world=DoorTransform.box(placement,null,false,swept);
            for (BlockPos pos:cells(world)) {
                slots.putIfAbsent(pos,new Slot(pos,null));
                if (slots.size()>MAX_CELLS) throw new IllegalArgumentException("reserved_cell_limit");
            }
            if (leaf!=null) sweeps.computeIfAbsent(leaf.id(),key->new ArrayList<>()).add(world);
        }
        sweeps.replaceAll((key,value)->List.copyOf(value));
        var chunks=new HashSet<ChunkPos>(); slots.keySet().forEach(pos->chunks.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)));
        return new DoorPlan(slots,sweeps,Objects.requireNonNull(controller),chunks);
    }
    public static List<BlockPos> cells(DoorBox box) {
        int x0=floor(box.minX()),y0=floor(box.minY()),z0=floor(box.minZ());
        int x1=ceil(box.maxX())-1,y1=ceil(box.maxY())-1,z1=ceil(box.maxZ())-1;
        long nx=(long)x1-x0+1,ny=(long)y1-y0+1,nz=(long)z1-z0+1;
        if (nx<1 || ny<1 || nz<1 || nx>MAX_CELLS || ny>MAX_CELLS || nz>MAX_CELLS) throw new IllegalArgumentException("reserved_cell_limit");
        long count=nx*ny*nz;
        if (count<1 || count>MAX_CELLS) throw new IllegalArgumentException("reserved_cell_limit");
        var result=new ArrayList<BlockPos>((int)count);
        for (int x=x0;x<=x1;x++) for (int y=y0;y<=y1;y++) for (int z=z0;z<=z1;z++) result.add(new BlockPos(x,y,z));
        return List.copyOf(result);
    }
    private static int floor(double v) { check(v); return (int)Math.floor(v+DoorBox.EPS); }
    private static int ceil(double v) { check(v); return (int)Math.ceil(v-DoorBox.EPS); }
    private static void check(double v) { if (v<Integer.MIN_VALUE+128.0 || v>Integer.MAX_VALUE-128.0) throw new IllegalArgumentException("coordinate_limit"); }
}
