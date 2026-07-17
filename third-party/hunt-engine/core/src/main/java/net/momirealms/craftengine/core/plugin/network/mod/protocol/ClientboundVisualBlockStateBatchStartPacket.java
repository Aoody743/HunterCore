package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.mod.ClientCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

public record ClientboundVisualBlockStateBatchStartPacket(int size) implements ClientCustomPacket {
    public static final Key ID = Key.ce("visual_block_state_batch_start");
    public static final NetworkCodec<FriendlyByteBuf, ClientboundVisualBlockStateBatchStartPacket> CODEC = ClientCustomPacket.codec(
            (packet, buf) -> buf.writeVarInt(packet.size),
            buf -> new ClientboundVisualBlockStateBatchStartPacket(buf.readVarInt())
    );

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ClientboundVisualBlockStateBatchStartPacket> codec() {
        return CODEC;
    }
}
