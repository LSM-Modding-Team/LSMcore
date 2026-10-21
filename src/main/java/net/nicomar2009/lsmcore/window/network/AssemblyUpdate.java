package net.nicomar2009.lsmcore.window.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One observed chunk: BEGIN/ENTRY/END, REMOVE/UPSERT deltas, UNWATCH, BLOCKED, INVALIDATE.
 * Each record is bounded independently; a large watched chunk never creates an oversized payload.
 */
public record AssemblyUpdate(String dimension,int x,int z,long revision,int kind,String json) implements CustomPacketPayload {
    public static final int BEGIN=0,ENTRY=1,END=2,REMOVE=3,UPSERT=4,UNWATCH=5,BLOCKED=6,INVALIDATE=7;
    public static final Type<AssemblyUpdate> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lsmcore","window_assemblies"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AssemblyUpdate> CODEC=StreamCodec.of((buf,p)-> {
        buf.writeUtf(p.dimension(),256); buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeLong(p.revision());
        buf.writeVarInt(p.kind()); buf.writeUtf(p.json(),8192);
    },buf->new AssemblyUpdate(buf.readUtf(256),buf.readInt(),buf.readInt(),buf.readLong(),buf.readVarInt(),buf.readUtf(8192)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
