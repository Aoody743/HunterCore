package net.momirealms.craftengine.core.plugin.context.selector;

import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.util.Key;

public record PlayerSelectorType<CTX extends Context>(Key id, PlayerSelectorFactory<CTX> factory) {
}
