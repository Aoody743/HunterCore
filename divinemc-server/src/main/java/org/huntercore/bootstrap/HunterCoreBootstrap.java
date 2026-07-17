package org.huntercore.bootstrap;

import java.nio.file.Path;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.huntengine.HuntEngineServices;
import org.huntercore.huntengine.HunterHuntEngineServiceManager;
import org.huntercore.network.HunterProxyRegistry;
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
        HunterCoreOptimizer.applyEarlyPreferenceDefaults(Path.of("plugins", "HunterCore", "preferences.yml"));
        HunterProxyRegistry.loadOrCreate(Path.of("plugins", "HunterCore", HunterProxyRegistry.FILE_NAME));
        HunterCoreProvider.register(HunterCoreRuntime.get());
        HuntEngineServices.installResolver(HunterHuntEngineServiceManager::resolve);
        initialized = true;
    }
}
