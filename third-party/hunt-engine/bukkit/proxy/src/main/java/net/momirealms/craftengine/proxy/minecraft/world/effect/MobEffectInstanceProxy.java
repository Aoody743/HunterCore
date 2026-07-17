package net.momirealms.craftengine.proxy.minecraft.world.effect;

import net.momirealms.craftengine.proxy.minecraft.core.HolderProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.world.effect.MobEffectInstance")
public interface MobEffectInstanceProxy {
    MobEffectInstanceProxy INSTANCE = ASMProxyFactory.create(MobEffectInstanceProxy.class);

    @ConstructorInvoker(activeIf = "min_version=1.20.5")
    Object newInstance(@Type(clazz = HolderProxy.class) Object effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon);

    @ConstructorInvoker(activeIf = "max_version=1.20.4")
    Object newInstance$legacy(@Type(clazz = MobEffectProxy.class) Object effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon);
}
