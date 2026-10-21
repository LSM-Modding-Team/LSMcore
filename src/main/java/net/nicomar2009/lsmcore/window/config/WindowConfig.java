package net.nicomar2009.lsmcore.window.config;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import net.nicomar2009.lsmcore.window.catalog.LayoutJson;
import net.nicomar2009.lsmcore.window.model.LayoutLimits;
import net.nicomar2009.lsmcore.window.storage.JsonFiles;

/** Single source of policy: <world>/lsmcore/window-policy.json. UI edits THIS data atomically.
 * No parallel TOML source. Missing file uses hard-safe defaults; invalid/unknown files fail closed.
 * Limits govern NEW applications, not existing snapshots or automatic base matching.
 */
public final class WindowConfig {
    private final Path file;
    private LayoutLimits limits=LayoutLimits.HARD;
    private long revision;
    private boolean readable=true;
    public WindowConfig(Path file) {
        this.file=file;
        if (!Files.exists(file)) return;
        try {
            var json=JsonFiles.read(file,8192);
            LayoutJson.requireKeys(json,"version","revision","limits");
            if (LayoutJson.integer(json,"version")!=1) throw new IllegalArgumentException("Unsupported policy");
            limits=decode(json.getAsJsonObject("limits")); revision=LayoutJson.longInteger(json,"revision");
            if (revision<0 || revision==Long.MAX_VALUE) throw new IllegalArgumentException("Invalid revision");
        } catch (IOException | RuntimeException error) {
            readable=false; revision=0; com.mojang.logging.LogUtils.getLogger().error("Preserving invalid window policy {}",file,error);
        }
    }
    public LayoutLimits limits() { requireReadable(); return limits; }
    public long revision() { return revision; }
    public boolean readable() { return readable; }
    public void requireReadable() { if (!readable) throw new IllegalArgumentException("storage_unavailable"); }
    public void update(LayoutLimits next,long expected) throws IOException {
        requireReadable(); if (expected!=revision) throw new IllegalArgumentException("stale_catalog");
        var json=new JsonObject(); json.addProperty("version",1); json.addProperty("revision",revision+1); json.add("limits",encode(next));
        JsonFiles.write(file,json); limits=next; revision++;
    }
    public static JsonObject encode(LayoutLimits limits) {
        var json=new JsonObject(); json.addProperty("width",limits.maxWidth()); json.addProperty("height",limits.maxHeight());
        json.addProperty("columns",limits.maxColumns()); json.addProperty("rows",limits.maxRows()); json.addProperty("leaves",limits.maxLeaves());
        json.addProperty("cells",limits.maxCells()); json.addProperty("custom",limits.maxCustomLayouts()); return json;
    }
    public static LayoutLimits decode(JsonObject json) {
        LayoutJson.requireKeys(json,"width","height","columns","rows","leaves","cells","custom");
        return new LayoutLimits(LayoutJson.number(json,"width"),LayoutJson.number(json,"height"),LayoutJson.integer(json,"columns"),
                LayoutJson.integer(json,"rows"),LayoutJson.integer(json,"leaves"),LayoutJson.integer(json,"cells"),LayoutJson.integer(json,"custom"));
    }
}
