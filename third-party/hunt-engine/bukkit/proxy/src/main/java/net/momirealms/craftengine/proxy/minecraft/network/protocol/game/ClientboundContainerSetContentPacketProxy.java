package net.momirealms.craftengine.proxy.minecraft.network.protocol.game;

import net.momirealms.craftengine.proxy.minecraft.core.NonNullListProxy;
import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

import java.util.List;

@ReflectionProxy(name = "net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket")
public interface ClientboundContainerSetContentPacketProxy {
    ClientboundContainerSetContentPacketProxy INSTANCE = ASMProxyFactory.create(ClientboundContainerSetContentPacketProxy.class);
    Class<?> CLASS = SparrowClass.find("net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket");

    @FieldGetter(name = "containerId")
    int getContainerId(Object target);

    @FieldGetter(name = "stateId")
    int getStateId(Object target);

    @FieldGetter(name = "items")
    List<Object> getItems(Object target);

    @FieldGetter(name = "carriedItem")
    Object getCarriedItem(Object target);

    @ConstructorInvoker(activeIf = "min_version=1.21.5")
    Object newInstance(int containerId, int stateId, List<Object> items, @Type(clazz = ItemStackProxy.class) Object carriedItem);

    @ConstructorInvoker(activeIf = "max_version=1.21.4")
    Object newInstance$legacy(int containerId, int stateId, @Type(clazz = NonNullListProxy.class) Object items, @Type(clazz = ItemStackProxy.class) Object carriedItem);
}