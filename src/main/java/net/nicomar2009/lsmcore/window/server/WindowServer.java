package net.nicomar2009.lsmcore.window.server;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicomar2009.lsmcore.window.catalog.LayoutJson;
import net.nicomar2009.lsmcore.window.config.WindowConfig;
import net.nicomar2009.lsmcore.window.detection.DetectionResult;
import net.nicomar2009.lsmcore.window.integration.WindowApplication;
import net.nicomar2009.lsmcore.window.model.*;
import net.nicomar2009.lsmcore.window.network.*;

/** Main-thread requests. Rate-limited requests ALWAYS receive their correlated rejection. */
public final class WindowServer {
    private static final Map<ServerPlayer,Long> LAST=new IdentityHashMap<>();
    private WindowServer() {}
    public static void stopped(MinecraftServer server) {
        LAST.keySet().removeIf(player->player.level().getServer()==server); WindowSync.stopped(server); WindowState.stopped(server);
    }
    public static void logout(ServerPlayer player) { LAST.remove(player); WindowSync.logout(player); }
    public static void handle(ServerPlayer player,WindowRequest request) {
        if (!player.level().dimension().identifier().toString().equals(request.dimension())) { reply(player,request,"stale_target",""); return; }
        long now=System.nanoTime(); Long previous=LAST.get(player);
        if (previous!=null && now-previous<250_000_000L) { reply(player,request,"rate_limited",""); return; }
        LAST.put(player,now); String status="ok";
        try { execute(player,request); }
        catch (IllegalArgumentException invalid) { status=code(invalid.getMessage()); }
        catch (IOException error) { status="storage_unavailable"; com.mojang.logging.LogUtils.getLogger().error("Window persistence failed",error); }
        catch (RuntimeException error) { status="integration_error"; com.mojang.logging.LogUtils.getLogger().error("Window integration failed",error); }
        try { reply(player,request,status,snapshot(player,request).encode()); }
        catch (RuntimeException error) { reply(player,request,"storage_unavailable",""); }
    }
    private static String code(String message) { return message!=null && message.matches("[a-z_]{1,64}")?message:"invalid"; }
    private static JsonObject data(String body) {
        try { return JsonParser.parseString(body).getAsJsonObject(); }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("invalid"); }
    }
    private static void execute(ServerPlayer player,WindowRequest request) throws IOException {
        var state=WindowState.of(player.level().getServer());
        if (request.action()==WindowRequest.Action.QUERY) return;
        state.policy.requireReadable();
        if (request.action()==WindowRequest.Action.POLICY) {
            if (!WindowPermissions.administrator(player)) throw new IllegalArgumentException("permission");
            state.policy.update(WindowConfig.decode(data(request.json())),request.policyRevision()); return;
        }
        if (request.action()==WindowRequest.Action.RESET) {
            if (!request.hasTarget()) throw new IllegalArgumentException("no_target");
            WindowApplication.reset(player,request.clicked(),request.placedRevision()); return;
        }
        var data=data(request.json());
        if (request.action()==WindowRequest.Action.SAVE || request.action()==WindowRequest.Action.DELETE) {
            if (!WindowPermissions.administrator(player)) throw new IllegalArgumentException("permission");
            if (request.action()==WindowRequest.Action.SAVE) state.catalog.save(LayoutJson.read(data),request.catalogRevision(),state.policy.limits());
            else state.catalog.delete(data.get("id").getAsString(),request.catalogRevision()); return;
        }
        if (!request.hasTarget()) throw new IllegalArgumentException("no_target");
        if (request.policyRevision()!=state.policy.revision() || request.catalogRevision()!=state.catalog.revision()) throw new IllegalArgumentException("stale_catalog");
        var layout=state.catalog.resolve(data.get("id").getAsString()).orElseThrow(()->new IllegalArgumentException("unknown_layout"));
        WindowApplication.apply(player,request.clicked(),request.manual(),request.first(),request.last(),layout,request.placedRevision());
    }
    private static WindowSnapshot snapshot(ServerPlayer player,WindowRequest request) {
        var state=WindowState.of(player.level().getServer()); var store=state.assemblies(player.level());
        EffectiveWindow result=new EffectiveWindow(EffectiveWindow.Mode.UNAVAILABLE,DetectionResult.error("no_target"),null,null);
        if (request.hasTarget()) {
            try { result=WindowApplication.inspect(player,request.clicked(),request.manual(),request.first(),request.last()); }
            catch (RuntimeException unavailable) { result=new EffectiveWindow(EffectiveWindow.Mode.UNAVAILABLE,DetectionResult.error(code(unavailable.getMessage())),null,null); }
        }
        return new WindowSnapshot(state.catalog.revision(),state.policy.revision(),store.revision(),WindowPermissions.administrator(player),
                state.policy.readable() && state.catalog.writable() && store.readable(),state.policy.readable()?state.policy.limits():LayoutLimits.HARD,
                List.copyOf(state.catalog.entries().values()),result);
    }
    private static void reply(ServerPlayer player,WindowRequest request,String status,String json) {
        PacketDistributor.sendToPlayer(player,new WindowReply(request.session(),request.serial(),request.dimension(),status,json));
    }
}
