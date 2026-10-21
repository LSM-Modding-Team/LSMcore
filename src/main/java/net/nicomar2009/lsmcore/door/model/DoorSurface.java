package net.nicomar2009.lsmcore.door.model;

import java.util.List;
import net.nicomar2009.lsmcore.door.geometry.DoorBox;

/** Stable interaction/future-material surface, owned by part; null leaf means FIXED forever.
 * Selection encloses this surface's owner-supplied collision boxes. Empty collision is legal
 * for purely visual/interaction components. Core never infers collision from a material.
 */
public record DoorSurface(String id,String part,String leaf,DoorBox selection,List<DoorBox> collision) {
    public DoorSurface {
        DoorDefinition.identifier(id); DoorDefinition.identifier(part);
        if (leaf!=null) DoorDefinition.identifier(leaf);
        java.util.Objects.requireNonNull(selection); collision=List.copyOf(collision);
        if (collision.size()>8 || collision.stream().anyMatch(box->!selection.contains(box))) throw new IllegalArgumentException("invalid_collision");
    }
}
