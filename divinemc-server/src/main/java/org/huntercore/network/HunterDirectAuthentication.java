package org.huntercore.network;

import java.util.Locale;

public enum HunterDirectAuthentication {
    OFFLINE,
    ONLINE;

    public static HunterDirectAuthentication parse(final String value) {
        if (value == null || value.isBlank()) {
            return OFFLINE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "online", "premium", "mojang" -> ONLINE;
            case "offline", "hunterauth", "password" -> OFFLINE;
            default -> throw new IllegalArgumentException("Unsupported direct authentication mode: " + value);
        };
    }
}
