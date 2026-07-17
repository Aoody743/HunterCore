package net.momirealms.craftengine.proxy.minecraft.world.item;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldSetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

import java.util.function.Predicate;

@ReflectionProxy(name = "net.minecraft.world.item.ProjectileWeaponItem")
public interface ProjectileWeaponItemProxy {
    ProjectileWeaponItemProxy INSTANCE = ASMProxyFactory.create(ProjectileWeaponItemProxy.class);

    @FieldSetter(name = "ARROW_ONLY", isStatic = true)
    void setArrowOnly(Predicate<Object> predicate);

    @FieldSetter(name = "ARROW_OR_FIREWORK", isStatic = true)
    void setArrowOrFirework(Predicate<Object> predicate);
}
