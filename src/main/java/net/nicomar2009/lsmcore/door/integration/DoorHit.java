package net.nicomar2009.lsmcore.door.integration;

import java.util.Optional;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import net.nicomar2009.lsmcore.door.model.DoorView;

/** Server ray-test against the current pose, including fixed panels and OPEN leaves.
 * Fixed surfaces occlude leaves but cannot be toggled. A tie across different leaves or a
 * fixed/moving boundary is ambiguous. Same-leaf ties remain clickable, with null surface ID
 * (and possibly null part ID) to expose the unresolved paint/interaction surface explicitly.
 */
public record DoorHit(String surface,String part,String leaf,Vec3 location,double distanceSquared,boolean ambiguous) {
    public static Optional<DoorHit> pick(DoorView view,Vec3 from,Vec3 to) {
        DoorHit result=null;
        for (var surface:view.geometry().surfaces()) {
            var box=surface.world().aabb();
            var hit=box.contains(from)?Optional.of(from):box.clip(from,to);
            if (hit.isEmpty()) continue;
            double distance=from.distanceToSqr(hit.get());
            if (result==null || distance<result.distanceSquared()-1e-10)
                result=new DoorHit(surface.id(),surface.part(),surface.leaf(),hit.get(),distance,false);
            else if (Math.abs(distance-result.distanceSquared())<=1e-10)
                result=new DoorHit(null,Objects.equals(result.part(),surface.part())?result.part():null,
                        Objects.equals(result.leaf(),surface.leaf())?result.leaf():null,result.location(),result.distanceSquared(),
                        result.ambiguous() || !Objects.equals(result.leaf(),surface.leaf()));
        }
        return Optional.ofNullable(result);
    }
}
