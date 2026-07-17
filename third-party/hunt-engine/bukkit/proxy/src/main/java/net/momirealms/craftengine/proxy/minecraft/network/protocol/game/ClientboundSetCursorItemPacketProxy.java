package net.momirealms.craftengine.proxy.minecraft.network.protocol.game;

import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket")
public interface ClientboundSetCursorItemPacketProxy {
    ClientboundSetCursorItemPacketProxy INSTANCE = ASMProxyFactory.create(ClientboundSetCursorItemPacketProxy.class);
    Class<?> CLASS = SparrowClass.find("net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket");

    @FieldGetter(name = "contents")
    Object getContents(Object packet);

    @ConstructorInvoker
    Object newInstance(@Type(clazz = ItemStackProxy.class) Object contents);
}