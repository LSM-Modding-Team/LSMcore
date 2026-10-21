package net.nicomar2009.lsmcore.window.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Empty JSON is a lightweight explanatory rejection (e.g. frequency limit), not a missing response. */
public record WindowReply(UUID session,long serial,String dimension,String status,String json) implements CustomPacketPayload {
    public static final Type<WindowReply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","window_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WindowReply> CODEC=StreamCodec.of((buf,p)-> {
        buf.writeUUID(p.session()); buf.writeLong(p.serial()); buf.writeUtf(p.dimension(),256); buf.writeUtf(p.status(),64); buf.writeUtf(p.json(),65536);
    },buf->new WindowReply(buf.readUUID(),buf.readLong(),buf.readUtf(256),buf.readUtf(64),buf.readUtf(65536)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
