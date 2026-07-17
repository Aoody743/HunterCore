package net.momirealms.craftengine.core.plugin.network.codec;

@FunctionalInterface
public interface NetworkMemberEncoder<O, T> {
    void encode(T object, O object2);
}
