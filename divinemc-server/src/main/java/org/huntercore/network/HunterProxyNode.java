package org.huntercore.network;

import java.net.SocketAddress;
import java.util.List;

public record HunterProxyNode(
    String id,
    HunterProxyType type,
    HunterTrustedAddressMatcher trustedAddresses,
    String secret,
    boolean onlineAuthenticated,
    String networkId
) {
    public HunterProxyNode {
        id = id == null ? "" : id.trim();
        secret = secret == null ? "" : secret;
        networkId = networkId == null || networkId.isBlank() ? "default" : networkId.trim();
    }

    public static HunterProxyNode legacy(final String id, final HunterProxyType type, final String secret, final boolean onlineAuthenticated) {
        return new HunterProxyNode(id, type, HunterTrustedAddressMatcher.of(List.of()), secret, onlineAuthenticated, "default");
    }

    public boolean trusts(final SocketAddress address) {
        return this.trustedAddresses.matches(address);
    }
}
