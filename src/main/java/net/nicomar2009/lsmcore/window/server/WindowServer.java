package net.nicomar2009.lsmcore.window.server;

import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicomar2009.lsmcore.window.catalog.*;
import net.nicomar2009.lsmcore.window.config.WindowConfig;
import net.nicomar2009.lsmcore.window.integration.WindowApplication;
import net.nicomar2009.lsmcore.window.model.SavedSelection;
import net.nicomar2009.lsmcore.window.network.*;

/** Lazy world catalog, no tick handler and no world/chunk migration. Main-thread access only. */
public final class WindowServer {
    private static final Map<MinecraftServer,WindowServer> WORLDS=new WeakHashMap<>();
    private final WorldLayoutStore catalog;
    private final Map<UUID,Long> requests=new HashMap<>();
    private WindowServer(MinecraftServer server) {
        catalog=new WorldLayoutStore(server.getWorldPath(LevelResource.ROOT).resolve("lsmcore/window-layouts.json"));
    }
    public static void stopped(MinecraftServer server) { WORLDS.remove(server); }
    public static void logout(ServerPlayer player) {
        var state=WORLDS.get(player.level().getServer());
        if (state!=null) state.requests.remove(player.getUUID());
    }
    public static void handle(ServerPlayer player,WindowRequest request) {
        MinecraftServer server=player.level().getServer();
        if (server==null || !player.level().dimension().identifier().toString().equals(request.dimension())) return;
        var state=WORLDS.computeIfAbsent(server,WindowServer::new);
        long now=System.nanoTime();
        Long last=state.requests.get(player.getUUID());
        if (last!=null && now-last<250_000_000L) return; // Bound disk writes, geometry checks and response traffic.
        state.requests.put(player.getUUID(),now);
        String status="";
        try { state.execute(player,request); status=request.action()==WindowRequest.Action.SYNC?"":"success"; }
        catch (IllegalArgumentException invalid) {
            String key=invalid.getMessage();
            status=key!=null && key.matches("[a-z_]{1,64}")?key:"invalid";
        } catch (IOException failure) {
            status="storage_unavailable";
            com.mojang.logging.LogUtils.getLogger().error("Cannot persist window catalog",failure);
        } catch (RuntimeException failure) {
            status="integration_error";
            com.mojang.logging.LogUtils.getLogger().error("Window integration failed for {}",player.getUUID(),failure);
        }
        state.reply(player,request,status);
    }
    private void execute(ServerPlayer player,WindowRequest request) throws IOException {
        var limits=WindowConfig.limits();
        switch (request.action()) {
            case SYNC -> { }
            case SAVE, DELETE -> {
                if (!WindowPermissions.administrator(player)) throw new IllegalArgumentException("permission");
                if (request.action()==WindowRequest.Action.SAVE) catalog.save(request.proposedLayout(),request.revision(),limits);
                else catalog.delete(request.id(),request.revision());
            }
            case APPLY, RESET -> {
                if (!request.hasTarget()) throw new IllegalArgumentException("no_target");
                if (request.revision()!=catalog.revision()) throw new IllegalArgumentException("stale_catalog");
                var layout=request.action()==WindowRequest.Action.RESET?BaseLayouts.defaultLayout():catalog.resolve(request.id())
                        .orElseThrow(()->new IllegalArgumentException("unknown_layout"));
                WindowApplication.apply(player,request.target(),layout);
            }
        }
    }
    private void reply(ServerPlayer player,WindowRequest request,String status) {
        SavedSelection selection=null; boolean available=false;
        net.nicomar2009.lsmcore.window.model.Rect surface=null;
        if (request.hasTarget()) {
            try {
                var target=WindowApplication.target(player,request.target());
                selection=target.owner().readSelection(player.level(),target.assembly()).orElseGet(()->SavedSelection.of(BaseLayouts.defaultLayout()));
                available=true; surface=target.assembly().available();
            } catch (RuntimeException unavailable) {
                if (status.isEmpty()) status=unavailable instanceof IllegalArgumentException && "no_integration".equals(unavailable.getMessage())
                        ?"no_integration":"target_unavailable";
            }
        }
        if (!catalog.writable() && status.isEmpty()) status="storage_unavailable";
        var snapshot=new WindowSnapshot(catalog.revision(),WindowPermissions.administrator(player),catalog.writable(),available,
                selection,surface,WindowConfig.limits(),List.copyOf(catalog.entries().values()),status);
        PacketDistributor.sendToPlayer(player,new WindowReply(request.session(),player.level().dimension().identifier().toString(),snapshot.encode()));
    }
}
