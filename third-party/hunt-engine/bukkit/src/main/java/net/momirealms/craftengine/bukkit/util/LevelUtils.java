package net.momirealms.craftengine.bukkit.util;

import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.ChunkPos;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.CraftChunkProxy;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.CraftWorldProxy;
import net.momirealms.craftengine.proxy.minecraft.core.registries.RegistriesProxy;
import net.momirealms.craftengine.proxy.minecraft.resources.ResourceKeyProxy;
import net.momirealms.craftengine.proxy.minecraft.server.MinecraftServerProxy;
import net.momirealms.craftengine.proxy.minecraft.server.level.ChunkHolderProxy;
import net.momirealms.craftengine.proxy.minecraft.server.level.ServerChunkCacheProxy;
import net.momirealms.craftengine.proxy.minecraft.server.level.ServerLevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import org.bukkit.Chunk;
import org.bukkit.GameEvent;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LevelUtils {
    private LevelUtils() {}

    public static void levelEvent(Object target, @Nullable Object source, int eventId, Object pos, int data) {
        if (VersionHelper.isOrAbove1_21_5) {
            LevelAccessorProxy.INSTANCE.levelEvent$0(target, source, eventId, pos, data);
        } else {
            LevelAccessorProxy.INSTANCE.levelEvent$1(target, source, eventId, pos, data);
        }
    }

    public static Object getEntityLookup(Object level) {
        Object entityLookup;
        if (VersionHelper.isOrAbove1_21) {
            entityLookup = LevelProxy.INSTANCE.moonrise$getEntityLookup(level);
        } else {
            entityLookup = ServerLevelProxy.INSTANCE.getEntityLookup(level);
        }
        return entityLookup;
    }

    @org.jetbrains.annotations.Nullable
    public static Object getMinecraftChunk(@NotNull Chunk bukkitChunk) {
        Object worldServer = CraftChunkProxy.INSTANCE.getWorld(bukkitChunk);
        Object chunkSource = ServerLevelProxy.INSTANCE.getChunkSource(worldServer);
        return getChunkAtIfLoaded(chunkSource, bukkitChunk.getX(), bukkitChunk.getZ());
    }

    public static Object getEntityLookup(World world) {
        Object serverLevel = CraftWorldProxy.INSTANCE.getWorld(world);
        return LevelUtils.getEntityLookup(serverLevel);
    }

    public static Object getChunkAtIfLoaded(Object chunkSource, int chunkX, int chunkZ) {
        if (VersionHelper.hasPaperPatch) {
            if (VersionHelper.isOrAbove1_21) {
                return ServerChunkCacheProxy.INSTANCE.getChunkAtIfLoadedImmediately(chunkSource, chunkX, chunkZ);
            } else {
                return ServerChunkCacheProxy.INSTANCE.getChunkAtIfLoadedMainThread(chunkSource, chunkX, chunkZ);
            }
        } else {
            return ServerChunkCacheProxy.INSTANCE.getChunkNow(chunkSource, chunkX, chunkZ);
        }
    }

    public static World getWorld(Key dimension) {
        Object server = MinecraftServerProxy.INSTANCE.getServer();
        Object level = MinecraftServerProxy.INSTANCE.getLevel(server, ResourceKeyProxy.INSTANCE.create(RegistriesProxy.DIMENSION, KeyUtils.toIdentifier(dimension)));
        if (level == null) return null;
        return LevelProxy.INSTANCE.getWorld(level);
    }

    public static void sendGameEvent(World world, Entity entity, GameEvent event, Vector pos) {
        if (VersionHelper.hasPaperPatch) {
            world.sendGameEvent(entity, event, pos);
        }
    }
}
