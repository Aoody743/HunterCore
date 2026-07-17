package net.momirealms.craftengine.bukkit.plugin.proxy;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.plugin.proxy.AbstractProxyMessageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BukkitProxyMessageManager extends AbstractProxyMessageManager implements Listener {
    private final BukkitCraftEngine plugin;

    public BukkitProxyMessageManager(BukkitCraftEngine plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public void delayedInit() {
        if (!this.plugin.platform().hasProxy()) return;
        super.delayedInit();
        Bukkit.getPluginManager().registerEvents(this, this.plugin.javaPlugin());
    }

    @Override
    public void disable() {
        super.disable();
        HandlerList.unregisterAll(this);
    }

    // 玩家离开服务器, 清理缓存数据
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.removeUser(event.getPlayer().getUniqueId());
    }
}
