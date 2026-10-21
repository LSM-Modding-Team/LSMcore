package net.nicomar2009.lsmcore.door.server;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.nicomar2009.lsmcore.door.Doors;
import net.nicomar2009.lsmcore.door.geometry.DoorMotion;
import net.nicomar2009.lsmcore.door.integration.*;
import net.nicomar2009.lsmcore.door.model.*;

/** Server-thread entry points. Call only for real owner placement/use/break/neighbor changes.
 * Expected revisions prevent duplicate/stale actions. No blocks, items or generic polling are registered.
 */
public final class DoorService {
    private static final Map<ServerLevel,Set<UUID>> BUSY=new IdentityHashMap<>();
    private DoorService() {}
    public static Optional<DoorView> at(ServerLevel level,BlockPos pos) { return DoorState.of(level).at(pos); }
    public static Optional<DoorView> get(ServerLevel level,UUID id) {return DoorState.of(level).get(id);}
    private static DoorAdapter adapter(DoorRecord r) {
        return Doors.adapter(r.structure().definition().type()).orElseThrow(()->new IllegalArgumentException("adapter_unavailable"));
    }
    public static DoorResult place(ServerPlayer player,DoorDefinition definition,DoorPlacement placement,BlockPlaceContext context) throws IOException {
        var level=player.level(); var store=DoorState.of(level);
        if (context.getLevel()!=level || context.getPlayer()!=player) throw new IllegalArgumentException("invalid_context");
        var r=DoorRecord.placing(definition,placement); var owner=adapter(r);
        if (!player.isWithinBlockInteractionRange(context.getClickedPos(),0)) throw new IllegalArgumentException("out_of_reach");
        for (var slot:r.structure().plan().slots().values()) {
            bounds(level,slot.position()); loaded(level,slot.position());
            if (store.at(slot.position()).isPresent() || !level.getBlockState(slot.position()).canBeReplaced(context)
                    || !owner.canPlace(player,r,slot)) throw new IllegalArgumentException("space_occupied");
        }
        authorize(level,player,DoorAction.PLACE,r,owner,true,context.getHand());
        enter(level,r.id());
        try {
            commit(level,null,r); // Intent and all reserved memberships exist BEFORE any owner block mutation.
            try {
                for (var slot:r.structure().plan().slots().values()) {
                    loaded(level,slot.position()); owner.place(level,r,slot);
                    if (!owner.matches(level,r,slot)) throw new IllegalStateException("Owner did not stamp door cell");
                }
                var next=r.next(r.leaves(),DoorRecord.Phase.ACTIVE,DoorRecord.Removal.NONE,false);
                commit(level,r,next); return result("ok",next);
            } catch (IOException | RuntimeException failure) {
                // A durable placement journal is recoverable even if cleanup itself fails or chunks unload.
                try { beginRemoval(level,r,DoorRecord.Removal.FAILED_PLACEMENT); cleanup(level,store.get(r.id()).orElseThrow().record()); }
                catch (IOException | RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        } finally { leave(level,r.id()); }
    }
    /** Ray-test current server pose; the client never chooses a leaf or supplies an open state. */
    public static DoorResult manual(ServerPlayer player,UUID id,BlockPos clicked,long expected) throws IOException {
        return manual(player,id,clicked,expected,InteractionHand.MAIN_HAND);
    }
    public static DoorResult manual(ServerPlayer player,UUID id,BlockPos clicked,long expected,InteractionHand hand) throws IOException {
        var level=player.level(); var view=DoorState.of(level).get(id).orElseThrow(()->new IllegalArgumentException("unknown_structure"));
        var r=view.record(); active(r,expected); loaded(level,clicked);
        if (!view.geometry().cells().containsKey(clicked) || !player.isWithinBlockInteractionRange(clicked,0)) throw new IllegalArgumentException("out_of_reach");
        double reach=Math.min(32,player.blockInteractionRange());
        Vec3 from=player.getEyePosition(),to=from.add(player.getViewVector(1).scale(reach));
        rayLoaded(level,from,to);
        var hit=DoorHit.pick(view,from,to).orElseThrow(()->new IllegalArgumentException("no_surface"));
        if (hit.ambiguous()) throw new IllegalArgumentException("ambiguous_surface");
        var obstacle=level.clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if (obstacle.getType()!=HitResult.Type.MISS && from.distanceToSqr(obstacle.getLocation())+1e-8<hit.distanceSquared())
            throw new IllegalArgumentException("occluded");
        if (hit.leaf()==null) throw new IllegalArgumentException("fixed_surface");
        var leaf=r.structure().definition().leaf(hit.leaf());
        if (!leaf.manual()) throw new IllegalArgumentException("manual_disabled");
        var owner=adapter(r); verify(level,r,owner); authorize(level,player,DoorAction.MANUAL,r,owner,true,java.util.Objects.requireNonNull(hand));
        var previous=r.leaves().get(leaf.id());
        return changeLeaf(level,r,owner,leaf.id(),new DoorRecord.LeafState(!previous.open(),previous.powered()));
    }
    /** Trusted neighbor callback, not a network endpoint. Call only when this leaf's input changes.
     * Other leaves are not changed. FOLLOW_POWER follows edges, TOGGLE_ON_RISING only rising edges.
     */
    public static DoorResult power(ServerLevel level,UUID id,String leafId,boolean powered) throws IOException {
        var r=DoorState.of(level).get(id).orElseThrow(()->new IllegalArgumentException("unknown_structure")).record();
        active(r,r.revision()); var leaf=r.structure().definition().leaf(leafId); var old=r.leaves().get(leafId);
        if (leaf.redstone()==DoorLeaf.Redstone.IGNORE || old.powered()==powered) return result("unchanged",r);
        var owner=adapter(r); verify(level,r,owner); authorize(level,null,DoorAction.REDSTONE,r,owner,true);
        boolean open=leaf.redstone()==DoorLeaf.Redstone.FOLLOW_POWER?powered:powered?!old.open():old.open();
        return changeLeaf(level,r,owner,leafId,new DoorRecord.LeafState(open,powered));
    }
    private static DoorResult changeLeaf(ServerLevel level,DoorRecord r,DoorAdapter owner,String leaf,DoorRecord.LeafState state) throws IOException {
        enter(level,r.id());
        try {
            if (state.open()!=r.leaves().get(leaf).open() && r.structure().definition().motionPolicy()==DoorDefinition.MotionPolicy.RESTRICTIVE && !owner.canMove(level,r,DoorMotion.of(r,leaf,state.open()))) throw new IllegalArgumentException("motion_obstructed");
            var states=new LinkedHashMap<>(r.leaves()); states.put(leaf,state);
            var next=r.next(states,DoorRecord.Phase.ACTIVE,DoorRecord.Removal.NONE,false); commit(level,r,next); return result("ok",next);
        } finally { leave(level,r.id()); }
    }
    /** Call BEFORE vanilla breaks a registered piece; cancel vanilla removal/drops when accepted.
     * The owner must also honor reentrant/in-progress callbacks and not emit per-cell drops.
     */
    public static DoorResult breakPiece(ServerPlayer player,BlockPos clicked,long expected) throws IOException {
        var level=player.level(); loaded(level,clicked);
        var r=DoorState.of(level).at(clicked).orElseThrow(()->new IllegalArgumentException("unknown_structure")).record();
        if (r.revision()!=expected) throw new IllegalArgumentException("stale_structure");
        if (!player.isWithinBlockInteractionRange(clicked,0)) throw new IllegalArgumentException("out_of_reach");
        var owner=adapter(r);
        if (!owner.matches(level,r,r.structure().plan().slots().get(clicked))) throw new IllegalArgumentException("structure_broken");
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(Math.min(32,player.blockInteractionRange())));
        rayLoaded(level,eye,end);
        var target=level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if (target.getType()!=HitResult.Type.BLOCK || !target.getBlockPos().equals(clicked)) throw new IllegalArgumentException("occluded");
        authorize(level,player,DoorAction.REMOVE,r,owner,false);
        enter(level,r.id());
        try {
            if (r.phase()!=DoorRecord.Phase.REMOVING) r=beginRemoval(level,r,player.isCreative()?DoorRecord.Removal.PLAYER_CREATIVE:DoorRecord.Removal.PLAYER);
            return cleanup(level,r);
        } finally { leave(level,r.id()); }
    }
    /** Entity interaction: server traces the real current surface, foreign blocks still occlude it. */
    public static DoorResult breakSurface(ServerPlayer player,UUID id,long expected) throws IOException {
        var level=player.level();var view=DoorState.of(level).get(id).orElseThrow(()->new IllegalArgumentException("unknown_structure"));
        var r=view.record();active(r,expected);var owner=adapter(r);verify(level,r,owner);
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(Math.min(32,player.blockInteractionRange())));
        rayLoaded(level,eye,end);var hit=DoorHit.pick(view,eye,end).orElseThrow(()->new IllegalArgumentException("no_surface"));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.ambiguous()||block.getType()!=HitResult.Type.MISS&&eye.distanceToSqr(block.getLocation())+1e-8<hit.distanceSquared())throw new IllegalArgumentException("occluded");
        authorize(level,player,DoorAction.REMOVE,r,owner,false);enter(level,id);
        try {return cleanup(level,beginRemoval(level,r,player.isCreative()?DoorRecord.Removal.PLAYER_CREATIVE:DoorRecord.Removal.PLAYER));}
        finally {leave(level,id);}
    }
    /** Owner onRemove hook ONLY when the block identity/UUID link was actually removed or replaced.
     * Never call for a leaf state update, neighbor notification or chunk unload. Unknown IDs are safe.
     */
    public static void pieceRemoved(ServerLevel level,BlockPos position) throws IOException {
        var view=DoorState.of(level).at(position); if (view.isEmpty() || busy(level,view.get().record().id())) return;
        var r=view.get().record(); loaded(level,position);
        var owner=adapter(r); var slot=r.structure().plan().slots().get(position);
        if (owner.matches(level,r,slot)) return; // Ordinary state change, not removal.
        recover(level,r.id());
    }
    /** Explicit recovery and chunk-load work share this path. Missing chunks are NEVER loaded.
     * PLACING after restart rolls back without drops. Missing adapters/files preserve all data.
     */
    public static DoorResult recover(ServerLevel level,UUID id) throws IOException {
        var view=DoorState.of(level).get(id); if (view.isEmpty()) return new DoorResult("unchanged",id,0);
        var r=view.get().record(); var owner=adapter(r); enter(level,id);
        try {
            if (r.phase()==DoorRecord.Phase.ACTIVE) {
                var replacement=owner.upgrade(r);
                if(replacement.isPresent()) {
                    var definition=replacement.get();
                    if(!definition.type().equals(r.structure().definition().type())||!r.leaves().keySet().equals(definition.leaves().stream().map(DoorLeaf::id).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("incompatible_upgrade");
                    authorize(level,null,DoorAction.RECOVER,r,owner,false);
                    var retired=new ArrayList<>(r.retiredProxies());r.structure().plan().slots().values().stream().filter(s->s.proxy()).forEach(retired::add);
                    var next=new DoorRecord(r.id(),r.revision()+1,new DoorStructure(definition,r.structure().placement()),r.leaves(),r.phase(),r.removal(),r.dropsClaimed(),retired);
                    DoorState.of(level).replace(id,next,r.revision(),true);DoorSync.changed(level,r,next);owner.changed(level,r,next);r=next;
                }
                r=retireProxies(level,r,owner);
                net.nicomar2009.lsmcore.door.entity.DoorBodies.reconcile(level,r);
                boolean broken=false;
                for (var slot:r.structure().plan().slots().values()) if (level.hasChunkAt(slot.position()) && !owner.matches(level,r,slot)) { broken=true; break; }
                if (!broken) return result("unchanged",r);
            }
            authorize(level,null,DoorAction.RECOVER,r,owner,false);
            if (r.phase()!=DoorRecord.Phase.REMOVING) r=beginRemoval(level,r,r.phase()==DoorRecord.Phase.PLACING
                    ?DoorRecord.Removal.FAILED_PLACEMENT:DoorRecord.Removal.INTEGRITY_LOSS);
            return cleanup(level,r);
        } finally { leave(level,id); }
    }
    private static DoorRecord retireProxies(ServerLevel level,DoorRecord r,DoorAdapter owner) throws IOException {
        if(r.retiredProxies().isEmpty())return r;authorize(level,null,DoorAction.RECOVER,r,owner,false);var pending=new ArrayList<net.nicomar2009.lsmcore.door.geometry.DoorPlan.Slot>();
        for(var slot:r.retiredProxies()) {
            if(!level.hasChunkAt(slot.position())) {pending.add(slot);continue;}
            if(owner.matches(level,r,slot)) {owner.removeWithoutDrops(level,r,slot);if(owner.matches(level,r,slot))pending.add(slot);}
        }
        if(pending.size()==r.retiredProxies().size())return r;
        var next=new DoorRecord(r.id(),r.revision()+1,r.structure(),r.leaves(),r.phase(),r.removal(),r.dropsClaimed(),pending);commit(level,r,next);return next;
    }
    private static DoorRecord beginRemoval(ServerLevel level,DoorRecord r,DoorRecord.Removal reason) throws IOException {
        var next=r.next(r.leaves(),DoorRecord.Phase.REMOVING,reason,false); commit(level,r,next); return next;
    }
    private static DoorResult cleanup(ServerLevel level,DoorRecord r) throws IOException {
        var owner=adapter(r);r=retireProxies(level,r,owner);boolean incomplete=!r.retiredProxies().isEmpty();
        for (var slot:r.structure().plan().slots().values()) {
            if (!level.hasChunkAt(slot.position())) { incomplete=true; continue; }
            if (!owner.matches(level,r,slot)) continue; // Never delete unrelated replacement blocks.
            owner.removeWithoutDrops(level,r,slot);
            if (owner.matches(level,r,slot)) throw new IllegalStateException("Owner did not remove door cell");
        }
        if (incomplete) return result("pending_chunks",r);
        if (!r.dropsClaimed()) {
            var claimed=r.next(r.leaves(),DoorRecord.Phase.REMOVING,r.removal(),true); commit(level,r,claimed); r=claimed;
            if (r.structure().definition().removalPolicy().drops(r.removal())) {
                try { owner.drops(level,r); } catch (RuntimeException error) { log("Door drops callback failed after durable claim",error); }
            }
        }
        commit(level,r,null); return new DoorResult("ok",r.id(),r.revision()+1);
    }
    private static void verify(ServerLevel level,DoorRecord r,DoorAdapter owner) {
        for (var slot:r.structure().plan().slots().values()) {
            loaded(level,slot.position()); bounds(level,slot.position());
            if (!owner.matches(level,r,slot)) throw new IllegalArgumentException("structure_broken");
        }
    }
    private static void authorize(ServerLevel level,ServerPlayer player,DoorAction action,DoorRecord r,DoorAdapter owner,boolean requireLoaded) {
        authorize(level,player,action,r,owner,requireLoaded,InteractionHand.MAIN_HAND);
    }
    private static void authorize(ServerLevel level,ServerPlayer player,DoorAction action,DoorRecord r,DoorAdapter owner,boolean requireLoaded,InteractionHand hand) {
        if (player!=null && (player.isSpectator() || !player.mayBuild())) throw new IllegalArgumentException("permission");
        for (BlockPos pos:r.structure().plan().slots().keySet()) {
            if (action==DoorAction.PLACE || action==DoorAction.MANUAL || action==DoorAction.REDSTONE) bounds(level,pos);
            if (requireLoaded) loaded(level,pos);
            if (player!=null && level.hasChunkAt(pos) && (!level.mayInteract(player,pos)
                    || !player.mayUseItemAt(pos,Direction.UP,player.getItemInHand(hand)))) throw new IllegalArgumentException("permission");
        }
        var affected=new HashSet<>(r.structure().plan().slots().keySet());r.retiredProxies().forEach(s->affected.add(s.position()));
        if (!owner.authorize(level,player,action,r,affected)
                || NeoForge.EVENT_BUS.post(new DoorPermissionEvent(level,player,action,r,affected)).isCanceled()) throw new IllegalArgumentException("permission");
    }
    private static void commit(ServerLevel level,DoorRecord old,DoorRecord next) throws IOException {
        UUID id=next==null?old.id():next.id(); DoorState.of(level).replace(id,next,old==null?0:old.revision());
        try {net.nicomar2009.lsmcore.door.entity.DoorBodies.changed(level,old,next);}
        catch(RuntimeException error) {log("Door body reconciliation failed after durable commit; retry through recovery",error);}
        try { DoorSync.changed(level,old,next); }
        catch (RuntimeException error) { log("Door synchronization failed after commit; state remains authoritative",error); }
        Doors.adapter((next==null?old:next).structure().definition().type()).ifPresent(owner-> {
            try { owner.changed(level,old,next); } catch (RuntimeException error) { log("Door changed callback failed after commit",error); }
        });
    }
    private static void active(DoorRecord r,long expected) {
        if (r.revision()!=expected) throw new IllegalArgumentException("stale_structure");
        if (r.phase()!=DoorRecord.Phase.ACTIVE) throw new IllegalArgumentException("structure_pending");
    }
    private static void bounds(ServerLevel level,BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) throw new IllegalArgumentException("world_limit");
    }
    private static void loaded(ServerLevel level,BlockPos pos) { if (!level.hasChunkAt(pos)) throw new IllegalArgumentException("incomplete"); }
    private static void rayLoaded(ServerLevel level,Vec3 from,Vec3 to) {
        int x0=((int)Math.floor(Math.min(from.x,to.x)))>>4,x1=((int)Math.floor(Math.max(from.x,to.x)))>>4;
        int z0=((int)Math.floor(Math.min(from.z,to.z)))>>4,z1=((int)Math.floor(Math.max(from.z,to.z)))>>4;
        for (int x=x0;x<=x1;x++) for (int z=z0;z<=z1;z++) loaded(level,new BlockPos(x<<4,(int)Math.floor(from.y),z<<4));
    }
    private static boolean busy(ServerLevel level,UUID id) { return BUSY.getOrDefault(level,Set.of()).contains(id); }
    private static void enter(ServerLevel level,UUID id) {
        if (!BUSY.computeIfAbsent(level,key->new HashSet<>()).add(id)) throw new IllegalArgumentException("structure_busy");
    }
    private static void leave(ServerLevel level,UUID id) { var ids=BUSY.get(level); ids.remove(id); if (ids.isEmpty()) BUSY.remove(level); }
    private static DoorResult result(String code,DoorRecord r) { return new DoorResult(code,r.id(),r.revision()); }
    private static void log(String message,Throwable error) { com.mojang.logging.LogUtils.getLogger().error(message,error); }
}
