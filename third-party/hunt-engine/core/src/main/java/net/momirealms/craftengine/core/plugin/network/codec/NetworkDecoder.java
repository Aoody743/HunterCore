package net.momirealms.craftengine.core.plugin.network.codec;

public interface NetworkDecoder<I, T> {
    T decode(I in);
}
