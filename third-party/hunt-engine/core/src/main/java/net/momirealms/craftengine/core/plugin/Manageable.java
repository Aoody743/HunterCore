package net.momirealms.craftengine.core.plugin;

public interface Manageable {

    default void init() {
    }

    // after all plugins enabled
    default void delayedInit() {
    }

    // async reload
    default void reload() {
        unload();
        load();
    }

    // async unload
    default void unload() {
    }

    // async load
    default void load() {
    }

    // on plugin disable
    default void disable() {
        unload();
    }

    // after all modules reloaded
    default void delayedLoad() {
    }

    // delayed tasks on main thread
    default void runDelayedSyncTasks() {
    }
}
