package net.momirealms.craftengine.core.plugin.network.mod;

import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.core.plugin.network.codec.NetworkCodec;
import net.momirealms.craftengine.core.util.FriendlyByteBuf;
import net.momirealms.craftengine.core.util.Key;

import java.util.function.BiPredicate;

public record ClientCustomPacketType<T extends ClientCustomPacket>(Key id, NetworkCodec<FriendlyByteBuf, T> codec, BiPredicate<NetWorkUser, Key> permissionChecker, boolean inServerHandle) {

    public boolean checkPermission(NetWorkUser user) {
        return this.permissionChecker.test(user, id);
    }

}
