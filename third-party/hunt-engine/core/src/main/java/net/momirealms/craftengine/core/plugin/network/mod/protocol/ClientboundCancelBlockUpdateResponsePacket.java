package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.mod.ClientCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

public final class ClientboundCancelBlockUpdateResponsePacket implements ClientCustomPacket {
    public static final ClientboundCancelBlockUpdateResponsePacket INSTANCE = new ClientboundCancelBlockUpdateResponsePacket();
    public static final Key ID = Key.ce("cancel_block_update_response");
    public static final NetworkCodec<FriendlyByteBuf, ClientboundCancelBlockUpdateResponsePacket> CODEC = ClientCustomPacket.codec(
            (packet, buf) -> {
            },
            buf -> INSTANCE
    );

    private ClientboundCancelBlockUpdateResponsePacket() {
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ClientboundCancelBlockUpdateResponsePacket> codec() {
        return CODEC;
    }
}
