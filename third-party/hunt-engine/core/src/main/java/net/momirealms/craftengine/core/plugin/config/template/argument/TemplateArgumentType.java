package net.momirealms.craftengine.core.plugin.config.template.argument;

import net.momirealms.craftengine.core.util.Key;

public record TemplateArgumentType<T extends TemplateArgument>(Key id, TemplateArgumentFactory<T> factory) {
}
