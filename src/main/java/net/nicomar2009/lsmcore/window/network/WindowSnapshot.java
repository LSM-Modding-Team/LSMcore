package net.nicomar2009.lsmcore.window.network;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.nicomar2009.lsmcore.window.catalog.LayoutJson;
import net.nicomar2009.lsmcore.window.catalog.WorldLayoutStore;
import net.nicomar2009.lsmcore.window.geometry.LayoutValidator;
import net.nicomar2009.lsmcore.window.model.*;

public record WindowSnapshot(long revision,boolean administrator,boolean writable,boolean targetAvailable,
                             SavedSelection current,Rect available,LayoutLimits limits,List<WindowLayout> custom,String status) {
    public WindowSnapshot { custom=List.copyOf(custom); }
    public String encode() {
        var json=new JsonObject(); json.addProperty("version",1); json.addProperty("revision",revision);
        json.addProperty("administrator",administrator); json.addProperty("writable",writable); json.addProperty("targetAvailable",targetAvailable);
        json.addProperty("status",status);
        if (current!=null) json.add("current",current.toJson());
        if (available!=null) {
            var surface=new JsonObject(); surface.addProperty("x",available.minX()); surface.addProperty("y",available.minY());
            surface.addProperty("width",available.width()); surface.addProperty("height",available.height()); json.add("available",surface);
        }
        var rules=new JsonObject(); rules.addProperty("width",limits.maxWidth()); rules.addProperty("height",limits.maxHeight());
        rules.addProperty("columns",limits.maxColumns()); rules.addProperty("rows",limits.maxRows()); rules.addProperty("leaves",limits.maxLeaves());
        rules.addProperty("cells",limits.maxCells()); rules.addProperty("custom",limits.maxCustomLayouts()); json.add("limits",rules);
        var array=new JsonArray(); custom.forEach(layout->array.add(LayoutJson.write(layout))); json.add("custom",array);
        return json.toString();
    }
    public static WindowSnapshot decode(String encoded) {
        var json=JsonParser.parseString(encoded).getAsJsonObject();
        if (LayoutJson.integer(json,"version")!=1) throw new IllegalArgumentException("Unsupported snapshot");
        var rules=json.getAsJsonObject("limits");
        var limits=new LayoutLimits(LayoutJson.number(rules,"width"),LayoutJson.number(rules,"height"),
                LayoutJson.integer(rules,"columns"),LayoutJson.integer(rules,"rows"),LayoutJson.integer(rules,"leaves"),
                LayoutJson.integer(rules,"cells"),LayoutJson.integer(rules,"custom"));
        var array=json.getAsJsonArray("custom");
        if (array.size()>LayoutLimits.HARD.maxCustomLayouts()) throw new IllegalArgumentException("Catalog limit");
        var custom=new ArrayList<WindowLayout>(); var ids=new HashSet<String>();
        for (var element:array) {
            var layout=LayoutJson.read(element.getAsJsonObject());
            LayoutValidator.validate(layout,LayoutLimits.HARD).requireValid();
            if (!WorldLayoutStore.customId(layout.id()) || !ids.add(layout.id())) throw new IllegalArgumentException("Invalid custom ID");
            custom.add(layout);
        }
        String status=json.get("status").getAsString();
        if (!status.matches("[a-z_]{0,64}")) throw new IllegalArgumentException("Invalid status");
        Rect available=null;
        if (json.has("available")) {
            var surface=json.getAsJsonObject("available");
            double x=LayoutJson.number(surface,"x"),y=LayoutJson.number(surface,"y");
            double w=LayoutJson.number(surface,"width"),h=LayoutJson.number(surface,"height");
            if (x<0 || x>=1 || y<0 || y>=1 || w>64 || h>64) throw new IllegalArgumentException("Invalid opening");
            available=new Rect(x,y,x+w,y+h);
        }
        return new WindowSnapshot(json.get("revision").getAsLong(),json.get("administrator").getAsBoolean(),
                json.get("writable").getAsBoolean(),json.get("targetAvailable").getAsBoolean(),
                json.has("current")?SavedSelection.fromJson(json.getAsJsonObject("current")):null,available,limits,custom,status);
    }
}
