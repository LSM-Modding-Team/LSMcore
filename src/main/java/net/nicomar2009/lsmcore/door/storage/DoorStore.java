package net.nicomar2009.lsmcore.door.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.nicomar2009.lsmcore.door.model.*;

/** Single per-dimension authority and derived indexes. Server-thread only, no ticking or chunk loads.
 * Unknown/corrupt files are preserved verbatim and block ALL reads/writes until external recovery.
 */
public final class DoorStore {
    private final Path file;
    private final Map<UUID,DoorView> records=new LinkedHashMap<>();
    private final Map<UUID,JsonObject> snapshots=new HashMap<>();
    private final Map<BlockPos,UUID> members=new HashMap<>();
    private final Map<ChunkPos,Set<UUID>> chunks=new HashMap<>();
    private boolean readable=true;
    private long revision;
    public DoorStore(Path file) {
        this.file=file;
        if (!Files.exists(file)) return;
        try {
            var json=DoorFiles.read(file); DoorJson.keys(json,"version","revision","structures");
            if (DoorJson.integer(json,"version")!=1) throw new IllegalArgumentException("unknown_storage_version");
            revision=DoorJson.numberLong(json,"revision"); if (revision<0 || revision==Long.MAX_VALUE) throw new IllegalArgumentException("invalid_revision");
            var data=json.getAsJsonArray("structures"); if (data.size()>256) throw new IllegalArgumentException("structure_limit");
            for (var item:data) {
                if (item.toString().length()>DoorJson.MAX_JSON) throw new IllegalArgumentException("snapshot_limit");
                var r=DoorJson.read(item.getAsJsonObject()); var view=DoorView.of(r);
                if (records.containsKey(r.id())) throw new IllegalArgumentException("duplicate_structure");
                validate(r,null); install(view); snapshots.put(r.id(),item.getAsJsonObject());
            }
        } catch (IOException | RuntimeException error) {
            readable=false; snapshots.clear(); records.clear(); members.clear(); chunks.clear();
            com.mojang.logging.LogUtils.getLogger().error("Preserving unreadable door storage {}",file,error);
        }
    }
    public boolean readable() { return readable; }
    public long revision() { return revision; }
    public void requireReadable() { if (!readable) throw new IllegalArgumentException("storage_unavailable"); }
    public Optional<DoorView> get(UUID id) { requireReadable(); return Optional.ofNullable(records.get(id)); }
    public Optional<DoorView> at(BlockPos pos) { requireReadable(); return Optional.ofNullable(members.get(pos)).map(records::get); }
    public List<DoorView> inChunk(ChunkPos chunk) { requireReadable(); return chunks.getOrDefault(chunk,Set.of()).stream().map(records::get).toList(); }
    /** Internal service transaction. Persist FIRST, then expose indexes/derived pose; expected is record revision (0 for new). */
    public void replace(UUID id,DoorRecord next,long expected) throws IOException {
        requireReadable(); var old=records.get(id);
        if ((old==null?0:old.record().revision())!=expected || revision>=Long.MAX_VALUE-1) throw new IllegalArgumentException("stale_structure");
        DoorView view=null;
        if (next!=null) {
            if (!next.id().equals(id) || next.revision()!=expected+1) throw new IllegalArgumentException("invalid_revision");
            if (old!=null && old.record().structure()!=next.structure()) throw new IllegalArgumentException("immutable_structure");
            validate(next,old); view=DoorView.updated(next,old);
        }
        var json=new JsonObject(); json.addProperty("version",1); json.addProperty("revision",revision+1); var array=new JsonArray();
        for (var entry:snapshots.entrySet()) if (!entry.getKey().equals(id)) array.add(entry.getValue());
        JsonObject snapshot=next==null?null:DoorJson.write(next);
        if (snapshot!=null) array.add(snapshot); json.add("structures",array); DoorFiles.write(file,json);
        if (old!=null) uninstall(old); if (view!=null) { install(view); snapshots.put(id,snapshot); }
        else snapshots.remove(id); revision++;
    }
    private void validate(DoorRecord r,DoorView old) {
        if (old==null && records.size()>=256 || (long)members.size()-(old==null?0:old.record().structure().plan().slots().size())
                +r.structure().plan().slots().size()>65536) throw new IllegalArgumentException("structure_limit");
        for (BlockPos pos:r.structure().plan().slots().keySet()) {
            UUID member=members.get(pos); if (member!=null && !member.equals(r.id())) throw new IllegalArgumentException("occupied_structure");
        }
    }
    private void install(DoorView view) {
        var r=view.record(); records.put(r.id(),view);
        r.structure().plan().slots().keySet().forEach(pos->members.put(pos,r.id()));
        r.structure().plan().chunks().forEach(chunk->chunks.computeIfAbsent(chunk,key->new HashSet<>()).add(r.id()));
    }
    private void uninstall(DoorView view) {
        var r=view.record(); records.remove(r.id()); r.structure().plan().slots().keySet().forEach(pos->members.remove(pos,r.id()));
        for (ChunkPos chunk:r.structure().plan().chunks()) { var ids=chunks.get(chunk); ids.remove(r.id()); if (ids.isEmpty()) chunks.remove(chunk); }
    }
}
