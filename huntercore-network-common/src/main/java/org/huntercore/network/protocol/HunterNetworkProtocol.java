package org.huntercore.network.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Stable, dependency-free wire format for proxy companions and HunterCore. */
public final class HunterNetworkProtocol {
    public static final String CHANNEL = "huntercore:network_v1";
    public static final int VERSION = 1;
    public static final byte SNAPSHOT = 1;
    public static final byte CHAT = 2;
    private static final int MAX_STRING = 256;
    private static final int MAX_PLAYERS = 10_000;

    private HunterNetworkProtocol() { }

    public record PlayerEntry(String name, String server, String uuid) { }
    public record Snapshot(String network, String source, List<PlayerEntry> players) { }
    public record ChatMessage(String network, String source, String sender, String server, String message) { }

    public static byte[] snapshot(String network, String source, List<PlayerEntry> players) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        try (var data = new DataOutputStream(out)) {
            data.writeByte(VERSION); data.writeByte(SNAPSHOT); writeString(data, network); writeString(data, source);
            data.writeInt(Math.min(players.size(), MAX_PLAYERS));
            for (int i = 0; i < players.size() && i < MAX_PLAYERS; i++) {
                var p = players.get(i); writeString(data, p.name()); writeString(data, p.server()); writeString(data, p.uuid());
            }
        }
        return out.toByteArray();
    }

    public static byte[] chat(String network, String source, String sender, String server, String message) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        try (var data = new DataOutputStream(out)) {
            data.writeByte(VERSION); data.writeByte(CHAT); writeString(data, network); writeString(data, source);
            writeString(data, sender); writeString(data, server); writeString(data, message);
        }
        return out.toByteArray();
    }

    public static Object decode(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported network protocol version");
            int type = in.readUnsignedByte(); String network = readString(in), source = readString(in);
            if (type == SNAPSHOT) {
                int count = in.readInt(); if (count < 0 || count > MAX_PLAYERS) throw new IOException("Invalid player count");
                var players = new ArrayList<PlayerEntry>(count);
                for (int i = 0; i < count; i++) players.add(new PlayerEntry(readString(in), readString(in), readString(in)));
                return new Snapshot(network, source, List.copyOf(players));
            }
            if (type == CHAT) return new ChatMessage(network, source, readString(in), readString(in), readString(in));
            throw new IOException("Unknown network message type: " + type);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING * 4) throw new IOException("String exceeds protocol limit");
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort(); if (length > MAX_STRING * 4) throw new IOException("String exceeds protocol limit");
        byte[] bytes = in.readNBytes(length); if (bytes.length != length) throw new IOException("Truncated message");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
