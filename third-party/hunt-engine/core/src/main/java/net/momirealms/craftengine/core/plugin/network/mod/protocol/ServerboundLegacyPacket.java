package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.mod.CustomPackets;
import net.momirealms.craftengine.core.plugin.network.mod.ServerCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

public final class ServerboundLegacyPacket implements ServerCustomPacket {
    public static final ServerboundLegacyPacket INSTANCE = new ServerboundLegacyPacket();
    public static final Key ID = Key.ce("payload");
    public static final NetworkCodec<FriendlyByteBuf, ServerboundLegacyPacket> CODEC = ServerCustomPacket.codec(
            (packet, buf) -> {
            },
            buf -> INSTANCE
    );

    private ServerboundLegacyPacket() {
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ServerboundLegacyPacket> codec() {
        return CODEC;
    }

    @Override
    public void handle(NetWorkUser user, ByteBufPacketEvent event) {
        CustomPackets.checkProtocolVersion(user);
    }
}
