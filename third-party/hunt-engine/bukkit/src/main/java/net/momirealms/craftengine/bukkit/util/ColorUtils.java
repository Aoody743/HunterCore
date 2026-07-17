package net.momirealms.craftengine.bukkit.util;

import org.bukkit.Color;

public final class ColorUtils {
    private ColorUtils() {}

    public static Color toBukkit(net.momirealms.craftengine.core.util.Color color) {
        return Color.fromARGB(color.a(), color.r(), color.g(), color.b());
    }
}
