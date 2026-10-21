package net.nicomar2009.lsmcore.window.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.nicomar2009.lsmcore.window.catalog.WorldLayoutStore;
import net.nicomar2009.lsmcore.window.config.WindowConfig;
import net.nicomar2009.lsmcore.window.storage.WorldAssemblies;

/** Server-thread state. Shutdown drops caches only: all acknowledged mutations are already durable. */
public final class WindowState {
    private static final Map<MinecraftServer,WindowState> SERVERS=new WeakHashMap<>();
    private final Path root;
    public final WindowConfig policy;
    public final WorldLayoutStore catalog;
    private final Map<String,WorldAssemblies> dimensions=new HashMap<>();
    private WindowState(MinecraftServer server) {
        root=server.getWorldPath(LevelResource.ROOT).resolve("lsmcore");
        policy=new WindowConfig(root.resolve("window-policy.json")); catalog=new WorldLayoutStore(root.resolve("window-layouts.json"));
    }
    public static WindowState of(MinecraftServer server) { return SERVERS.computeIfAbsent(server,WindowState::new); }
    public static void stopped(MinecraftServer server) { SERVERS.remove(server); }
    public WorldAssemblies assemblies(ServerLevel level) {
        String dimension=level.dimension().identifier().toString();
        return dimensions.computeIfAbsent(dimension,key->new WorldAssemblies(root.resolve("assemblies").resolve(
                Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8))+".json")));
    }
}
