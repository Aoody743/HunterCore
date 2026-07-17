package net.momirealms.craftengine.core.plugin.network.mod.protocol;

import net.momirealms.craftengine.core.block.BlockManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodecs;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.mod.ClientCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.LazyReference;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.ChunkPos;
import net.momirealms.craftengine.core.world.chunk.client.ClientChunk;

import java.util.Objects;

public record ClientboundLightPacket(BlockPos pos, byte level) implements ClientCustomPacket {
    public static final Key ID = Key.ce("light");
    public static final NetworkCodec<FriendlyByteBuf, ClientboundLightPacket> CODEC = ClientCustomPacket.codec(
            (packet, buf) -> {
                buf.writeBlockPos(packet.pos);
                NetworkCodecs.BYTE.encode(buf, packet.level);
            },
            buf -> new ClientboundLightPacket(
                    buf.readBlockPos(),
                    NetworkCodecs.BYTE.decode(buf)
            )
    );
    public static final int BLOCK_UPDATE_PACKET_ID = CraftEngine.instance().platform().packetIds().clientboundBlockUpdatePacket();
    public static final LazyReference<int[]> LIGHT_BLOCK_STATES_ID = LazyReference.lazyReference(() -> {
        int[] ids = new int[16];
        BlockManager blockManager = CraftEngine.instance().blockManager();
        ids[0] = Objects.requireNonNull(blockManager.createBlockState("minecraft:air")).registryId();
        for (int i = 1; i < 16; i++) {
            ids[i] = Objects.requireNonNull(blockManager.createBlockState("minecraft:light[level=" + i + "]")).registryId();
        }
        return ids;
    });
    public static final LazyReference<int[]> WATERLOGGED_LIGHT_BLOCK_STATES_ID = LazyReference.lazyReference(() -> {
        int[] ids = new int[16];
        BlockManager blockManager = CraftEngine.instance().blockManager();
        ids[0] = Objects.requireNonNull(blockManager.createBlockState("minecraft:water")).registryId();
        for (int i = 1; i < 16; i++) {
            ids[i] = Objects.requireNonNull(blockManager.createBlockState("minecraft:light[level=" + i + ",waterlogged=true]")).registryId();
        }
        return ids;
    });

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ClientboundLightPacket> codec() {
        return CODEC;
    }

    @Override
    public void handle(NetWorkUser user, ByteBufPacketEvent event) {
        ClientChunk trackedChunk = user.getTrackedChunk(ChunkPos.asLong(pos.x >> 4, pos.z >> 4));
        if (trackedChunk == null) return;
        int blockType = trackedChunk.lightBlockType(pos.x, pos.y, pos.z);
        if (blockType == 0) { // 不替换固体方块
            event.setCancelled(true);
            return;
        }
        // 重写入发出
        FriendlyByteBuf buf = event.getBuffer();
        event.setChanged(true);
        buf.clear();
        buf.writeVarInt(BLOCK_UPDATE_PACKET_ID);
        buf.writeBlockPos(pos);
        if (blockType == 1) { // 替换空气
            buf.writeVarInt(LIGHT_BLOCK_STATES_ID.get()[level]);
        } else { // 替换水
            buf.writeVarInt(WATERLOGGED_LIGHT_BLOCK_STATES_ID.get()[level]);
        }
    }
}
