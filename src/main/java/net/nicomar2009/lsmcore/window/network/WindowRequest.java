package net.nicomar2009.lsmcore.window.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.window.model.WindowLayout;

/** Parameters are used ONLY for admin catalog edits. Apply accepts an ID and resolves server data. */
public record WindowRequest(UUID session,String dimension,Action action,boolean hasTarget,BlockPos target,String id,
                            double width,double height,int columns,int rows,long revision) implements CustomPacketPayload {
    public enum Action { SYNC, SAVE, DELETE, APPLY, RESET }
    public static final Type<WindowRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","window_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WindowRequest> CODEC=StreamCodec.of((buf,value)-> {
        buf.writeUUID(value.session()); buf.writeUtf(value.dimension(),256); buf.writeVarInt(value.action().ordinal()); buf.writeBoolean(value.hasTarget());
        buf.writeBlockPos(value.target()); buf.writeUtf(value.id(),96); buf.writeDouble(value.width()); buf.writeDouble(value.height());
        buf.writeVarInt(value.columns()); buf.writeVarInt(value.rows()); buf.writeLong(value.revision());
    },buf-> {
        UUID session=buf.readUUID(); String dimension=buf.readUtf(256); int action=buf.readVarInt();
        if (action<0 || action>=Action.values().length) throw new IllegalArgumentException("Invalid window action");
        return new WindowRequest(session,dimension,Action.values()[action],buf.readBoolean(),buf.readBlockPos(),buf.readUtf(96),
                buf.readDouble(),buf.readDouble(),buf.readVarInt(),buf.readVarInt(),buf.readLong());
    });
    public WindowLayout proposedLayout() { return new WindowLayout(id,width,height,columns,rows); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
