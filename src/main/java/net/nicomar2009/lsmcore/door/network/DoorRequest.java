package net.nicomar2009.lsmcore.door.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Intent only. No client geometry, leaf choice, power, removal or desired open state is trusted. */
public record DoorRequest(UUID session,long serial,String dimension,UUID structure,BlockPos clicked,long expected,InteractionHand hand) implements CustomPacketPayload {
    public static final Type<DoorRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","door_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorRequest> CODEC=StreamCodec.of((b,p)-> {
        b.writeUUID(p.session()); b.writeLong(p.serial()); b.writeUtf(p.dimension(),256); b.writeUUID(p.structure()); b.writeBlockPos(p.clicked()); b.writeLong(p.expected()); b.writeVarInt(p.hand().ordinal());
    },b-> {
        UUID session=b.readUUID(); long serial=b.readLong(); String dimension=b.readUtf(256); UUID structure=b.readUUID();
        BlockPos clicked=b.readBlockPos(); long expected=b.readLong(); int hand=b.readVarInt();
        if (hand<0 || hand>=InteractionHand.values().length) throw new IllegalArgumentException("invalid_hand");
        return new DoorRequest(session,serial,dimension,structure,clicked,expected,InteractionHand.values()[hand]);
    });
    public DoorRequest { clicked=clicked.immutable(); java.util.Objects.requireNonNull(hand); if (serial<1 || expected<1) throw new IllegalArgumentException("invalid_request"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
