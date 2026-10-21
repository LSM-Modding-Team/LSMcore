package net.nicomar2009.lsmcore.window.storage;

import com.google.gson.JsonObject;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.window.catalog.LayoutJson;
import net.nicomar2009.lsmcore.window.integration.*;
import net.nicomar2009.lsmcore.window.model.*;

public final class AssemblyJson {
    private AssemblyJson() {}
    public static JsonObject assembly(WindowAssembly value) {
        var json=new JsonObject(); BlockPos pos=value.anchor();
        json.addProperty("x",pos.getX()); json.addProperty("y",pos.getY()); json.addProperty("z",pos.getZ());
        var cell=value.identity(); json.addProperty("family",cell.family().toString()); json.addProperty("facing",cell.facing().getName());
        json.addProperty("plane",cell.plane());
        var bounds=value.available(); json.addProperty("minX",bounds.minX()); json.addProperty("minY",bounds.minY());
        json.addProperty("maxX",bounds.maxX()); json.addProperty("maxY",bounds.maxY()); return json;
    }
    public static WindowAssembly assembly(JsonObject json) {
        LayoutJson.requireKeys(json,"x","y","z","family","facing","plane","minX","minY","maxX","maxY");
        Direction facing=Direction.byName(json.get("facing").getAsString());
        Rect bounds=new Rect(LayoutJson.number(json,"minX"),LayoutJson.number(json,"minY"),LayoutJson.number(json,"maxX"),LayoutJson.number(json,"maxY"));
        var identity=new WindowCell(Identifier.parse(json.get("family").getAsString()),facing,LayoutJson.number(json,"plane"),new Rect(0,0,1,1),false);
        return new WindowAssembly(new BlockPos(LayoutJson.integer(json,"x"),LayoutJson.integer(json,"y"),LayoutJson.integer(json,"z")),identity,bounds);
    }
    public static JsonObject write(PlacedAssembly record) {
        var json=new JsonObject(); json.addProperty("version",1); json.addProperty("id",record.id().toString());
        json.add("rectangle",assembly(record.assembly())); json.add("selection",record.selection().toJson()); json.addProperty("health",record.health()); return json;
    }
    public static PlacedAssembly read(JsonObject json) {
        LayoutJson.requireKeys(json,"version","id","rectangle","selection","health");
        if (LayoutJson.integer(json,"version")!=1) throw new IllegalArgumentException("Unknown assembly format");
        return new PlacedAssembly(UUID.fromString(json.get("id").getAsString()),assembly(json.getAsJsonObject("rectangle")),
                SavedSelection.fromJson(json.getAsJsonObject("selection")),json.get("health").getAsString());
    }
}
