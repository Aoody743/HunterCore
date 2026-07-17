package net.momirealms.craftengine.proxy.minecraft.world.level;

import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockStateProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.AABBProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.shapes.CollisionContextProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.world.level.CollisionGetter")
public interface CollisionGetterProxy extends BlockGetterProxy {
    CollisionGetterProxy INSTANCE = ASMProxyFactory.create(CollisionGetterProxy.class);

    @MethodInvoker(name = "getBlockCollisions")
    Iterable<Object> getBlockCollisions(Object target,
                                        @Type(clazz = EntityProxy.class) Object entity,
                                        @Type(clazz = AABBProxy.class) Object aabb);

    @MethodInvoker(name = "isUnobstructed")
    boolean isUnobstructed(Object target,
                           @Type(clazz = BlockStateProxy.class) Object state,
                           @Type(clazz = BlockPosProxy.class) Object pos,
                           @Type(clazz = CollisionContextProxy.class) Object context);
}
