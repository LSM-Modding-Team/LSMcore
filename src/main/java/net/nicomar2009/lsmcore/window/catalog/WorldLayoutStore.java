package net.nicomar2009.lsmcore.window.catalog;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.nicomar2009.lsmcore.window.geometry.LayoutValidator;
import net.nicomar2009.lsmcore.window.model.*;

/** World-owned, bounded catalog; writes are transactional and never touch the bundled catalog.
 * Malformed files remain untouched and make editing unavailable until repaired by an administrator.
 */
public final class WorldLayoutStore {
    private static final int MAX_BYTES=262144;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private Map<String,WindowLayout> entries=Map.of();
    private long revision;
    private boolean writable=true;
    public WorldLayoutStore(Path file) {
        this.file=file;
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file)>MAX_BYTES) throw new IOException("Window catalog exceeds size limit");
            var root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if (LayoutJson.integer(root,"version")!=1 || !WindowLayout.PROFILE.equals(root.get("profile").getAsString()))
                throw new IOException("Unsupported window catalog format");
            var array=root.getAsJsonArray("custom");
            if (array.size()>LayoutLimits.HARD.maxCustomLayouts()) throw new IOException("Too many custom layouts");
            var loaded=new LinkedHashMap<String,WindowLayout>();
            for (var element:array) {
                var layout=LayoutJson.read(element.getAsJsonObject());
                LayoutValidator.validate(layout,LayoutLimits.HARD).requireValid();
                if (!customId(layout.id()) || loaded.putIfAbsent(layout.id(),layout)!=null)
                    throw new IOException("Duplicate or protected layout ID");
            }
            entries=Collections.unmodifiableMap(loaded);
        } catch (IOException | RuntimeException error) {
            writable=false;
            com.mojang.logging.LogUtils.getLogger().error("Cannot load window catalog {}; preserving file and disabling catalog edits",file,error);
        }
    }
    public Map<String,WindowLayout> entries() { return entries; }
    public long revision() { return revision; }
    public boolean writable() { return writable; }
    public static boolean customId(String id) { return id!=null && id.matches("custom:[a-z0-9_-]{1,64}"); }
    public Optional<WindowLayout> resolve(String id) {
        var base=BaseLayouts.entries().get(id);
        return Optional.ofNullable(base!=null ? base : entries.get(id));
    }
    public void save(WindowLayout layout, long expectedRevision, LayoutLimits limits) throws IOException {
        checkRevision(expectedRevision);
        if (!customId(layout.id())) throw new IllegalArgumentException("protected_catalog");
        LayoutValidator.validate(layout,limits).requireValid();
        var changed=new LinkedHashMap<>(entries);
        if (!changed.containsKey(layout.id()) && changed.size()>=limits.maxCustomLayouts())
            throw new IllegalArgumentException("catalog_limit");
        changed.put(layout.id(),layout); persist(changed);
    }
    public void delete(String id,long expectedRevision) throws IOException {
        checkRevision(expectedRevision);
        if (!customId(id) || !entries.containsKey(id)) throw new IllegalArgumentException("protected_catalog");
        var changed=new LinkedHashMap<>(entries); changed.remove(id); persist(changed);
    }
    private void checkRevision(long expected) {
        if (!writable) throw new IllegalArgumentException("storage_unavailable");
        if (expected!=revision) throw new IllegalArgumentException("stale_catalog");
    }
    private void persist(Map<String,WindowLayout> changed) throws IOException {
        var root=new JsonObject(); root.addProperty("version",1); root.addProperty("profile",WindowLayout.PROFILE);
        var array=new JsonArray(); changed.values().forEach(layout->array.add(LayoutJson.write(layout))); root.add("custom",array);
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"windows-",".tmp");
        try {
            Files.writeString(temp,JSON.toJson(root),StandardCharsets.UTF_8);
            // Refuse to degrade to a non-atomic overwrite on unsupported filesystems.
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            entries=Collections.unmodifiableMap(new LinkedHashMap<>(changed)); revision++;
        } finally { Files.deleteIfExists(temp); }
    }
}
