package net.momirealms.craftengine.proxy.spigotmc;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "org.spigotmc.SpigotConfig", optional = true)
public interface SpigotConfigProxy {
    SpigotConfigProxy INSTANCE = ASMProxyFactory.create(SpigotConfigProxy.class);

    @FieldGetter(name = "bungee", isStatic = true)
    boolean getBungee();
}
