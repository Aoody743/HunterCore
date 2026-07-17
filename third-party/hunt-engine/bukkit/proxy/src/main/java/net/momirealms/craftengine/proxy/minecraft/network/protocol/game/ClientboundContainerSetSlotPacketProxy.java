package net.momirealms.craftengine.proxy.minecraft.network.protocol.game;

import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.*;

import javax.print.attribute.standard.MediaSize;

@ReflectionProxy(name = "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket")
public interface ClientboundContainerSetSlotPacketProxy {
    ClientboundContainerSetSlotPacketProxy INSTANCE = ASMProxyFactory.create(ClientboundContainerSetSlotPacketProxy.class);
    Class<?> CLASS = SparrowClass.find("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket");

    @FieldGetter(name = "containerId")
    int getContainerId(Object target);

    @FieldGetter(name = "stateId")
    int getStateId(Object target);

    @FieldGetter(name = "slot")
    int getSlot(Object target);

    @FieldGetter(name = "itemStack")
    Object getItemStack(Object target);

    @ConstructorInvoker
    Object newInstance(int containerId, int stateId, int slot, @Type(clazz = ItemStackProxy.class) Object itemStack);
}