package net.nicomar2009.lsmcore.door.integration;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.nicomar2009.lsmcore.door.model.*;
import net.nicomar2009.lsmcore.door.geometry.DoorPlan;
import net.nicomar2009.lsmcore.door.geometry.DoorMotion;

/** Owner implementation, independent of other consumer mods. No method may load chunks.
 * Permission/profile/motion queries must be pure and must not reenter mutations.
 * Cell writers are idempotent, suppress vanilla drops and MUST NOT replace foreign occupants.
 * Stamp each carrier/proxy with structure UUID and slot identity. No leaf state is stored there.
 */
public interface DoorAdapter {
    /** Verify actual owner block plus UUID/slot link, never merely a matching block ID. */
    boolean matches(ServerLevel level,DoorRecord record,DoorPlan.Slot slot);
    /** Additional support/fluid/replaceability constraints, after Core's basic checks. */
    boolean canPlace(ServerPlayer player,DoorRecord record,DoorPlan.Slot slot);
    void place(ServerLevel level,DoorRecord record,DoorPlan.Slot slot);
    void removeWithoutDrops(ServerLevel level,DoorRecord record,DoorPlan.Slot slot);
    /** actor is null only for redstone/integrity recovery; protection must handle this explicitly. */
    boolean authorize(ServerLevel level,ServerPlayer actor,DoorAction action,DoorRecord record,Set<BlockPos> affected);
    /** Entity/special physics policy. Core already reserves the conservative sweep and verifies cells.
     * No entity is pushed automatically; reject obstructed movement here; physics/pushing, if desired, remains owner work.
     */
    boolean canMove(ServerLevel level,DoorRecord record,DoorMotion motion);
    /** AFTER durable commit: sounds, owner block updates and render/collision invalidation only.
     * before is null on first creation; after is null on final deletion. Compare leaf bits/phase
     * to emit each intended sound once. Throwing cannot undo the committed state. Never copy authoritative leaf state into controllers.
     */
    default void changed(ServerLevel level,DoorRecord before,DoorRecord after) {}
    /** At most once after all pieces are gone, durable claim precedes this call. Crash in the
     * claim->callback gap can lose drops; external side effects cannot be made atomic with a file.
     * DoorRemovalPolicy controls player/integrity drops; creative and failed placement never drop.
     * Default callback emits nothing; do not call vanilla cell drops.
     */
    default void drops(ServerLevel level,DoorRecord removed) {}
}
