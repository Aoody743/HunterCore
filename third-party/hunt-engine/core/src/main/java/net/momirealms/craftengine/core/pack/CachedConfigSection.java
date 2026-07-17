package net.momirealms.craftengine.core.pack;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.template.argument.TemplateArgument;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;

public final class CachedConfigSection {
    public final Pack pack;
    public final Path path;
    public final ConfigSection config;
    @Nullable
    public final Map<String, TemplateArgument> arguments;

    public CachedConfigSection(Pack pack, Path path, ConfigSection config, @Nullable Map<String, TemplateArgument> arguments) {
        this.pack = pack;
        this.path = path;
        this.config = config;
        this.arguments = arguments;
    }

    public boolean hasArguments() {
        return this.arguments != null && !this.arguments.isEmpty();
    }

    @Nullable
    public Map<String, TemplateArgument> arguments() {
        return this.arguments;
    }

    public Pack pack() {
        return this.pack;
    }

    public Path path() {
        return this.path;
    }

    public ConfigSection config() {
        return this.config;
    }
}