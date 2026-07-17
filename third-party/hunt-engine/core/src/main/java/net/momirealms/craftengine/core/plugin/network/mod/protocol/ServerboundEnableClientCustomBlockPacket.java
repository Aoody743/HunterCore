package net.momirealms.craftengine.core.plugin.network.mod.protocol;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslationArgument;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodecs;
import net.momirealms.craftengine.core.plugin.network.event.ByteBufPacketEvent;
import net.momirealms.craftengine.core.plugin.network.mod.ServerCustomPacket;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.IntIdentityList;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;

public record ServerboundEnableClientCustomBlockPacket(int vanillaSize, int currentSize) implements ServerCustomPacket {
    public static final Key ID = Key.ce("enable_client_custom_block");
    public static final NetworkCodec<FriendlyByteBuf, ServerboundEnableClientCustomBlockPacket> CODEC = ServerCustomPacket.codec(
            (packet, buf) -> {
                NetworkCodecs.VAR_INTEGER.encode(buf, packet.vanillaSize);
                NetworkCodecs.VAR_INTEGER.encode(buf, packet.currentSize);
            },
            buf -> new ServerboundEnableClientCustomBlockPacket(
                    NetworkCodecs.VAR_INTEGER.decode(buf),
                    NetworkCodecs.VAR_INTEGER.decode(buf)
            )
    );

    @Override
    public void handle(NetWorkUser user, ByteBufPacketEvent event) {
        if (user.clientCustomBlockEnabled()) return; // 防止滥用
        int vanillaBlockRegistrySize = CraftEngine.instance().blockManager().vanillaBlockStateCount();
        if (this.vanillaSize != vanillaBlockRegistrySize) {
            user.kick(Component.translatable(
                    "disconnect.craftengine.vanilla_block_registry_mismatch",
                    TranslationArgument.numeric(this.vanillaSize),
                    TranslationArgument.numeric(vanillaBlockRegistrySize)
            ));
            return;
        }
        int serverBlockRegistrySize = CraftEngine.instance().blockManager().currentBlockRegistrySize();
        if (this.currentSize < serverBlockRegistrySize) {
            user.kick(Component.translatable(
                    "disconnect.craftengine.current_block_registry_mismatch",
                    TranslationArgument.numeric(this.currentSize),
                    TranslationArgument.numeric(serverBlockRegistrySize)
            ));
            return;
        }
        user.setClientCustomBlock(true);
        user.setClientBlockList(new IntIdentityList(this.currentSize));
        user.sendCustomPackets(CraftEngine.instance().blockManager().cachedClientVisualBlockStatesPackets());
        if (!VersionHelper.isOrAbove1_20_2) {
            // 因为旧版本没有配置阶段需要重新发送区块
            CraftEngine.instance().scheduler().platform().run(user::resendChunks, null, (Player) user);
        }
    }

    @Override
    public Key id() {
        return ID;
    }

    @Override
    public NetworkCodec<FriendlyByteBuf, ServerboundEnableClientCustomBlockPacket> codec() {
        return CODEC;
    }
}
