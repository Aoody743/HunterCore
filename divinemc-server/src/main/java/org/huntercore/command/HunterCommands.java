package org.huntercore.command;

import net.minecraft.server.MinecraftServer;
import org.bukkit.command.Command;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.huntercore.bootstrap.HunterCoreBootstrap;
import org.huntercore.bootstrap.HunterCoreRuntime;

@DefaultQualifier(NonNull.class)
public final class HunterCommands {
    private HunterCommands() {
    }

    public static void registerCommands(final MinecraftServer server) {
        HunterCoreBootstrap.init();
        final Command command = new HunterCoreCommand();
        server.server.getCommandMap().register(HunterCoreCommand.COMMAND_LABEL, HunterCoreRuntime.COMMAND_NAMESPACE, command);
    }

    public static String sanitizeConsoleCommand(final String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        StringBuilder sanitized = null;
        for (int index = 0; index < command.length(); index++) {
            final char character = command.charAt(index);
            if (Character.isISOControl(character)) {
                if (sanitized == null) {
                    sanitized = new StringBuilder(command.length());
                    sanitized.append(command, 0, index);
                }
                sanitized.append(' ');
                continue;
            }
            if (sanitized != null) {
                sanitized.append(character);
            }
        }
        return sanitized == null ? command : sanitized.toString().trim();
    }
}
