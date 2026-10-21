package net.nicomar2009.lsmcore.window.server;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class WindowPermissions {
    private WindowPermissions() {}
    /** Operator administrator permission (level 3), including the integrated-world owner when authorized. */
    public static boolean administrator(ServerPlayer player) {
        return Commands.hasPermission(Commands.LEVEL_ADMINS).test(player.createCommandSourceStack());
    }
}
