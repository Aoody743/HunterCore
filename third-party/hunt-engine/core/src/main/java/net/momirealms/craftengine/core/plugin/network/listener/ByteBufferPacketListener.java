package net.momirealms.craftengine.core.plugin.network.listener;

import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;

public interface ByteBufferPacketListener {

    default void onPacketReceive(NetWorkUser user, ByteBufPacketEvent event) {
    }

    default void onPacketSend(NetWorkUser user, ByteBufPacketEvent event) {
    }
}
