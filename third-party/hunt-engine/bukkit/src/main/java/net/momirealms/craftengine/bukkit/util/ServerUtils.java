package net.momirealms.craftengine.bukkit.util;

import net.momirealms.craftengine.proxy.minecraft.server.MinecraftServerProxy;

public final class ServerUtils {
    private ServerUtils() {}

    public static boolean isStopping() {
        return MinecraftServerProxy.INSTANCE.hasStopped(MinecraftServerProxy.INSTANCE.getServer());
    }
}
