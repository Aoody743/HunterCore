package net.momirealms.craftengine.proxy.adventure.key;

import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net{}kyori{}adventure{}key{}Key", ignoreRelocation = true)
public interface AdventureKeyProxy {
    AdventureKeyProxy INSTANCE = ASMProxyFactory.create(AdventureKeyProxy.class);
    Class<?> CLASS = SparrowClass.find("net{}kyori{}adventure{}key{}Key".replace("{}", "."));

    @MethodInvoker(name = "key", isStatic = true)
    Object key(String namespace, String value);
}
