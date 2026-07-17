package net.momirealms.craftengine.bukkit.plugin.injector;

import net.momirealms.craftengine.bukkit.nms.FastNMS;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.proxy.minecraft.world.item.ItemsProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.DispenserBlockProxy;

public final class DispenserInjector {
    private DispenserInjector() {}

    public static void init() {
        if (VersionHelper.isOrAbove1_21) {
            DispenserBlockProxy.INSTANCE.registerBehavior(ItemsProxy.WIND_CHARGE, FastNMS.INSTANCE.createInjectedProjectileDispenseBehavior(ItemsProxy.WIND_CHARGE));
        }
    }
}
