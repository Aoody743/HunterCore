package org.huntercore.network;

import java.util.Locale;

public enum HunterProxyType {
    BUNGEECORD,
    VELOCITY;

    public static HunterProxyType parse(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Proxy type is required");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "bungee", "bungeecord", "waterfall" -> BUNGEECORD;
            case "velocity" -> VELOCITY;
            default -> throw new IllegalArgumentException("Unsupported proxy type: " + value);
        };
    }
}
