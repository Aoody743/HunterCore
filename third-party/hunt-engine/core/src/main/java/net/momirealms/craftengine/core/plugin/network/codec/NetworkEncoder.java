package net.momirealms.craftengine.core.plugin.network.codec;

public interface NetworkEncoder<O, T> {
    void encode(O out, T value);
}
