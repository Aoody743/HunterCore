package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.mod.ServerCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

public final class ServerboundCancelBlockUpdateRequestPacket implements ServerCustomPacket {
    public static final ServerboundCancelBlockUpdateRequestPacket INSTANCE = new ServerboundCancelBlockUpdateRequestPacket();
    public static final Key ID = Key.ce("cancel_block_update_request");
    public static final NetworkCodec<FriendlyByteBuf, ServerboundCancelBlockUpdateRequestPacket> CODEC = ServerCustomPacket.codec(
            (packet, buf) -> {
            },
            buf -> INSTANCE
    );

    private ServerboundCancelBlockUpdateRequestPacket() {
    }

    @Override
    public void handle(NetWorkUser user, ByteBufPacketEvent event) {
        user.sendCustomPacket(ClientboundCancelBlockUpdateResponsePacket.INSTANCE);
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ServerboundCancelBlockUpdateRequestPacket> codec() {
        return CODEC;
    }
}
