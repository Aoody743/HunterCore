package net.momirealms.craftengine.core.pack.host;

import java.util.UUID;

public record ResourcePackDownloadData(String url, UUID uuid, String sha1) {

    public static ResourcePackDownloadData of(String url, UUID uuid, String sha1) {
        return new ResourcePackDownloadData(url, uuid, sha1);
    }
}
