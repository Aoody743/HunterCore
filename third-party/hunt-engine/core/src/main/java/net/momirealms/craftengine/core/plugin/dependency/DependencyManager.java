package net.momirealms.craftengine.core.plugin.dependency;

import java.util.Collection;
import java.util.Set;

/**
 * Loads and manages runtime dependencies for the plugin.
 */
public interface DependencyManager {

    /**
     * Loads dependencies.
     *
     * @param dependencies the dependencies to load
     */
    void loadDependencies(Collection<Dependency> dependencies);

    /**
     * Obtains an isolated classloader containing the given dependencies.
     *
     * @param dependencies the dependencies
     * @return the classloader
     */
    ClassLoader obtainClassLoaderWith(Set<Dependency> dependencies);

    void close();
}
