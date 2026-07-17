package net.momirealms.craftengine.core.plugin.proxy;

import net.momirealms.craftengine.core.plugin.Manageable;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface ProxyMessageManager extends Manageable {

    void addUser(@NotNull UUID userUUID, UUID proxyUUID);

    void removeUser(@NotNull UUID userUUID);

    long networkTagDataVersion();
}
