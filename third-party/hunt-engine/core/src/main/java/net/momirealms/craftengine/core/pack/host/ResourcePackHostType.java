package net.momirealms.craftengine.core.pack.host;

import net.momirealms.craftengine.core.util.Key;

public record ResourcePackHostType<T extends ResourcePackHost>(Key id, ResourcePackHostFactory<T> factory) {
}
