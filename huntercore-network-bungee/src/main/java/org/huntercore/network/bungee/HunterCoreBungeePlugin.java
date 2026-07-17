package org.huntercore.network.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.huntercore.network.protocol.HunterNetworkProtocol;

import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Optional BungeeCord companion. The backend remains authoritative for trust decisions. */
public final class HunterCoreBungeePlugin extends Plugin implements Listener {
    private String network = "default";
    private String nodeId = "bungee-main";
    @Override public void onEnable() {
        loadConfiguration();
        ProxyServer.getInstance().registerChannel(HunterNetworkProtocol.CHANNEL);
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
        getProxy().getScheduler().schedule(this, this::publishSnapshot, 2, 5, TimeUnit.SECONDS);
    }
    @Override public void onDisable() { ProxyServer.getInstance().unregisterChannel(HunterNetworkProtocol.CHANNEL); }
    @EventHandler public void onJoin(PostLoginEvent event) { publishSnapshot(); }
    @EventHandler public void onPluginMessage(PluginMessageEvent event) {
        if (!HunterNetworkProtocol.CHANNEL.equals(event.getTag()) || !(event.getSender() instanceof Server source)) return;
        try {
            Object decoded = HunterNetworkProtocol.decode(event.getData());
            if (decoded instanceof HunterNetworkProtocol.ChatMessage chat) {
                byte[] forwarded = HunterNetworkProtocol.chat(network, nodeId, chat.sender(), chat.server(), chat.message());
                broadcastExcept(forwarded, source.getInfo());
            }
        } catch (Exception ignored) { }
    }
    private void publishSnapshot() {
        try {
            var players = getProxy().getPlayers().stream().map(p -> new HunterNetworkProtocol.PlayerEntry(
                    p.getName(), p.getServer() == null ? "" : p.getServer().getInfo().getName(), p.getUniqueId().toString())).toList();
            broadcast(HunterNetworkProtocol.snapshot(network, nodeId, players));
        } catch (Exception ignored) { }
    }
    private void broadcast(byte[] data) { for (ServerInfo server : getProxy().getServers().values()) server.sendData(HunterNetworkProtocol.CHANNEL, data, true); }
    private void broadcastExcept(byte[] data, ServerInfo excluded) {
        for (ServerInfo server : getProxy().getServers().values()) if (!server.equals(excluded)) server.sendData(HunterNetworkProtocol.CHANNEL, data, true);
    }
    private void loadConfiguration() {
        try {
            if (!getDataFolder().isDirectory()) getDataFolder().mkdirs();
            File file = new File(getDataFolder(), "network.properties");
            Properties properties = new Properties();
            if (file.isFile()) try (FileInputStream input = new FileInputStream(file)) { properties.load(input); }
            network = properties.getProperty("network", network).trim();
            nodeId = properties.getProperty("node-id", nodeId).trim();
            properties.setProperty("network", network);
            properties.setProperty("node-id", nodeId);
            try (FileOutputStream output = new FileOutputStream(file)) { properties.store(output, "HunterCore proxy network identity; node-id must match proxies.yml"); }
        } catch (Exception ex) { getLogger().warning("Unable to load network.properties: " + ex.getMessage()); }
    }
}
