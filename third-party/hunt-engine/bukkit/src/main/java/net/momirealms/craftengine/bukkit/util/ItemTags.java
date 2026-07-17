package net.momirealms.craftengine.bukkit.util;

import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.proxy.minecraft.core.registries.RegistriesProxy;
import net.momirealms.craftengine.proxy.minecraft.tags.TagKeyProxy;

import java.util.HashMap;
import java.util.Map;

public final class ItemTags {
    private static final Map<Key, Object> CACHE = new HashMap<>();

    private ItemTags() {}

    public static Object getOrCreate(Key key) {
        Object value = CACHE.get(key);
        if (value == null) {
            value = TagKeyProxy.INSTANCE.create(RegistriesProxy.ITEM, KeyUtils.toIdentifier(key));
            CACHE.put(key, value);
        }
        return value;
    }
}
