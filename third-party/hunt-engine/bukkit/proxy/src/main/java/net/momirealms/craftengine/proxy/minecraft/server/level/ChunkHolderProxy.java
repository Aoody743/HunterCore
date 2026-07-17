package net.momirealms.craftengine.proxy.minecraft.server.level;

import net.momirealms.craftengine.proxy.minecraft.world.level.ChunkPosProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

import java.util.List;

@ReflectionProxy(name = "net.minecraft.server.level.ChunkHolder")
public interface ChunkHolderProxy {
    ChunkHolderProxy INSTANCE = ASMProxyFactory.create(ChunkHolderProxy.class);

    @MethodInvoker(name = {"moonrise$getPlayers", "getPlayers"}, activeIf = "has_patch=paper")
    List<Object> getPlayers(Object target, boolean onlyOnWatchDistanceEdge);

    @FieldGetter(name = "playerProvider", optional = true)
    Object getPlayerProvider(Object target);

    @ReflectionProxy(name = "net.minecraft.server.level.ChunkHolder$PlayerProvider")
    interface PlayerProviderProxy {
        PlayerProviderProxy INSTANCE = ASMProxyFactory.create(PlayerProviderProxy.class);

        @MethodInvoker(name = "getPlayers")
        List<Object> getPlayers(Object target, @Type(clazz = ChunkPosProxy.class) Object chunkPos, boolean onlyOnWatchDistanceEdge);
    }
}
