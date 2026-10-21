package net.nicomar2009.lsmcore.window.client;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.nicomar2009.lsmcore.window.network.*;

/** Per-screen correlation; late/duplicate replies never complete a newer operation. */
final class EditorChannel {
    final UUID session=UUID.randomUUID();
    final String dimension;
    WindowSnapshot snapshot;
    WindowRequest.Action pending,completed;
    String status="";
    private long next,waiting,sent;
    EditorChannel(String dimension,WindowSnapshot initial) { this.dimension=dimension; snapshot=initial; }
    void send(WindowRequest.Action action,boolean hasTarget,BlockPos clicked,boolean manual,BlockPos first,BlockPos last,String json) {
        if (pending!=null) return;
        pending=action; waiting=++next; sent=System.nanoTime(); status="waiting";
        ClientPacketDistributor.sendToServer(new WindowRequest(session,waiting,dimension,action,hasTarget,clicked,manual,first,last,json,
                snapshot==null?0:snapshot.catalogRevision(),snapshot==null?0:snapshot.policyRevision(),snapshot==null?0:snapshot.placedRevision()));
    }
    boolean accept(WindowReply reply) {
        if (!session.equals(reply.session()) || reply.serial()!=waiting || pending==null || !dimension.equals(reply.dimension())) return false;
        completed=pending; pending=null;
        try {
            if (!reply.status().matches("[a-z_]{1,64}")) throw new IllegalArgumentException("Invalid status");
            if (!reply.json().isEmpty()) snapshot=WindowSnapshot.decode(reply.json());
            status=reply.status();
        } catch (RuntimeException invalid) { snapshot=null; status="bad_snapshot"; }
        return true;
    }
    void tick() {
        if (pending!=null && System.nanoTime()-sent>8_000_000_000L) { pending=null; status="timeout"; }
    }
}
