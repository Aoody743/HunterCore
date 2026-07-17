package org.huntercore.network.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.huntercore.network.protocol.HunterNetworkProtocol;

import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Optional Velocity companion; configure the same network id as the backend proxies.yml entry. */
@Plugin(id = "huntercore-network-velocity", name = "HunterCore-Network-Velocity", version = "2.9.16")
public final class HunterCoreVelocityPlugin {
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.from(HunterNetworkProtocol.CHANNEL);
    private String network = "default";
    private String nodeId = "velocity-main";
    @Inject public HunterCoreVelocityPlugin(ProxyServer proxy, @DataDirectory Path dataDirectory) { this.proxy = proxy; this.dataDirectory = dataDirectory; }
    @Subscribe public void onProxyInitialization(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        loadConfiguration();
        proxy.getChannelRegistrar().register(channel);
        proxy.getScheduler().buildTask(this, this::publishSnapshot).repeat(5, TimeUnit.SECONDS).schedule();
    }
    @Subscribe public void onJoin(PostLoginEvent event) { publishSnapshot(); }
    @Subscribe public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.equals(event.getIdentifier()) || !(event.getSource() instanceof ServerConnection source)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        try {
            Object decoded = HunterNetworkProtocol.decode(event.getData());
            if (decoded instanceof HunterNetworkProtocol.ChatMessage chat) {
                byte[] forwarded = HunterNetworkProtocol.chat(network, nodeId, chat.sender(), chat.server(), chat.message());
                proxy.getAllServers().stream().filter(server -> !server.getServerInfo().equals(source.getServerInfo()))
                    .forEach(server -> server.sendPluginMessage(channel, forwarded));
            }
        } catch (Exception ignored) { }
    }
    private void publishSnapshot() {
        try {
            var players = proxy.getAllPlayers().stream().map(p -> new HunterNetworkProtocol.PlayerEntry(
                    p.getUsername(), p.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(""), p.getUniqueId().toString())).toList();
            broadcast(HunterNetworkProtocol.snapshot(network, nodeId, players));
        } catch (Exception ignored) { }
    }
    private void broadcast(byte[] data) { proxy.getAllServers().forEach(server -> server.sendPluginMessage(channel, data)); }
    private void loadConfiguration() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("network.properties");
            Properties properties = new Properties();
            if (Files.isRegularFile(file)) try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
            network = properties.getProperty("network", network).trim();
            nodeId = properties.getProperty("node-id", nodeId).trim();
            properties.setProperty("network", network);
            properties.setProperty("node-id", nodeId);
            try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "HunterCore proxy network identity; node-id must match proxies.yml"); }
        } catch (Exception ignored) { }
    }
}
