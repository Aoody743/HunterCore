package net.momirealms.craftengine.bukkit.entity.projectile;

import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.bukkit.util.EntityUtils;
import net.momirealms.craftengine.core.item.ItemKeys;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.entity.CraftEntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;

public final class ProjectileItems {
    private ProjectileItems() {}
    private static final Map<Key, ProjectileFactory> PROJECTILE_FACTORIES = new HashMap<>();
    static {
        PROJECTILE_FACTORIES.put(ItemKeys.EXPERIENCE_BOTTLE, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, ThrownExpBottle.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.ENDER_PEARL, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, EnderPearl.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.EGG, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, Egg.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.BROWN_EGG, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, Egg.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.BLUE_EGG, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, Egg.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.SNOWBALL, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, Snowball.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.TRIDENT, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, Trident.class, e -> {
                e.setShooter(source);
                e.setItemStack(itemStack);
                e.setCritical(isCritical);
                e.setLoyaltyLevel(itemStack.getEnchantmentLevel(Enchantment.LOYALTY));
                Object entity = CraftEntityProxy.INSTANCE.getEntity(e);
                EntityProxy.INSTANCE.setRot(entity, location.getYaw(), location.getPitch());
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.FIRE_CHARGE, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, SizedFireball.class, e -> {
                e.setShooter(source);
                e.setDisplayItem(itemStack);
            });
        });
        PROJECTILE_FACTORIES.put(ItemKeys.FIREWORK_ROCKET, (location, itemStack, source, isCritical) -> {
            return EntityUtils.spawnEntity(location.getWorld(), location, Firework.class, e -> {
                e.setShooter(source);
                e.setItem(itemStack);
                if (itemStack.getItemMeta() instanceof FireworkMeta meta) {
                    e.setFireworkMeta(meta);
                }
            });
        });
    }

    public interface ProjectileFactory {

        Projectile create(Location location, ItemStack itemStack, ProjectileSource source, boolean isCritical);
    }

    public static Projectile createProjectileByItem(Location location, BukkitItem item, ProjectileSource source, boolean critical) {
        ProjectileFactory projectileFactory = PROJECTILE_FACTORIES.get(item.id());
        if (projectileFactory == null) {
            projectileFactory = PROJECTILE_FACTORIES.get(item.vanillaId());
        }
        if (projectileFactory == null) {
            return null;
        }
        return projectileFactory.create(location, item.getBukkitItem(), source, critical);
    }
}