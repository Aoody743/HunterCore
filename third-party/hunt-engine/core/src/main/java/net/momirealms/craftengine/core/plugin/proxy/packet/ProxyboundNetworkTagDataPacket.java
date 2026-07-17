package net.momirealms.craftengine.core.plugin.proxy.packet;

import io.netty.buffer.Unpooled;
import net.momirealms.craftengine.core.font.NetworkTagDataSerializer;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.mod.ClientCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ProxyboundNetworkTagDataPacket(long networkTagDataVersion, int total, int index, byte[] data) implements ClientCustomPacket {
    public static final Key ID = Key.ce("tag_data");
    public static final NetworkCodec<FriendlyByteBuf, ProxyboundNetworkTagDataPacket> CODEC = ClientCustomPacket.codec(
            (packet, buf) -> {
                buf.writeVarLong(packet.networkTagDataVersion); // Version
                buf.writeVarInt(packet.total);
                buf.writeVarInt(packet.index);
                buf.writeBytes(packet.data);
            },
            buf -> {
                long version = buf.readVarLong();
                int total = buf.readVarInt();
                int index = buf.readVarInt();
                byte[] data = buf.readFixedBytes(buf.readableBytes());
                return new ProxyboundNetworkTagDataPacket(version, total, index, data);
            }
    );
    private static final int PAGE_LENGTH = 30000; // https://docs.papermc.io/velocity/reference/system-properties/#velocitymax-plugin-message-payload-size
    private static volatile List<ProxyboundNetworkTagDataPacket> CACHED_PACKETS = List.of();

    public static void rebuildDataCache() {
        byte[] rawData = serializeTagData();
        CACHED_PACKETS = buildPackets(rawData);
    }

    private static byte[] serializeTagData() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NetworkTagDataSerializer.writeOffsetFont(buf, CraftEngine.instance().fontManager().offsetFont());
            NetworkTagDataSerializer.writeImages(buf, CraftEngine.instance().fontManager().loadedImages());
            NetworkTagDataSerializer.writeL10n(buf, CraftEngine.instance().translationManager());
            NetworkTagDataSerializer.writeGlobalVariables(buf, CraftEngine.instance().globalVariableManager());
            return buf.readFixedBytes(buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    private static List<ProxyboundNetworkTagDataPacket> buildPackets(byte[] data) {
        long version = CraftEngine.instance().proxyMessageManager().networkTagDataVersion();
        int total = Math.max(1, (data.length + PAGE_LENGTH - 1) / PAGE_LENGTH);
        List<ProxyboundNetworkTagDataPacket> packets = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int from = i * PAGE_LENGTH;
            byte[] chunk = Arrays.copyOfRange(data, from, Math.min(from + PAGE_LENGTH, data.length));
            packets.add(i, new ProxyboundNetworkTagDataPacket(version, total, i, chunk));
        }
        return packets;
    }

    public static List<ProxyboundNetworkTagDataPacket> cachedPackets() {
        return CACHED_PACKETS;
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ProxyboundNetworkTagDataPacket> codec() {
        return CODEC;
    }
}
