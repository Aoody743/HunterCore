package net.momirealms.craftengine.proxy.paper.configuration;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "io.papermc.paper.configuration.GlobalConfiguration")
public interface GlobalConfigurationProxy {
    GlobalConfigurationProxy INSTANCE = ASMProxyFactory.create(GlobalConfigurationProxy.class);

    @MethodInvoker(name = "get", isStatic = true)
    Object get();

    @FieldGetter(name = "proxies")
    Object getProxies(Object target);

    @ReflectionProxy(name = "io.papermc.paper.configuration.GlobalConfiguration$Proxies")
    interface ProxiesProxy {
        ProxiesProxy INSTANCE = ASMProxyFactory.create(ProxiesProxy.class);

        @FieldGetter(name = "velocity")
        Object getVelocity(Object target);

        @ReflectionProxy(name = "io.papermc.paper.configuration.GlobalConfiguration$Proxies$Velocity")
        interface VelocityProxy {
            VelocityProxy INSTANCE = ASMProxyFactory.create(VelocityProxy.class);

            @FieldGetter(name = "enabled")
            boolean getEnabled(Object target);
        }
    }
}
