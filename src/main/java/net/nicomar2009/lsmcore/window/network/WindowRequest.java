package net.nicomar2009.lsmcore.window.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Every response echoes session AND monotonically increasing request number.
 * Design parameters are accepted only for authorized catalog editing; application resolves server IDs.
 */
public record WindowRequest(UUID session,long serial,String dimension,Action action,boolean hasTarget,BlockPos clicked,
                            boolean manual,BlockPos first,BlockPos last,String json,long catalogRevision,
                            long policyRevision,long placedRevision) implements CustomPacketPayload {
    public enum Action { QUERY,SAVE,DELETE,APPLY,RESET,POLICY }
    public static final Type<WindowRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","window_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WindowRequest> CODEC=StreamCodec.of((buf,p)-> {
        buf.writeUUID(p.session()); buf.writeLong(p.serial()); buf.writeUtf(p.dimension(),256); buf.writeVarInt(p.action().ordinal());
        buf.writeBoolean(p.hasTarget()); buf.writeBlockPos(p.clicked()); buf.writeBoolean(p.manual()); buf.writeBlockPos(p.first()); buf.writeBlockPos(p.last());
        buf.writeUtf(p.json(),8192); buf.writeLong(p.catalogRevision()); buf.writeLong(p.policyRevision()); buf.writeLong(p.placedRevision());
    },buf-> {
        UUID session=buf.readUUID(); long serial=buf.readLong(); String dimension=buf.readUtf(256); int action=buf.readVarInt();
        if (serial<1 || action<0 || action>=Action.values().length) throw new IllegalArgumentException("Invalid request header");
        return new WindowRequest(session,serial,dimension,Action.values()[action],buf.readBoolean(),buf.readBlockPos(),
                buf.readBoolean(),buf.readBlockPos(),buf.readBlockPos(),buf.readUtf(8192),buf.readLong(),buf.readLong(),buf.readLong());
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
