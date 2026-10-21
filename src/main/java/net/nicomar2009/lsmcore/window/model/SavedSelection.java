package net.nicomar2009.lsmcore.window.model;

import com.google.gson.JsonObject;
import net.nicomar2009.lsmcore.window.catalog.LayoutJson;
import net.nicomar2009.lsmcore.window.geometry.LayoutValidator;

/** Owner persists the full snapshot with its assembly, not just a catalog ID.
 * Editing/deleting a catalog entry never changes existing snapshots. No opening state here.
 */
public record SavedSelection(int version, String profile, WindowLayout layout) {
    public SavedSelection {
        if (version != WindowLayout.FORMAT_VERSION || !WindowLayout.PROFILE.equals(profile))
            throw new IllegalArgumentException("Unsupported selection format");
        LayoutValidator.validate(layout,LayoutLimits.HARD).requireValid();
    }
    public static SavedSelection of(WindowLayout layout) {
        return new SavedSelection(WindowLayout.FORMAT_VERSION,WindowLayout.PROFILE,layout);
    }
    public JsonObject toJson() {
        var json=new JsonObject(); json.addProperty("version",version); json.addProperty("profile",profile);
        json.add("layout",LayoutJson.write(layout)); return json;
    }
    /** Invalid/unknown saved formats throw; owner must preserve the original data, never silently reset it. */
    public static SavedSelection fromJson(JsonObject json) {
        return new SavedSelection(LayoutJson.integer(json,"version"),json.get("profile").getAsString(),
                LayoutJson.read(json.getAsJsonObject("layout")));
    }
}
