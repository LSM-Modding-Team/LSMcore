package net.nicomar2009.lsmcore.door.entity;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
import net.nicomar2009.lsmcore.door.geometry.*;
import net.nicomar2009.lsmcore.door.model.*;
import net.nicomar2009.lsmcore.door.server.DoorService;

/** Derived, stationary hit/collision surface. Only UUID/surface/box links are saved or synchronized.
 * Core's record is the sole pose authority. No physics, inventory, independent pose, drops or arc ticker.
 */
public final class DoorBody extends Entity {
    private static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(DoorBody.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SURFACE=SynchedEntityData.defineId(DoorBody.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> BOX=SynchedEntityData.defineId(DoorBody.class,EntityDataSerializers.INT);
    private AABB posed;private boolean pick,solid;
    public DoorBody(EntityType<? extends DoorBody> type,Level level) {super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {builder.define(OWNER,"");builder.define(SURFACE,"");builder.define(BOX,-1);}
    public UUID owner() {try{return UUID.fromString(entityData.get(OWNER));}catch(IllegalArgumentException e){return null;}}
    public String surface() {return entityData.get(SURFACE);}
    public int box() {return entityData.get(BOX);}
    public String key() {return surface()+"/"+box();}
    void link(DoorRecord r,String surface,int box) {entityData.set(OWNER,r.id().toString());entityData.set(SURFACE,surface);entityData.set(BOX,box);refresh(r);}
    public void refresh(DoorRecord r) {
        pick=solid=false;posed=null;
        if(r==null||r.phase()!=DoorRecord.Phase.ACTIVE||!r.id().equals(owner()))return;
        var s=r.structure().definition().surfaces().stream().filter(v->v.id().equals(surface())).findFirst();if(s.isEmpty())return;
        var surface=s.get();if(surface.leaf()==null||box()>=surface.collision().size())return;
        var leaf=r.structure().definition().leaf(surface.leaf());
        var local=box()<0?surface.selection():surface.collision().get(box());
        posed=DoorTransform.box(r.structure().placement(),leaf,r.leaves().get(leaf.id()).open(),local).aabb();
        solid=box()>=0;pick=box()<0||surface.collision().size()==1&&surface.selection().equals(local);
        setPos((posed.minX+posed.maxX)/2,posed.minY,(posed.minZ+posed.maxZ)/2);setBoundingBox(posed);
    }
    public void refresh() {if(level() instanceof ServerLevel server&&!server.getServer().isSameThread())return;UUID id=owner();if(id==null)return;try {refresh(DoorBodies.view(level(),id).map(DoorView::record).orElse(null));}catch(IllegalArgumentException ignored) {pick=solid=false;}}
    @Override public void setPos(double x,double y,double z) {
        if(posed!=null) {x=(posed.minX+posed.maxX)/2;y=posed.minY;z=(posed.minZ+posed.maxZ)/2;}
        super.setPos(x,y,z);
    }
    @Override protected AABB makeBoundingBox(net.minecraft.world.phys.Vec3 position) {return posed==null?super.makeBoundingBox(position):posed;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {super.onSyncedDataUpdated(accessor);if(accessor==OWNER||accessor==SURFACE||accessor==BOX) {DoorBodies.track(this);refresh();}}
    @Override public boolean isPickable() {return pick;}
    @Override public boolean canBeCollidedWith(Entity other) {return solid&&!(other instanceof DoorBody);}
    @Override public boolean shouldRenderAtSqrDistance(double distance) {return distance<192*192;}
    @Override public boolean isPushable() {return false;}
    @Override public void push(double x,double y,double z) {}
    @Override public void push(Entity entity) {}
    @Override public void move(net.minecraft.world.entity.MoverType type,net.minecraft.world.phys.Vec3 movement) {}
    @Override public void tick() {} // Stationary; reconciliation is triggered by record/chunk/entity events only.
    @Override public InteractionResult interact(Player player,InteractionHand hand,net.minecraft.world.phys.Vec3 location) {
        if(player instanceof ServerPlayer server)try {
            var view=DoorService.get(server.level(),owner()).orElseThrow(()->new IllegalArgumentException("unknown_structure"));
            if(!net.nicomar2009.lsmcore.door.Doors.adapter(view.record().structure().definition().type()).orElseThrow().allowsEntityUse(server,hand))return InteractionResult.FAIL;
            var from=server.getEyePosition();var to=from.add(server.getViewVector(1).scale(server.blockInteractionRange()));
            var hit=net.nicomar2009.lsmcore.door.integration.DoorHit.pick(view,from,to).orElseThrow(()->new IllegalArgumentException("no_surface"));
            var clicked=BlockPos.containing(hit.location().add(server.getViewVector(1).scale(1e-6)));
            DoorService.manual(server,owner(),clicked,view.record().revision(),hand);
        }catch(java.io.IOException|IllegalArgumentException e) {failure(player,e);}
        return InteractionResult.SUCCESS;
    }
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount) {
        if(source.getEntity() instanceof ServerPlayer player)try {
            var r=DoorService.get(level,owner()).orElseThrow(()->new IllegalArgumentException("unknown_structure")).record();
            DoorService.breakSurface(player,r.id(),r.revision());
        }catch(java.io.IOException|IllegalArgumentException e) {failure(player,e);}
        return false;
    }
    private static void failure(Player player,Exception error) {
        String code=error instanceof java.io.IOException?"storage_unavailable":error.getMessage();
        if(code==null||!java.util.Set.of("space_occupied","permission","incomplete","unknown_structure","storage_unavailable","structure_broken","stale_structure","structure_pending","occluded","no_surface","ambiguous_surface","fixed_surface","out_of_reach").contains(code))code="unknown_structure";
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("door.lsmcore.error."+code));
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {entityData.set(OWNER,input.getStringOr("owner",""));entityData.set(SURFACE,input.getStringOr("surface",""));entityData.set(BOX,input.getIntOr("box",-1));}
    @Override protected void addAdditionalSaveData(ValueOutput output) {output.putString("owner",entityData.get(OWNER));output.putString("surface",surface());output.putInt("box",box());}
}
