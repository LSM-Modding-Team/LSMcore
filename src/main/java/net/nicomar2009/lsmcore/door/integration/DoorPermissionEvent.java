package net.nicomar2009.lsmcore.door.integration;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.neoforged.bus.api.*;
import net.nicomar2009.lsmcore.door.model.DoorRecord;

/** Protection hook on the server thread. actor may be null for system/redstone operations. */
public final class DoorPermissionEvent extends Event implements ICancellableEvent {
    public final ServerLevel level;
    public final ServerPlayer actor;
    public final DoorAction action;
    public final DoorRecord structure;
    public final Set<BlockPos> affected;
    public DoorPermissionEvent(ServerLevel level,ServerPlayer actor,DoorAction action,DoorRecord structure,Set<BlockPos> affected) {
        this.level=level; this.actor=actor; this.action=action; this.structure=structure; this.affected=Set.copyOf(affected);
    }
}
