package net.nicomar2009.lsmcore.window.catalog;

import com.google.gson.JsonObject;
import net.nicomar2009.lsmcore.window.model.WindowLayout;

/** Explicit schema, never reflective deserialization of unchecked geometry. */
public final class LayoutJson {
    private LayoutJson() {}
    public static JsonObject write(WindowLayout layout) {
        var json=new JsonObject();
        json.addProperty("id",layout.id()); json.addProperty("width",layout.width());
        json.addProperty("height",layout.height()); json.addProperty("columns",layout.columns()); json.addProperty("rows",layout.rows());
        return json;
    }
    public static WindowLayout read(JsonObject json) {
        return new WindowLayout(json.get("id").getAsString(),number(json,"width"),number(json,"height"),
                integer(json,"columns"),integer(json,"rows"));
    }
    public static double number(JsonObject json,String name) {
        var value=json.getAsJsonPrimitive(name);
        if (!value.isNumber()) throw new IllegalArgumentException("Expected number: "+name);
        double result=value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Nonfinite number: "+name);
        return result;
    }
    public static int integer(JsonObject json,String name) {
        var value=json.getAsJsonPrimitive(name);
        if (!value.isNumber()) throw new IllegalArgumentException("Expected integer: "+name);
        return value.getAsBigDecimal().intValueExact();
    }
}
