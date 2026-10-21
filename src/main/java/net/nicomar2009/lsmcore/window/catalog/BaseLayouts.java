package net.nicomar2009.lsmcore.window.catalog;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.nicomar2009.lsmcore.window.geometry.LayoutValidator;
import net.nicomar2009.lsmcore.window.model.*;

/** Private classpath resource, outside assets/data and all reload/resource-pack APIs. */
public final class BaseLayouts {
    public static final String DEFAULT_ID="lsmcore:unit_1x1";
    private static final Map<String,WindowLayout> ENTRIES=load();
    private BaseLayouts() {}
    public static Map<String,WindowLayout> entries() { return ENTRIES; }
    public static WindowLayout defaultLayout() { return ENTRIES.get(DEFAULT_ID); }
    public static boolean protectedId(String id) { return id.startsWith("lsmcore:"); }
    private static Map<String,WindowLayout> load() {
        try (var stream=BaseLayouts.class.getResourceAsStream("/net/nicomar2009/lsmcore/window/base_layouts.json")) {
            if (stream==null) throw new IllegalStateException("Missing internal window catalog");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if (LayoutJson.integer(json,"version")!=1 || !WindowLayout.PROFILE.equals(json.get("profile").getAsString()))
                throw new IllegalStateException("Unsupported internal catalog");
            var entries=new LinkedHashMap<String,WindowLayout>();
            for (var element:json.getAsJsonArray("layouts")) {
                var layout=LayoutJson.read(element.getAsJsonObject());
                LayoutValidator.validate(layout,LayoutLimits.HARD).requireValid();
                if (!protectedId(layout.id()) || entries.putIfAbsent(layout.id(),layout)!=null)
                    throw new IllegalStateException("Invalid internal catalog ID");
            }
            if (entries.size()!=10 || !entries.containsKey(DEFAULT_ID)) throw new IllegalStateException("Incomplete base catalog");
            return java.util.Collections.unmodifiableMap(entries);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load internal catalog",exception); }
    }
}
