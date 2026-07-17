package net.momirealms.craftengine.proxy.minecraft.advancements.triggers;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = {"net.minecraft.advancements.triggers.CriterionTrigger", "net.minecraft.advancements.CriterionTrigger"})
public interface CriterionTriggerProxy {
    CriterionTriggerProxy INSTANCE = ASMProxyFactory.create(CriterionTriggerProxy.class);
}
