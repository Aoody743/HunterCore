package net.momirealms.craftengine.proxy.minecraft.world.level.lighting;

import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockStateProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.world.level.lighting.LightEngine")
public interface LightEngineProxy {
    LightEngineProxy INSTANCE = ASMProxyFactory.create(LightEngineProxy.class);

    @MethodInvoker(name = {"getLightDampeningInto", "getLightBlockInto"}, isStatic = true, activeIf = "min_version=1.21.2")
    int getLightDampeningInto(
            @Type(clazz = BlockStateProxy.class) Object fromState,
            @Type(clazz = BlockStateProxy.class) Object toState,
            @Type(clazz = DirectionProxy.class) Object direction,
            int simpleOpacity
    );

    @MethodInvoker(name = "getLightBlockInto", isStatic = true, activeIf = "max_version=1.21.1")
    int getLightDampeningInto(
            @Type(clazz = BlockGetterProxy.class) Object level,
            @Type(clazz = BlockStateProxy.class) Object fromState,
            @Type(clazz = BlockPosProxy.class) Object fromPos,
            @Type(clazz = BlockStateProxy.class) Object toState,
            @Type(clazz = BlockPosProxy.class) Object toPos,
            @Type(clazz = DirectionProxy.class) Object direction,
            int simpleOpacity
    );

    @MethodInvoker(name = "hasDifferentLightProperties", isStatic = true, activeIf = "min_version=1.21.2")
    boolean hasDifferentLightProperties( @Type(clazz = BlockStateProxy.class) Object oldState, @Type(clazz = BlockStateProxy.class) Object newState);
}
