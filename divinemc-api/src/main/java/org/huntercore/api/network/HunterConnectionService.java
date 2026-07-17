package org.huntercore.api.network;

import io.papermc.paper.connection.PlayerLoginConnection;
import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface HunterConnectionService {

    boolean available();

    @NotNull HunterConnectionSource source(@NotNull PlayerLoginConnection connection);

    @NotNull HunterConnectionSource source(@NotNull Player player);

    boolean onlineAuthenticated(@NotNull PlayerLoginConnection connection);

    boolean onlineAuthenticated(@NotNull Player player);

    @NotNull String proxyId(@NotNull Player player);

    @NotNull String networkId(@NotNull Player player);

    @NotNull String serverId();

    boolean groupChatEnabled();

    void replaceRemotePlayers(@NotNull String source, @NotNull String network, @NotNull List<HunterRemotePlayer> players);

    void refreshRemotePlayers(@NotNull Player viewer);

    static @NotNull HunterConnectionService unavailable() {
        return Unavailable.INSTANCE;
    }

    enum Unavailable implements HunterConnectionService {
        INSTANCE;

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public @NotNull HunterConnectionSource source(@NotNull final PlayerLoginConnection connection) {
            return connection instanceof final HunterConnectionInfo info ? info.hunterConnectionSource() : HunterConnectionSource.UNKNOWN;
        }

        @Override
        public @NotNull HunterConnectionSource source(@NotNull final Player player) {
            return HunterConnectionSource.UNKNOWN;
        }

        @Override
        public boolean onlineAuthenticated(@NotNull final PlayerLoginConnection connection) {
            return connection instanceof final HunterConnectionInfo info && info.hunterOnlineAuthenticated();
        }

        @Override
        public boolean onlineAuthenticated(@NotNull final Player player) {
            return false;
        }

        @Override
        public @NotNull String proxyId(@NotNull final Player player) {
            return "";
        }

        @Override
        public @NotNull String networkId(@NotNull final Player player) {
            return "";
        }

        @Override
        public @NotNull String serverId() {
            return "backend";
        }

        @Override
        public boolean groupChatEnabled() {
            return false;
        }

        @Override
        public void replaceRemotePlayers(@NotNull final String source, @NotNull final String network, @NotNull final List<HunterRemotePlayer> players) {
        }

        @Override
        public void refreshRemotePlayers(@NotNull final Player viewer) {
        }
    }
}
