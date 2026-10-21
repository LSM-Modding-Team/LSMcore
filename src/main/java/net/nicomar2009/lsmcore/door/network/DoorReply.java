package net.nicomar2009.lsmcore.door.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DoorReply(UUID session,long serial,String dimension,UUID structure,String code,long revision) implements CustomPacketPayload {
    public static final Type<DoorReply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","door_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorReply> CODEC=StreamCodec.of((b,p)-> {
        b.writeUUID(p.session()); b.writeLong(p.serial()); b.writeUtf(p.dimension(),256); b.writeUUID(p.structure()); b.writeUtf(p.code(),64); b.writeLong(p.revision());
    },b->new DoorReply(b.readUUID(),b.readLong(),b.readUtf(256),b.readUUID(),b.readUtf(64),b.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
