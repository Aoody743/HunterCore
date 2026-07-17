package net.momirealms.craftengine.core.plugin.proxy.packet;

import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.mod.ServerCustomPacket;
import net.momirealms.craftengine.core.plugin.proxy.ProxyMessageManager;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

import java.util.UUID;

public record ServerboundNetworkTagDataVersionPacket(long proxyTagDataVersion, UUID proxyUuid) implements ServerCustomPacket {
    public static final Key ID = Key.ce("tag_data");
    public static final NetworkCodec<FriendlyByteBuf, ServerboundNetworkTagDataVersionPacket> CODEC = ServerCustomPacket.codec(
            (packet, buf) -> {
                buf.writeVarLong(packet.proxyTagDataVersion);
                buf.writeUUID(packet.proxyUuid);
            },
            buf -> {
                long dataVersion = buf.readVarLong();
                UUID proxyUUID = buf.readUUID();
                return new ServerboundNetworkTagDataVersionPacket(dataVersion, proxyUUID);
            }
    );

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ServerboundNetworkTagDataVersionPacket> codec() {
        return CODEC;
    }

    /**
     * 当收到玩家进服后的数据版本号和代理UUID, 记录玩家所在的代理UUID, 并根据版本号决定是否要重发TagData包回去.
     */
    @Override
    public void handle(NetWorkUser user, ByteBufPacketEvent event) {
        ProxyMessageManager manager = CraftEngine.instance().proxyMessageManager();
        manager.addUser(user.uuid(), this.proxyUuid);
        if (this.proxyTagDataVersion != manager.networkTagDataVersion()) {
            user.sendCustomPackets(ProxyboundNetworkTagDataPacket.cachedPackets());
        }
    }
}
