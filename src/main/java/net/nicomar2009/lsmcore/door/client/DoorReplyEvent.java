package net.nicomar2009.lsmcore.door.client;

import net.neoforged.bus.api.Event;
import net.nicomar2009.lsmcore.door.network.DoorReply;

/** Only a matching outstanding client request reaches this event; serial identifies the operation.
 * Replies are status only. They NEVER replace newer authoritative observed geometry/state.
 */
public final class DoorReplyEvent extends Event {
    public final DoorReply reply;
    public DoorReplyEvent(DoorReply reply) { this.reply=reply; }
}
