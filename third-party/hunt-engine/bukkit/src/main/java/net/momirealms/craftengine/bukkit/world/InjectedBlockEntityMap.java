package net.momirealms.craftengine.bukkit.world;

import net.momirealms.craftengine.core.util.MapListener;

import java.util.Map;
import java.util.function.Consumer;

public final class InjectedBlockEntityMap<K, V> extends MapListener<K, V> {

    public InjectedBlockEntityMap(Map<K, V> original, Consumer<V> putListener) {
        super(original, putListener);
    }
}
