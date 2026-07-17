package net.momirealms.craftengine.core.pack;

import java.util.Map;

public final class CachedConfigFile {
    private final Map<String, Object> config;
    private final long lastModified;
    private final long size;
    private final Pack pack;

    public CachedConfigFile(Map<String, Object> config, Pack pack, long lastModified, long size) {
        this.config = config;
        this.size = size;
        this.lastModified = lastModified;
        this.pack = pack;
    }

    public Pack pack() {
        return this.pack;
    }

    public Map<String, Object> config() {
        return this.config;
    }

    public long lastModified() {
        return this.lastModified;
    }

    public long size() {
        return this.size;
    }
}
