package org.huntercore.network;

import io.papermc.paper.connection.PlayerLoginConnection;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.huntercore.api.network.HunterConnectionInfo;
import org.huntercore.api.network.HunterConnectionService;
import org.huntercore.api.network.HunterConnectionSource;
import org.huntercore.api.network.HunterRemotePlayer;
import org.jetbrains.annotations.NotNull;

public final class HunterNetworkManager implements HunterConnectionService {
    private final Map<SnapshotKey, List<HunterRemotePlayer>> remoteSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> displayedRemotePlayers = new ConcurrentHashMap<>();

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public @NotNull HunterConnectionSource source(@NotNull final PlayerLoginConnection connection) {
        return info(connection).hunterConnectionSource();
    }

    @Override
    public @NotNull HunterConnectionSource source(@NotNull final Player player) {
        return info(player).hunterConnectionSource();
    }

    @Override
    public boolean onlineAuthenticated(@NotNull final PlayerLoginConnection connection) {
        return info(connection).hunterOnlineAuthenticated();
    }

    @Override
    public boolean onlineAuthenticated(@NotNull final Player player) {
        return info(player).hunterOnlineAuthenticated();
    }

    @Override
    public @NotNull String proxyId(@NotNull final Player player) {
        return info(player).hunterProxyId();
    }

    @Override
    public @NotNull String networkId(@NotNull final Player player) {
        return info(player).hunterNetworkId();
    }

    @Override
    public @NotNull String serverId() {
        return HunterProxyRegistry.serverId();
    }

    @Override
    public boolean groupChatEnabled() {
        return HunterProxyRegistry.groupChatEnabled();
    }

    @Override
    public void replaceRemotePlayers(@NotNull final String source, @NotNull final String network, @NotNull final List<HunterRemotePlayer> players) {
        this.remoteSnapshots.put(new SnapshotKey(source, network), List.copyOf(players));
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            if (this.source(viewer).proxied() && this.networkId(viewer).equals(network)) {
                this.refreshRemotePlayers(viewer);
            }
        }
    }

    @Override
    public void refreshRemotePlayers(@NotNull final Player viewer) {
        if (!(viewer instanceof final CraftPlayer craftViewer) || craftViewer.getHandle().connection == null) {
            return;
        }
        final ServerPlayer handle = craftViewer.getHandle();
        final Set<UUID> local = new HashSet<>();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            local.add(player.getUniqueId());
        }
        final Set<UUID> previous = this.displayedRemotePlayers.getOrDefault(viewer.getUniqueId(), Set.of());
        final List<UUID> removed = previous.stream().filter(id -> !local.contains(id)).toList();
        if (!removed.isEmpty()) {
            handle.connection.send(new ClientboundPlayerInfoRemovePacket(removed));
        }
        if (!this.source(viewer).proxied()) {
            this.displayedRemotePlayers.remove(viewer.getUniqueId());
            return;
        }

        final String network = this.networkId(viewer);
        final Map<UUID, HunterRemotePlayer> merged = new HashMap<>();
        for (final Map.Entry<SnapshotKey, List<HunterRemotePlayer>> snapshot : this.remoteSnapshots.entrySet()) {
            if (!snapshot.getKey().network.equals(network)) {
                continue;
            }
            for (final HunterRemotePlayer remote : snapshot.getValue()) {
                if (!local.contains(remote.uniqueId())) {
                    merged.putIfAbsent(remote.uniqueId(), remote);
                }
            }
        }
        if (merged.isEmpty()) {
            this.displayedRemotePlayers.remove(viewer.getUniqueId());
            return;
        }

        final List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>(merged.size());
        for (final HunterRemotePlayer remote : merged.values()) {
            final GameProfile profile = new GameProfile(remote.uniqueId(), remote.name());
            final Component displayName = Component.literal("[" + remote.server() + "] " + remote.name());
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                remote.uniqueId(), profile, true, 0, GameType.DEFAULT_MODE, displayName, true, 0, null
            ));
        }
        handle.connection.send(new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER
            ),
            entries
        ));
        this.displayedRemotePlayers.put(viewer.getUniqueId(), Set.copyOf(merged.keySet()));
    }

    private static HunterConnectionInfo info(final PlayerLoginConnection connection) {
        return connection instanceof final HunterConnectionInfo info ? info : UnknownInfo.INSTANCE;
    }

    private static HunterConnectionInfo info(final Player player) {
        if (player instanceof final CraftPlayer craftPlayer && craftPlayer.getHandle().connection != null
            && craftPlayer.getHandle().connection.connection instanceof final HunterConnectionInfo info) {
            return info;
        }
        return UnknownInfo.INSTANCE;
    }

    private enum UnknownInfo implements HunterConnectionInfo {
        INSTANCE;

        @Override public @NotNull HunterConnectionSource hunterConnectionSource() { return HunterConnectionSource.UNKNOWN; }
        @Override public @NotNull String hunterProxyId() { return ""; }
        @Override public @NotNull String hunterNetworkId() { return ""; }
        @Override public boolean hunterOnlineAuthenticated() { return false; }
    }

    private record SnapshotKey(String source, String network) {
    }
}
