package net.momirealms.craftengine.proxy.minecraft.network.protocol.game;

import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;
import org.checkerframework.checker.units.qual.C;

@ReflectionProxy(name = "net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket", optional = true)
public interface ClientboundSetPlayerInventoryPacketProxy {
    ClientboundSetPlayerInventoryPacketProxy INSTANCE = ASMProxyFactory.create(ClientboundSetPlayerInventoryPacketProxy.class);
    Class<?> CLASS = SparrowClass.find("net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket");

    @FieldGetter(name = "slot")
    int getSlot(Object target);

    @FieldGetter(name = "contents")
    Object getContents(Object target);

    @ConstructorInvoker
    Object newInstance(int slot, @Type(clazz = ItemStackProxy.class) Object contents);
}