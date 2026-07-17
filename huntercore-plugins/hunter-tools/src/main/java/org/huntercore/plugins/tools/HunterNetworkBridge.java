package org.huntercore.plugins.tools;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.network.HunterConnectionService;
import org.huntercore.api.network.HunterRemotePlayer;
import org.huntercore.network.protocol.HunterNetworkProtocol;
import org.jetbrains.annotations.NotNull;

final class HunterNetworkBridge implements Listener, PluginMessageListener {
    private final HunterToolsPlugin plugin;
    private final HunterConnectionService connections;

    HunterNetworkBridge(final HunterToolsPlugin plugin) {
        this.plugin = plugin;
        this.connections = HunterCoreProvider.get().connections();
    }

    void enable() {
        this.plugin.getServer().getMessenger().registerIncomingPluginChannel(this.plugin, HunterNetworkProtocol.CHANNEL, this);
        this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, HunterNetworkProtocol.CHANNEL);
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    void disable() {
        this.plugin.getServer().getMessenger().unregisterIncomingPluginChannel(this.plugin, HunterNetworkProtocol.CHANNEL, this);
        this.plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(this.plugin, HunterNetworkProtocol.CHANNEL);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.connections.refreshRemotePlayers(event.getPlayer()), 10L);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (!this.connections.groupChatEnabled()) {
            return;
        }
        final Player sender = event.getPlayer();
        final String network = this.connections.networkId(sender);
        if (this.connections.source(sender).proxied()) {
            this.forwardOnMainThread(sender, network, event.getMessage());
            return;
        }
        final Set<String> sentNodes = new HashSet<>();
        for (final Player carrier : Bukkit.getOnlinePlayers()) {
            if (!this.connections.source(carrier).proxied()) {
                continue;
            }
            final String key = this.connections.proxyId(carrier) + '\0' + this.connections.networkId(carrier);
            if (sentNodes.add(key)) {
                this.forwardOnMainThread(carrier, this.connections.networkId(carrier), sender.getName(), event.getMessage());
            }
        }
    }

    private void forwardOnMainThread(final Player carrier, final String network, final String message) {
        this.forwardOnMainThread(carrier, network, carrier.getName(), message);
    }

    private void forwardOnMainThread(final Player carrier, final String network, final String senderName, final String message) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!carrier.isOnline()) {
                return;
            }
            try {
                carrier.sendPluginMessage(this.plugin, HunterNetworkProtocol.CHANNEL, HunterNetworkProtocol.chat(
                    network,
                    this.connections.serverId(),
                    senderName,
                    this.connections.serverId(),
                    message
                ));
            } catch (final IOException ex) {
                this.plugin.getLogger().warning("Unable to forward group chat: " + ex.getMessage());
            }
        });
    }

    @Override
    public void onPluginMessageReceived(@NotNull final String channel, @NotNull final Player carrier, final byte @NotNull [] bytes) {
        if (!channel.equals(HunterNetworkProtocol.CHANNEL) || !this.connections.source(carrier).proxied()) {
            return;
        }
        try {
            final Object decoded = HunterNetworkProtocol.decode(bytes);
            if (decoded instanceof final HunterNetworkProtocol.Snapshot snapshot) {
                if (!this.trustedCarrier(carrier, snapshot.network(), snapshot.source())) {
                    return;
                }
                final List<HunterRemotePlayer> players = snapshot.players().stream().map(entry -> new HunterRemotePlayer(
                    UUID.fromString(entry.uuid()), entry.name(), entry.server()
                )).toList();
                this.connections.replaceRemotePlayers(snapshot.source(), snapshot.network(), players);
            } else if (decoded instanceof final HunterNetworkProtocol.ChatMessage chat) {
                if (!this.connections.groupChatEnabled() || !this.trustedCarrier(carrier, chat.network(), chat.source())) {
                    return;
                }
                final String rendered = ChatColor.DARK_AQUA + "[群组/" + chat.server() + "] "
                    + ChatColor.RESET + chat.sender() + ": " + chat.message();
                for (final Player recipient : Bukkit.getOnlinePlayers()) {
                    if (this.connections.source(recipient).proxied() && this.connections.networkId(recipient).equals(chat.network())) {
                        recipient.sendMessage(rendered);
                    }
                }
            }
        } catch (final IOException | IllegalArgumentException ex) {
            this.plugin.getLogger().warning("Rejected invalid HunterCore network message from " + carrier.getName() + ": " + ex.getMessage());
        }
    }

    private boolean trustedCarrier(final Player carrier, final String network, final String source) {
        return this.connections.networkId(carrier).equals(network) && this.connections.proxyId(carrier).equals(source);
    }
}
