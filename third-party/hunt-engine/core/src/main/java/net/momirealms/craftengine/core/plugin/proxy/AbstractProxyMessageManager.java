package net.momirealms.craftengine.core.plugin.proxy;

import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.mod.CustomPackets;
import net.momirealms.craftengine.core.plugin.proxy.packet.ProxyboundNetworkTagDataPacket;
import net.momirealms.craftengine.core.plugin.proxy.packet.ServerboundNetworkTagDataVersionPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractProxyMessageManager implements ProxyMessageManager {
    protected final CraftEngine plugin;
    protected final Map<UUID, Map<UUID, Object>> proxyPlayers;
    protected final Map<UUID, UUID> proxyByPlayer;
    protected long networkTagDataVersion = System.currentTimeMillis();

    public AbstractProxyMessageManager(CraftEngine plugin) {
        this.plugin = plugin;
        this.proxyPlayers = new ConcurrentHashMap<>(); // ProxyUUID -> Set<PlayerUUID>
        this.proxyByPlayer = new ConcurrentHashMap<>(); // PlayerUUID -> ProxyUUID
    }

    @Override
    public void delayedInit() {
        CustomPackets.registerClientbound(ProxyboundNetworkTagDataPacket.ID, ProxyboundNetworkTagDataPacket.CODEC, CustomPackets.ALWAYS_ALLOWED, false);
        CustomPackets.registerServerbound(ServerboundNetworkTagDataVersionPacket.ID, ServerboundNetworkTagDataVersionPacket.CODEC, CustomPackets.ALWAYS_ALLOWED);
    }

    @Override
    public void delayedLoad() {
        this.networkTagDataVersion = System.currentTimeMillis();
        ProxyboundNetworkTagDataPacket.rebuildDataCache();
        for (Map<UUID, Object> set : this.proxyPlayers.values()) {
            for (UUID playerUUID : set.keySet()) {
                NetWorkUser user = CraftEngine.instance().networkManager().getOnlineUser(playerUUID);
                if (user == null) continue;
                user.sendCustomPackets(ProxyboundNetworkTagDataPacket.cachedPackets());
            }
        }
    }

    @Override
    public void disable() {
        this.proxyPlayers.clear();
        this.proxyByPlayer.clear();
    }

    @Override
    public void addUser(@NotNull UUID userUUID, UUID proxyUUID) {
        this.proxyPlayers.computeIfAbsent(proxyUUID, it -> new ConcurrentHashMap<>()).put(userUUID, Boolean.TRUE);
        this.proxyByPlayer.put(userUUID, proxyUUID);
    }

    @Override
    public void removeUser(@NotNull UUID userUUID) {
        UUID removed = this.proxyByPlayer.remove(userUUID);
        if (removed == null) return;
        Map<UUID, Object> entries = this.proxyPlayers.get(removed);
        if (entries == null) return;
        entries.remove(userUUID);
    }

    @Override
    public long networkTagDataVersion() {
        return this.networkTagDataVersion;
    }
}
