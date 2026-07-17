package net.momirealms.craftengine.core.plugin.dependency;

import net.momirealms.craftengine.core.plugin.Plugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves dependencies already bundled into HuntEngine.jar.
 *
 * <p>The upstream Community Edition downloads and remaps libraries into the
 * plugin data folder during startup. HunterCraft distributions are offline
 * deployable: the Paper shadow jar contains the required runtime libraries, so
 * this manager intentionally never opens a remote repository or writes a
 * dependency cache.</p>
 */
public final class DependencyManagerImpl implements DependencyManager {
    private final Set<Dependency> loaded = ConcurrentHashMap.newKeySet();
    private final ClassLoader bundledClassLoader;

    public DependencyManagerImpl(Plugin plugin) {
        this.bundledClassLoader = DependencyManagerImpl.class.getClassLoader();
    }

    @Override
    public ClassLoader obtainClassLoaderWith(Set<Dependency> dependencies) {
        Set<Dependency> missing = new HashSet<>(dependencies);
        missing.removeAll(this.loaded);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Bundled HuntEngine dependencies were requested before being registered: " + missing
            );
        }
        return this.bundledClassLoader;
    }

    @Override
    public void loadDependencies(Collection<Dependency> dependencies) {
        // The Gradle Paper distribution embeds these libraries at build time.
        // Marking the requested set preserves the upstream API while avoiding
        // filesystem caches and every form of startup network download.
        this.loaded.addAll(dependencies);
    }

    @Override
    public void close() {
        // All dependencies share the plugin class loader; there are no
        // isolated loaders or temporary jars to close.
    }
}
