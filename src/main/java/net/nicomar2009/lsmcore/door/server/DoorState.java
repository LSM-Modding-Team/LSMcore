package net.nicomar2009.lsmcore.door.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.nicomar2009.lsmcore.door.storage.DoorStore;

public final class DoorState {
    private static final Map<MinecraftServer,DoorState> SERVERS=new WeakHashMap<>();
    private final Path root;
    private final Map<String,DoorStore> dimensions=new HashMap<>();
    private DoorState(MinecraftServer server) { root=server.getWorldPath(LevelResource.ROOT).resolve("lsmcore/doors"); }
    public static DoorStore of(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Door API requires server thread");
        var state=SERVERS.computeIfAbsent(level.getServer(),DoorState::new);
        String dimension=level.dimension().identifier().toString();
        return state.dimensions.computeIfAbsent(dimension,key->new DoorStore(state.root.resolve(
                Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8))+".json")));
    }
    public static void stopped(MinecraftServer server) { SERVERS.remove(server); }
}
