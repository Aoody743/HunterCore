package net.momirealms.craftengine.bukkit.plugin.network;

public class CancelPacketException extends RuntimeException {

    public static final CancelPacketException INSTANCE = new CancelPacketException();
}