package net.nicomar2009.lsmcore.window.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Versioned bounded snapshot; client handler is registered exclusively on Dist.CLIENT. */
public record WindowReply(UUID session,String dimension,String json) implements CustomPacketPayload {
    public static final Type<WindowReply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","window_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WindowReply> CODEC=StreamCodec.of((buf,value)-> {
        buf.writeUUID(value.session()); buf.writeUtf(value.dimension(),256); buf.writeUtf(value.json(),65536);
    },buf->new WindowReply(buf.readUUID(),buf.readUtf(256),buf.readUtf(65536)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
