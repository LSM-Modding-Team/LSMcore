package net.nicomar2009.lsmcore.door.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.door.storage.DoorJson;

/** Observed-chunk snapshot/deltas only; one bounded record per payload. */
public record DoorUpdate(String dimension,int x,int z,long revision,int kind,String json) implements CustomPacketPayload {
    public static final int BEGIN=0,ENTRY=1,END=2,UPSERT=3,REMOVE=4,UNWATCH=5,BLOCKED=6,STATE=7;
    public static final Type<DoorUpdate> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","door_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorUpdate> CODEC=StreamCodec.of((b,p)-> {
        b.writeUtf(p.dimension(),256); b.writeInt(p.x()); b.writeInt(p.z()); b.writeLong(p.revision()); b.writeVarInt(p.kind()); b.writeUtf(p.json(),DoorJson.MAX_JSON);
    },b->new DoorUpdate(b.readUtf(256),b.readInt(),b.readInt(),b.readLong(),b.readVarInt(),b.readUtf(DoorJson.MAX_JSON)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
