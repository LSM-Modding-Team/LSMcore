package net.nicomar2009.lsmcore.window.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.nicomar2009.lsmcore.window.catalog.LayoutJson;

/** One file/index per dimension, independent of loaded chunks and consumer mods.
 * Files with unknown/invalid data are preserved verbatim and fail closed, never treated as empty.
 */
public final class WorldAssemblies {
    private final Path file;
    private Map<UUID,PlacedAssembly> records=Map.of();
    private Map<BlockPos,UUID> members=new HashMap<>();
    private Map<ChunkPos,Set<UUID>> chunks=new HashMap<>();
    private long revision;
    private boolean readable=true;
    public WorldAssemblies(Path file) {
        this.file=file;
        if (!Files.exists(file)) return;
        try {
            var json=JsonFiles.read(file,16*1024*1024);
            LayoutJson.requireKeys(json,"version","revision","assemblies");
            if (LayoutJson.integer(json,"version")!=1) throw new IllegalArgumentException("Unknown dimension storage");
            revision=LayoutJson.longInteger(json,"revision"); if (revision<0 || revision==Long.MAX_VALUE) throw new IllegalArgumentException("Invalid revision");
            var array=json.getAsJsonArray("assemblies");
            if (array.size()>4096) throw new IllegalArgumentException("assembly_limit");
            var loaded=new LinkedHashMap<UUID,PlacedAssembly>();
            for (var value:array) {
                var record=AssemblyJson.read(value.getAsJsonObject());
                if (loaded.putIfAbsent(record.id(),record)!=null) throw new IllegalArgumentException("Duplicate assembly ID");
            }
            install(loaded);
        } catch (IOException | RuntimeException error) {
            readable=false; revision=0; com.mojang.logging.LogUtils.getLogger().error("Preserving unreadable assembly storage {}",file,error);
        }
    }
    public boolean readable() { return readable; }
    public void requireReadable() { if (!readable) throw new IllegalArgumentException("storage_unavailable"); }
    public long revision() { return revision; }
    public Optional<UUID> membership(BlockPos pos) { requireReadable(); return Optional.ofNullable(members.get(pos)); }
    public Optional<PlacedAssembly> at(BlockPos pos) { return membership(pos).map(records::get); }
    public Collection<PlacedAssembly> inChunk(ChunkPos chunk) {
        requireReadable(); return chunks.getOrDefault(chunk,Set.of()).stream().map(records::get).toList();
    }
    /** Atomic replace/delete of ONE explicitly selected assembly. No implicit merge/split. */
    public void replace(UUID id,PlacedAssembly next,long expected) throws IOException {
        requireReadable(); if (expected!=revision) throw new IllegalArgumentException("stale_target");
        var changed=new LinkedHashMap<>(records);
        if (next==null) changed.remove(id); else {
            if (!next.id().equals(id)) throw new IllegalArgumentException("Invalid assembly identity");
            changed.put(id,next);
        }
        var old=records.get(id);
        List<BlockPos> previous=old==null?List.of():old.cells(),following=next==null?List.of():next.cells();
        if (changed.size()>4096 || (long)members.size()-previous.size()+following.size()>262144) throw new IllegalArgumentException("assembly_limit");
        for (BlockPos pos:following) {
            UUID member=members.get(pos); if (member!=null && !member.equals(id)) throw new IllegalArgumentException("explicit_boundary");
        }
        var json=new JsonObject(); json.addProperty("version",1); json.addProperty("revision",revision+1);
        var array=new JsonArray(); changed.values().forEach(value->array.add(AssemblyJson.write(value))); json.add("assemblies",array);
        JsonFiles.write(file,json);
        // Update only the edited record's indexes. Other placed sets are never recalculated.
        for (BlockPos pos:previous) members.remove(pos,id);
        if (old!=null) for (ChunkPos chunk:old.chunks()) {
            Set<UUID> ids=chunks.get(chunk); ids.remove(id); if (ids.isEmpty()) chunks.remove(chunk);
        }
        for (BlockPos pos:following) members.put(pos,id);
        if (next!=null) for (ChunkPos chunk:next.chunks()) chunks.computeIfAbsent(chunk,key->new HashSet<>()).add(id);
        records=Collections.unmodifiableMap(changed); revision++;
    }
    private record Index(Map<BlockPos,UUID> members,Map<ChunkPos,Set<UUID>> chunks) {}
    private static Index index(Map<UUID,PlacedAssembly> records) {
        if (records.size()>4096) throw new IllegalArgumentException("assembly_limit");
        var members=new HashMap<BlockPos,UUID>(); var chunks=new HashMap<ChunkPos,Set<UUID>>();
        for (var record:records.values()) for (BlockPos pos:record.cells()) {
            if (members.putIfAbsent(pos,record.id())!=null) throw new IllegalArgumentException("explicit_boundary");
            if (members.size()>262144) throw new IllegalArgumentException("assembly_limit");
            chunks.computeIfAbsent(new ChunkPos(pos),ignored->new HashSet<>()).add(record.id());
        }
        return new Index(members,chunks);
    }
    private void install(Map<UUID,PlacedAssembly> next) {
        var index=index(next); records=Collections.unmodifiableMap(new LinkedHashMap<>(next));
        members=index.members(); chunks=index.chunks();
    }
}
