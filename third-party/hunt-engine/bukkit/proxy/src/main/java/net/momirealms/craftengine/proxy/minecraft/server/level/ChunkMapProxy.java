package net.momirealms.craftengine.proxy.minecraft.server.level;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldSetter;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

import java.util.Set;

@ReflectionProxy(name = "net.minecraft.server.level.ChunkMap")
public interface ChunkMapProxy {
    ChunkMapProxy INSTANCE = ASMProxyFactory.create(ChunkMapProxy.class);

    @FieldGetter(name = "worldGenContext", activeIf = "min_version=1.20.5")
    Object getWorldGenContext(Object target);

    @FieldSetter(name = "worldGenContext", activeIf = "min_version=1.20.5")
    void setWorldGenContext(Object target, Object worldGenContext);

    @MethodInvoker(name = "getVisibleChunkIfPresent")
    Object getVisibleChunkIfPresent(Object target, long chunkPos);

    @FieldGetter(name = "generator", activeIf = "max_version=1.20.6")
    Object getGenerator(Object target);

    @FieldSetter(name = "generator", activeIf = "max_version=1.20.6")
    void setGenerator(Object target, Object generator);

    @FieldGetter(name = "entityMap", optional = true)
    Int2ObjectMap<Object> getEntityMap(Object target);

    @ReflectionProxy(name = "net.minecraft.server.level.ChunkMap$TrackedEntity")
    interface TrackedEntityProxy {
        TrackedEntityProxy INSTANCE = ASMProxyFactory.create(TrackedEntityProxy.class);

        @FieldGetter(name = "serverEntity")
        Object getServerEntity(Object target);

        @FieldGetter(name = "seenBy")
        Set<Object> getSeenBy(Object target);
    }
}
