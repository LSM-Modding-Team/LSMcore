package net.nicomar2009.lsmcore.door.entity;

import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nicomar2009.lsmcore.door.model.*;
import net.nicomar2009.lsmcore.door.server.DoorService;

/** Loaded-entity index, not another saved door registry. No world/entity scan or steady-state work. */
public final class DoorBodies {
    private static final DeferredRegister.Entities TYPES=DeferredRegister.createEntities("lsmcore");
    public static final java.util.function.Supplier<EntityType<DoorBody>> TYPE=TYPES.registerEntityType("door_surface",DoorBody::new,MobCategory.MISC,b->b.sized(.01F,.01F).clientTrackingRange(12).updateInterval(Integer.MAX_VALUE).noSummon());
    private static final Map<Level,Map<UUID,Map<String,DoorBody>>> LOADED=new WeakHashMap<>();
    public static java.util.function.Function<UUID,Optional<DoorView>> clientView=id->Optional.empty();
    public static void register(IEventBus bus) {TYPES.register(bus);}
    public static Optional<DoorView> view(Level level,UUID id) {return level instanceof ServerLevel server?DoorService.get(server,id):clientView.apply(id);}
    public static void track(DoorBody body) {
        if(!body.level().isClientSide()||body.owner()==null)return;
        var map=LOADED.computeIfAbsent(body.level(),k->new HashMap<>()).computeIfAbsent(body.owner(),k->new HashMap<>());
        map.values().removeIf(v->v==body);map.put(body.key(),body);
    }
    public static void joined(EntityJoinLevelEvent event) {
        if(!(event.getEntity() instanceof DoorBody body)||body.owner()==null)return;
        UUID expected=UUID.nameUUIDFromBytes(("lsmcore/door/"+body.owner()+"/"+body.key()).getBytes(StandardCharsets.UTF_8));
        if(!expected.equals(body.getUUID())) {event.setCanceled(true);return;}
        var bodies=LOADED.computeIfAbsent(event.getLevel(),k->new HashMap<>()).computeIfAbsent(body.owner(),k->new HashMap<>());
        var previous=bodies.get(body.key());if(previous!=null&&previous!=body&&!previous.isRemoved()) {event.setCanceled(true);return;}
        bodies.put(body.key(),body);
        try {
            var record=view(event.getLevel(),body.owner());
            if(!event.getLevel().isClientSide()&&(record.isEmpty()||record.get().record().phase()!=DoorRecord.Phase.ACTIVE)) {event.setCanceled(true);bodies.remove(body.key(),body);return;}
            body.refresh();
        }catch(IllegalArgumentException unavailable) { /* Preserve links while Core storage is blocked. */ }
    }
    public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {if(event.getLevel() instanceof Level level)LOADED.remove(level);}
    public static void left(EntityLeaveLevelEvent event) {
        if(!(event.getEntity() instanceof DoorBody body))return;var level=LOADED.get(event.getLevel());if(level==null)return;
        var bodies=level.get(body.owner());if(bodies!=null) {bodies.remove(body.key(),body);if(bodies.isEmpty())level.remove(body.owner());}
    }
    public static void clientChanged(Set<UUID> owners) {
        for(var entry:LOADED.entrySet())if(entry.getKey().isClientSide())for(var id:owners) {
            var bodies=entry.getValue().get(id);if(bodies!=null)for(var body:bodies.values())body.refresh();
        }
    }
    public static void reconcile(ServerLevel level,DoorRecord r) {
        if(r.structure().definition().motionPolicy()!=DoorDefinition.MotionPolicy.VANILLA)return;
        var bodies=LOADED.computeIfAbsent(level,k->new HashMap<>()).computeIfAbsent(r.id(),k->new HashMap<>());
        var wanted=new HashSet<String>();
        if(r.phase()==DoorRecord.Phase.ACTIVE)for(var surface:r.structure().definition().surfaces())if(surface.leaf()!=null) {
            boolean combined=surface.collision().size()==1&&surface.selection().equals(surface.collision().getFirst());
            for(int i=combined?0:-1;i<surface.collision().size();i++) {
                String key=surface.id()+"/"+i;wanted.add(key);var body=bodies.get(key);
                if(body!=null&&!body.isRemoved()) {body.refresh(r);continue;}
                var leaf=r.structure().definition().leaf(surface.leaf());var b=net.nicomar2009.lsmcore.door.geometry.DoorTransform.box(r.structure().placement(),leaf,r.leaves().get(leaf.id()).open(),i<0?surface.selection():surface.collision().get(i));
                var pos=BlockPos.containing((b.minX()+b.maxX())/2,b.minY(),(b.minZ()+b.maxZ())/2);if(!level.hasChunkAt(pos))continue;
                UUID uuid=UUID.nameUUIDFromBytes(("lsmcore/door/"+r.id()+"/"+key).getBytes(StandardCharsets.UTF_8));
                var existing=level.getEntity(uuid);if(existing instanceof DoorBody value) {bodies.put(key,value);value.refresh(r);continue;}if(existing!=null)continue;
                body=new DoorBody(net.nicomar2009.lsmcore.door.Doors.adapter(r.structure().definition().type()).orElseThrow().bodyType(),level);body.setUUID(uuid);body.link(r,surface.id(),i);level.addFreshEntity(body);
            }
        }
        for(var entry:new ArrayList<>(bodies.entrySet()))if(!wanted.contains(entry.getKey()))entry.getValue().discard();
    }
    public static void changed(ServerLevel level,DoorRecord before,DoorRecord after) {
        if(after!=null) {
            if(before!=null&&before.structure()==after.structure()&&before.phase()==after.phase()&&after.leaves().entrySet().stream().allMatch(e->e.getValue().open()==before.leaves().get(e.getKey()).open()))return;
            reconcile(level,after);
        }
        else {var entries=LOADED.get(level);if(entries!=null) {var bodies=entries.remove(before.id());if(bodies!=null)for(var body:new ArrayList<>(bodies.values()))body.discard();}}
    }
}
