package org.huntercore.bootstrap;

import org.huntercore.api.HunterCoreProvider;
import org.huntercore.optimization.HunterCoreOptimizer;

public final class HunterCoreBootstrap {
    private static boolean initialized;

    private HunterCoreBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        HunterCoreOptimizer.applyStartupDefaults();
        HunterCoreProvider.register(HunterCoreRuntime.get());
        initialized = true;
    }
}
