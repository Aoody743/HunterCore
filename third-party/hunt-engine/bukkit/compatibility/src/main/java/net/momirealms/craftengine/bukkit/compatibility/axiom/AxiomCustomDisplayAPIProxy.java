package net.momirealms.craftengine.bukkit.compatibility.axiom;

import com.moulberry.axiom.paperapi.AxiomCustomDisplayAPI;
import com.moulberry.axiom.paperapi.display.AxiomCustomDisplayBuilder;
import net.momirealms.craftengine.proxy.adventure.key.AdventureKeyProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;
import org.bukkit.inventory.ItemStack;

@ReflectionProxy(clazz = AxiomCustomDisplayAPI.class)
public interface AxiomCustomDisplayAPIProxy {
    AxiomCustomDisplayAPIProxy INSTANCE = ASMProxyFactory.create(AxiomCustomDisplayAPIProxy.class);

    @MethodInvoker(name = "create")
    AxiomCustomDisplayBuilder create(Object target, @Type(clazz = AdventureKeyProxy.class) Object key, String searchKey, ItemStack itemStack);
}
