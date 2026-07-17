package org.huntercore.api.network;

/**
 * The verified ingress path used by a player connection.
 */
public enum HunterConnectionSource {
    UNKNOWN,
    DIRECT,
    BUNGEECORD,
    VELOCITY,
    INTERNAL;

    public boolean proxied() {
        return this == BUNGEECORD || this == VELOCITY;
    }
}
