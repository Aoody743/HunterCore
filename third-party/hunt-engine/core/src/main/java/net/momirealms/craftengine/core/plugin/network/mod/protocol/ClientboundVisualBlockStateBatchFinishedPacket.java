package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.mod.ClientCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

public final class ClientboundVisualBlockStateBatchFinishedPacket implements ClientCustomPacket {
    public static final ClientboundVisualBlockStateBatchFinishedPacket INSTANCE = new ClientboundVisualBlockStateBatchFinishedPacket();
    public static final Key ID = Key.ce("visual_block_state_batch_finished");
    public static final NetworkCodec<FriendlyByteBuf, ClientboundVisualBlockStateBatchFinishedPacket> CODEC = ClientCustomPacket.codec(
            (packet, buf) -> {
            },
            buf -> INSTANCE
    );

    private ClientboundVisualBlockStateBatchFinishedPacket() {
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ClientboundVisualBlockStateBatchFinishedPacket> codec() {
        return CODEC;
    }
}
