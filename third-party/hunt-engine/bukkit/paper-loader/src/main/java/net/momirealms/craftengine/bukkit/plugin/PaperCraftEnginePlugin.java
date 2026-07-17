package net.momirealms.craftengine.bukkit.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCraftEnginePlugin extends JavaPlugin {
    private final PaperCraftEngineBootstrap bootstrap;

    public PaperCraftEnginePlugin(PaperCraftEngineBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.bootstrap.plugin.setJavaPlugin(this);
    }

    @Override
    public void onLoad() {
        this.bootstrap.plugin.onPluginLoad();
    }

    @Override
    public void onEnable() {
        this.bootstrap.plugin.onPluginEnable();
    }

    @Override
    public void onDisable() {
        this.bootstrap.plugin.onPluginDisable();
    }
}
