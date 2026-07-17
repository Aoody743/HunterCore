package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.mod.ClientCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public record ClientboundVisualBlockStatesPacket(int startIndex, int[] data) implements ClientCustomPacket {
    public static final Key ID = Key.ce("visual_block_states");
    public static final NetworkCodec<FriendlyByteBuf, ClientboundVisualBlockStatesPacket> CODEC = ClientCustomPacket.codec(
            (packet, buf) -> {
                buf.writeVarInt(packet.startIndex);
                int[] data = packet.data;
                BitSet present = new BitSet(data.length);
                for (int i = 0; i < data.length; i++) {
                    if (data[i] != -1) present.set(i);
                }
                buf.writeVarInt(data.length);
                byte[] bits = present.toByteArray();
                buf.writeVarInt(bits.length);
                buf.writeBytes(bits);
                for (int v : data) {
                    if (v != -1) buf.writeVarInt(v);
                }
            },
            buf -> {
                int startIndex = buf.readVarInt();
                int len = buf.readVarInt();
                byte[] bits = new byte[buf.readVarInt()];
                buf.readBytes(bits);
                BitSet present = BitSet.valueOf(bits);
                int[] data = new int[len];
                Arrays.fill(data, -1);
                for (int i = present.nextSetBit(0); i >= 0; i = present.nextSetBit(i + 1)) {
                    data[i] = buf.readVarInt();
                }
                return new ClientboundVisualBlockStatesPacket(startIndex, data);
            }
    );
    private static final int PER_PACKET_SIZE = 5000;

    public static List<ClientCustomPacket> create() {
        int vanillaBlockStateCount = CraftEngine.instance().blockManager().vanillaBlockStateCount();
        int serverSideBlockCount = Config.serverSideBlocks();
        int[] mappings = new int[serverSideBlockCount];
        Arrays.fill(mappings, -1);
        for (int i = 0; i < serverSideBlockCount; i++) {
            ImmutableBlockState state = CraftEngine.instance().blockManager().getImmutableBlockStateUnsafe(i + vanillaBlockStateCount);
            if (state.isEmpty()) continue;
            mappings[state.customBlockState().registryId() - vanillaBlockStateCount] = state.visualBlockState().registryId();
        }

        List<ClientCustomPacket> packets = new ArrayList<>();
        packets.add(new ClientboundVisualBlockStateBatchStartPacket(serverSideBlockCount));
        for (int start = 0; start < serverSideBlockCount; start += PER_PACKET_SIZE) {
            int end = Math.min(start + PER_PACKET_SIZE, serverSideBlockCount);
            packets.add(new ClientboundVisualBlockStatesPacket(start, Arrays.copyOfRange(mappings, start, end)));
        }
        packets.add(ClientboundVisualBlockStateBatchFinishedPacket.INSTANCE);
        return packets;
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ClientboundVisualBlockStatesPacket> codec() {
        return CODEC;
    }
}
