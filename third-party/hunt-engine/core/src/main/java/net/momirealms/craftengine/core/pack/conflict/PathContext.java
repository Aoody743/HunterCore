package net.momirealms.craftengine.core.pack.conflict;

import net.momirealms.craftengine.core.plugin.context.AbstractCommonContext;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;

import java.nio.file.Path;

public final class PathContext extends AbstractCommonContext {
    private final Path path;

    public PathContext(Path path, ContextHolder holder) {
        super(holder);
        this.path = path;
    }

    public Path path() {
        return this.path;
    }

    public static PathContext of(Path path) {
        return new PathContext(path, ContextHolder.empty());
    }
}
