package org.huntercore.api.network;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only connection metadata exposed by HunterCore's login and game connections.
 */
public interface HunterConnectionInfo {

    @NotNull HunterConnectionSource hunterConnectionSource();

    @NotNull String hunterProxyId();

    @NotNull String hunterNetworkId();

    boolean hunterOnlineAuthenticated();
}
