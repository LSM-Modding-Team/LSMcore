package net.nicomar2009.lsmcore.window.network;

import com.google.gson.*;
import java.util.*;
import net.nicomar2009.lsmcore.window.catalog.*;
import net.nicomar2009.lsmcore.window.config.WindowConfig;
import net.nicomar2009.lsmcore.window.detection.DetectionResult;
import net.nicomar2009.lsmcore.window.geometry.LayoutValidator;
import net.nicomar2009.lsmcore.window.model.*;
import net.nicomar2009.lsmcore.window.storage.*;

public record WindowSnapshot(long catalogRevision,long policyRevision,long placedRevision,boolean administrator,boolean writable,
                             LayoutLimits limits,List<WindowLayout> custom,EffectiveWindow target) {
    public WindowSnapshot { custom=List.copyOf(custom); }
    public String encode() {
        var json=new JsonObject(); json.addProperty("version",2); json.addProperty("catalogRevision",catalogRevision);
        json.addProperty("policyRevision",policyRevision); json.addProperty("placedRevision",placedRevision);
        json.addProperty("administrator",administrator); json.addProperty("writable",writable); json.add("limits",WindowConfig.encode(limits));
        var array=new JsonArray(); custom.forEach(layout->array.add(LayoutJson.write(layout))); json.add("custom",array);
        var result=new JsonObject(); result.addProperty("mode",target.mode().name()); result.addProperty("status",target.detection().status());
        if (target.detection().assembly()!=null) result.add("rectangle",AssemblyJson.assembly(target.detection().assembly()));
        if (target.explicit()!=null) result.add("explicit",AssemblyJson.write(target.explicit()));
        if (target.layout()!=null) result.add("layout",LayoutJson.write(target.layout()));
        json.add("target",result); return json.toString();
    }
    public static WindowSnapshot decode(String encoded) {
        var json=JsonParser.parseString(encoded).getAsJsonObject();
        if (LayoutJson.integer(json,"version")!=2) throw new IllegalArgumentException("Unsupported snapshot");
        var limits=WindowConfig.decode(json.getAsJsonObject("limits"));
        var array=json.getAsJsonArray("custom"); if (array.size()>128) throw new IllegalArgumentException("Catalog limit");
        var custom=new ArrayList<WindowLayout>(); var ids=new HashSet<String>();
        for (var element:array) {
            var layout=LayoutJson.read(element.getAsJsonObject()); LayoutValidator.intrinsic(layout).requireValid();
            if (!WorldLayoutStore.customId(layout.id()) || !ids.add(layout.id())) throw new IllegalArgumentException("Invalid custom ID"); custom.add(layout);
        }
        var result=json.getAsJsonObject("target");
        String status=result.get("status").getAsString(); if (!status.matches("[a-z_]{1,64}")) throw new IllegalArgumentException("Invalid detection status");
        var rectangle=result.has("rectangle")?AssemblyJson.assembly(result.getAsJsonObject("rectangle")):null;
        var explicit=result.has("explicit")?AssemblyJson.read(result.getAsJsonObject("explicit")):null;
        var layout=result.has("layout")?LayoutJson.read(result.getAsJsonObject("layout")):null;
        if (layout!=null) LayoutValidator.intrinsic(layout).requireValid();
        var effective=new EffectiveWindow(EffectiveWindow.Mode.valueOf(result.get("mode").getAsString()),
                new DetectionResult(status,rectangle,List.of()),explicit,layout);
        return new WindowSnapshot(json.get("catalogRevision").getAsLong(),json.get("policyRevision").getAsLong(),json.get("placedRevision").getAsLong(),
                json.get("administrator").getAsBoolean(),json.get("writable").getAsBoolean(),limits,custom,effective);
    }
}
