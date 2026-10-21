package net.nicomar2009.lsmcore.door.geometry;

import java.util.*;
import net.nicomar2009.lsmcore.door.model.DoorRecord;

/** One proposed movement, computed only on a requested state change. Owners can test entities,
 * other leaves or special physics against the common geometry before allowing it. Sweeps are
 * conservative continuous-arc reservation boxes, not exact swept collision meshes.
 */
public record DoorMotion(String leaf,boolean opening,List<DoorBox> conservativeSweep,
                         List<DoorBox> currentCollision,List<DoorBox> targetCollision) {
    public DoorMotion {
        conservativeSweep=List.copyOf(conservativeSweep); currentCollision=List.copyOf(currentCollision); targetCollision=List.copyOf(targetCollision);
    }
    public static DoorMotion of(DoorRecord record,String id,boolean open) {
        var definition=record.structure().definition(); var leaf=definition.leaf(id);
        var current=new ArrayList<DoorBox>(); var target=new ArrayList<DoorBox>();
        for (var surface:definition.surfaces()) if (id.equals(surface.leaf())) for (var box:surface.collision()) {
            current.add(DoorTransform.box(record.structure().placement(),leaf,record.leaves().get(id).open(),box));
            target.add(DoorTransform.box(record.structure().placement(),leaf,open,box));
        }
        return new DoorMotion(id,open,record.structure().plan().sweeps().getOrDefault(id,List.of()),current,target);
    }
}
