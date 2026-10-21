package net.nicomar2009.lsmcore.door.storage;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.nicomar2009.lsmcore.door.geometry.*;
import net.nicomar2009.lsmcore.door.model.*;

/** Strict, versioned snapshots. Unknown/invalid data fails closed; never becomes an empty door. */
public final class DoorJson {
    public static final int MAX_JSON=65536;
    private DoorJson() {}
    public static JsonObject write(DoorRecord r) {
        var j=new JsonObject(); j.addProperty("version",2); j.addProperty("id",r.id().toString()); j.addProperty("revision",r.revision());
        j.add("definition",definition(r.structure().definition())); var p=r.structure().placement(); var placement=new JsonObject();
        placement.add("origin",pos(p.origin())); placement.addProperty("yaw",p.orientation().quarterTurns()); placement.addProperty("mirror",p.orientation().mirrored());
        j.add("placement",placement); var states=new JsonObject();
        r.leaves().forEach((key,value)-> { var state=new JsonObject(); state.addProperty("open",value.open()); state.addProperty("powered",value.powered()); states.add(key,state); });
        j.add("states",states); j.addProperty("phase",r.phase().name()); j.addProperty("removal",r.removal().name()); j.addProperty("claimed",r.dropsClaimed());
        var retired=new JsonArray();for(var slot:r.retiredProxies())retired.add(pos(slot.position()));j.add("retiredProxies",retired);
        if (j.toString().length()>MAX_JSON) throw new IllegalArgumentException("snapshot_limit");
        return j;
    }
    public static DoorRecord read(String text) {
        if (text.length()>MAX_JSON) throw new IllegalArgumentException("snapshot_limit");
        return read(JsonParser.parseString(text).getAsJsonObject());
    }
    public static DoorRecord read(JsonObject j) {
        int version=integer(j,"version");
        if(version==1)keys(j,"version","id","revision","definition","placement","states","phase","removal","claimed");
        else keys(j,"version","id","revision","definition","placement","states","phase","removal","claimed","retiredProxies");
        if (version!=1&&version!=2) throw new IllegalArgumentException("unknown_door_version");
        var p=j.getAsJsonObject("placement"); keys(p,"origin","yaw","mirror");
        var structure=new DoorStructure(definition(j.getAsJsonObject("definition")),new DoorPlacement(pos(p.getAsJsonArray("origin")),new DoorOrientation(integer(p,"yaw"),bool(p,"mirror"))));
        var states=new LinkedHashMap<String,DoorRecord.LeafState>(); var stateJson=j.getAsJsonObject("states");
        if (stateJson.size()>8) throw new IllegalArgumentException("state_limit");
        stateJson.entrySet().forEach(entry-> { var state=entry.getValue().getAsJsonObject(); keys(state,"open","powered"); states.put(entry.getKey(),new DoorRecord.LeafState(bool(state,"open"),bool(state,"powered"))); });
        var retired=new ArrayList<DoorPlan.Slot>();if(version==2)for(var value:array(j,"retiredProxies",512))retired.add(new DoorPlan.Slot(pos(value.getAsJsonArray()),null));
        return new DoorRecord(UUID.fromString(string(j,"id")),numberLong(j,"revision"),structure,states,
                DoorRecord.Phase.valueOf(string(j,"phase")),DoorRecord.Removal.valueOf(string(j,"removal")),bool(j,"claimed"),retired);
    }
    /** Geometry-free state delta, only for an already observed immutable structure. */
    public static JsonObject writeState(DoorRecord r) {
        var result=new JsonObject(); result.addProperty("version",1); result.addProperty("id",r.id().toString()); result.addProperty("revision",r.revision());
        var states=new JsonObject(); r.leaves().forEach((key,value)-> {
            var state=new JsonObject(); state.addProperty("open",value.open()); state.addProperty("powered",value.powered()); states.add(key,state);
        });
        result.add("states",states); result.addProperty("phase",r.phase().name()); result.addProperty("removal",r.removal().name()); result.addProperty("claimed",r.dropsClaimed());
        return result;
    }
    public static DoorRecord readState(String text,DoorRecord previous) {
        if (text.length()>8192) throw new IllegalArgumentException("state_limit");
        var j=JsonParser.parseString(text).getAsJsonObject(); keys(j,"version","id","revision","states","phase","removal","claimed");
        if (integer(j,"version")!=1 || !UUID.fromString(string(j,"id")).equals(previous.id())) throw new IllegalArgumentException("invalid_state_target");
        var states=new LinkedHashMap<String,DoorRecord.LeafState>(); var values=j.getAsJsonObject("states");
        if (values.size()>8) throw new IllegalArgumentException("state_limit");
        values.entrySet().forEach(entry-> { var state=entry.getValue().getAsJsonObject(); keys(state,"open","powered");
            states.put(entry.getKey(),new DoorRecord.LeafState(bool(state,"open"),bool(state,"powered"))); });
        return new DoorRecord(previous.id(),numberLong(j,"revision"),previous.structure(),states,
                DoorRecord.Phase.valueOf(string(j,"phase")),DoorRecord.Removal.valueOf(string(j,"removal")),bool(j,"claimed"),previous.retiredProxies());
    }
    private static JsonObject definition(DoorDefinition d) {
        var j=new JsonObject(); j.addProperty("type",d.type().toString()); j.add("envelope",box(d.envelope())); j.addProperty("controller",d.controller()); j.addProperty("removalPolicy",d.removalPolicy().name());j.addProperty("motionPolicy",d.motionPolicy().name());j.addProperty("schema",d.schema());
        var cells=new JsonArray(); for (var c:d.cells()) { var v=new JsonObject(); v.addProperty("id",c.id()); v.add("offset",pos(c.offset())); cells.add(v); } j.add("cells",cells);
        var leaves=new JsonArray(); for (var l:d.leaves()) { var v=new JsonObject(); v.addProperty("id",l.id()); v.add("pivot",numbers(l.pivot().x(),l.pivot().y(),l.pivot().z()));
            v.addProperty("turn",l.turn()); v.addProperty("manual",l.manual()); v.addProperty("redstone",l.redstone().name()); leaves.add(v); } j.add("leaves",leaves);
        var surfaces=new JsonArray(); for (var s:d.surfaces()) { var v=new JsonObject(); v.addProperty("id",s.id()); v.addProperty("part",s.part()); v.addProperty("leaf",s.leaf());
            v.add("selection",box(s.selection())); var collision=new JsonArray(); s.collision().forEach(b->collision.add(box(b))); v.add("collision",collision); surfaces.add(v); } j.add("surfaces",surfaces);
        return j;
    }
    private static DoorDefinition definition(JsonObject j) {
        boolean modern=j.has("motionPolicy");
        if(modern)keys(j,"type","envelope","controller","cells","leaves","surfaces","removalPolicy","motionPolicy","schema");
        else keys(j,"type","envelope","controller","cells","leaves","surfaces","removalPolicy");
        var cells=new ArrayList<DoorCell>(); for (var e:array(j,"cells",128)) { var v=e.getAsJsonObject(); keys(v,"id","offset"); cells.add(new DoorCell(string(v,"id"),pos(v.getAsJsonArray("offset")))); }
        var leaves=new ArrayList<DoorLeaf>(); for (var e:array(j,"leaves",8)) { var v=e.getAsJsonObject(); keys(v,"id","pivot","turn","manual","redstone");
            var pivot=vector(v.getAsJsonArray("pivot"),3); leaves.add(new DoorLeaf(string(v,"id"),new DoorPoint(pivot[0],pivot[1],pivot[2]),integer(v,"turn"),bool(v,"manual"),DoorLeaf.Redstone.valueOf(string(v,"redstone")))); }
        var surfaces=new ArrayList<DoorSurface>(); for (var e:array(j,"surfaces",128)) { var v=e.getAsJsonObject(); keys(v,"id","part","leaf","selection","collision");
            var collision=new ArrayList<DoorBox>(); for (var b:array(v,"collision",8)) collision.add(box(b.getAsJsonArray()));
            surfaces.add(new DoorSurface(string(v,"id"),string(v,"part"),v.get("leaf").isJsonNull()?null:string(v,"leaf"),box(v.getAsJsonArray("selection")),collision)); }
        return new DoorDefinition(Identifier.parse(string(j,"type")),box(j.getAsJsonArray("envelope")),string(j,"controller"),cells,leaves,surfaces,DoorRemovalPolicy.valueOf(string(j,"removalPolicy")),modern?DoorDefinition.MotionPolicy.valueOf(string(j,"motionPolicy")):DoorDefinition.MotionPolicy.RESTRICTIVE,modern?integer(j,"schema"):1);
    }
    public static void keys(JsonObject j,String... names) {
        if (!j.keySet().equals(Set.of(names))) throw new IllegalArgumentException("unknown_fields");
    }
    public static long numberLong(JsonObject j,String key) {
        var value=j.getAsJsonPrimitive(key); if (!value.isNumber()) throw new IllegalArgumentException("invalid_integer");
        return value.getAsBigDecimal().longValueExact();
    }
    public static int integer(JsonObject j,String key) { return Math.toIntExact(numberLong(j,key)); }
    private static boolean bool(JsonObject j,String key) { var v=j.getAsJsonPrimitive(key); if (!v.isBoolean()) throw new IllegalArgumentException("invalid_boolean"); return v.getAsBoolean(); }
    private static String string(JsonObject j,String key) { var v=j.getAsJsonPrimitive(key); if (!v.isString() || v.getAsString().length()>256) throw new IllegalArgumentException("invalid_string"); return v.getAsString(); }
    private static JsonArray array(JsonObject j,String key,int limit) { var a=j.getAsJsonArray(key); if (a.size()>limit) throw new IllegalArgumentException("array_limit"); return a; }
    private static JsonArray numbers(double... values) { var a=new JsonArray(); for (double value:values) a.add(value); return a; }
    private static JsonArray pos(BlockPos p) { var a=new JsonArray(); a.add(p.getX()); a.add(p.getY()); a.add(p.getZ()); return a; }
    private static BlockPos pos(JsonArray a) {
        if (a.size()!=3) throw new IllegalArgumentException("invalid_position");
        for (var value:a) if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid_integer");
        return new BlockPos(a.get(0).getAsBigDecimal().intValueExact(),a.get(1).getAsBigDecimal().intValueExact(),a.get(2).getAsBigDecimal().intValueExact());
    }
    private static double[] vector(JsonArray a,int count) {
        if (a.size()!=count) throw new IllegalArgumentException("invalid_vector");
        double[] result=new double[count]; for (int i=0;i<count;i++) { if (!a.get(i).isJsonPrimitive() || !a.get(i).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid_number");
            result[i]=a.get(i).getAsDouble(); if (!Double.isFinite(result[i])) throw new IllegalArgumentException("non_finite_geometry"); } return result;
    }
    private static JsonArray box(DoorBox b) { return numbers(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()); }
    private static DoorBox box(JsonArray a) { var v=vector(a,6); return new DoorBox(v[0],v[1],v[2],v[3],v[4],v[5]); }
}
