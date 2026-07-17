package org.huntercore.api.network;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public record HunterRemotePlayer(
    @NotNull UUID uniqueId,
    @NotNull String name,
    @NotNull String server
) {
}
