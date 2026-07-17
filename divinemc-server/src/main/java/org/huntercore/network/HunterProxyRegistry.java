package org.huntercore.network;

import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

public final class HunterProxyRegistry {
    public static final String FILE_NAME = "proxies.yml";
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static volatile Snapshot snapshot = Snapshot.empty();

    private HunterProxyRegistry() {
    }

    public static synchronized void loadOrCreate(final Path path) {
        try {
            Files.createDirectories(path.getParent());
            final YamlConfiguration config = new YamlConfiguration();
            if (Files.isRegularFile(path)) {
                config.load(path.toFile());
            }
            boolean changed = false;
            changed |= setDefault(config, "direct.authentication", "offline");
            changed |= setDefault(config, "direct.online-hostnames", List.of());
            changed |= setDefault(config, "server-id", "backend");
            changed |= setDefault(config, "tab-list.include-direct-for-proxy", true);
            changed |= setDefault(config, "chat.group-enabled", true);
            changed |= setDefault(config, "proxies", List.of());
            if (changed || !Files.exists(path)) {
                config.options().header("""
                    HunterCore hybrid direct/proxy ingress configuration.
                    Add any number of BungeeCord and Velocity nodes under proxies.
                    trusted-addresses accepts IP literals and CIDR ranges; DNS names are rejected.
                    Velocity nodes require the same forwarding secret configured on that proxy.
                    Example:
                      - id: velocity-main
                        type: velocity
                        trusted-addresses: [\"127.0.0.1/32\", \"::1/128\"]
                        secret: \"replace-with-forwarding-secret\"
                        online-authenticated: true
                        network: main
                    Direct authentication defaults to offline and is protected by HunterAuth.
                    Set direct.authentication to online, or add hostnames under direct.online-hostnames,
                    to require Mojang authentication for selected direct entry addresses.
                    """);
                config.save(path.toFile());
            }
            snapshot = parse(config);
            LOGGER.info("Loaded {} HunterCore proxy nodes from {}", snapshot.nodes.size(), path);
        } catch (final Exception ex) {
            LOGGER.error("Failed to load HunterCore proxy configuration {}; legacy proxy settings remain available", path, ex);
            snapshot = Snapshot.empty();
        }
    }

    public static List<HunterProxyNode> nodes(final HunterProxyType type) {
        return snapshot.nodes.stream().filter(node -> node.type() == type).toList();
    }

    public static Optional<HunterProxyNode> match(final HunterProxyType type, final SocketAddress rawAddress) {
        return snapshot.nodes.stream().filter(node -> node.type() == type && node.trusts(rawAddress)).findFirst();
    }

    public static boolean configured(final HunterProxyType type) {
        return snapshot.nodes.stream().anyMatch(node -> node.type() == type);
    }

    public static Optional<HunterProxyNode> verifyVelocity(final SocketAddress rawAddress, final byte[] signature, final byte[] data) {
        for (final HunterProxyNode node : snapshot.nodes) {
            if (node.type() == HunterProxyType.VELOCITY && node.trusts(rawAddress) && !node.secret().isBlank()
                && verifyHmac(node.secret(), signature, data)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public static HunterDirectAuthentication directAuthentication(final String hostname) {
        final String normalizedHost = normalizeHost(hostname);
        if (!normalizedHost.isEmpty() && snapshot.onlineHostnames.stream().anyMatch(host -> host.equalsIgnoreCase(normalizedHost))) {
            return HunterDirectAuthentication.ONLINE;
        }
        return snapshot.directAuthentication;
    }

    public static boolean includeDirectForProxy() {
        return snapshot.includeDirectForProxy;
    }

    public static boolean groupChatEnabled() {
        return snapshot.groupChatEnabled;
    }

    public static String serverId() {
        return snapshot.serverId;
    }

    public static boolean verifyHmac(final String secret, final byte[] signature, final byte[] data) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(signature, mac.doFinal(data));
        } catch (final InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Snapshot parse(final YamlConfiguration config) {
        final List<HunterProxyNode> nodes = new ArrayList<>();
        for (final Map<?, ?> raw : config.getMapList("proxies")) {
            try {
                final String id = value(raw, "id", "").trim();
                if (id.isEmpty()) {
                    throw new IllegalArgumentException("Proxy id is required");
                }
                final HunterProxyType type = HunterProxyType.parse(value(raw, "type", ""));
                final Object addressValue = raw.get("trusted-addresses");
                final List<String> addresses = addressValue instanceof final List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
                final HunterTrustedAddressMatcher matcher = HunterTrustedAddressMatcher.of(addresses);
                if (matcher.empty()) {
                    throw new IllegalArgumentException("Proxy " + id + " must declare trusted-addresses");
                }
                final String secret = value(raw, "secret", "");
                if (type == HunterProxyType.VELOCITY && secret.isBlank()) {
                    throw new IllegalArgumentException("Velocity proxy " + id + " requires a forwarding secret");
                }
                final boolean online = Boolean.parseBoolean(value(raw, "online-authenticated", "true"));
                nodes.add(new HunterProxyNode(id, type, matcher, secret, online, value(raw, "network", "default")));
            } catch (final RuntimeException ex) {
                LOGGER.error("Ignoring invalid HunterCore proxy node {}", raw, ex);
            }
        }
        final HunterDirectAuthentication direct = HunterDirectAuthentication.parse(config.getString("direct.authentication", "offline"));
        final List<String> hosts = config.getStringList("direct.online-hostnames").stream()
            .map(HunterProxyRegistry::normalizeHost)
            .filter(host -> !host.isEmpty())
            .toList();
        return new Snapshot(
            List.copyOf(nodes),
            direct,
            hosts,
            config.getBoolean("tab-list.include-direct-for-proxy", true),
            config.getBoolean("chat.group-enabled", true),
            value(config.getValues(false), "server-id", "backend")
        );
    }

    private static String value(final Map<?, ?> map, final String key, final String fallback) {
        final Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String normalizeHost(final String value) {
        if (value == null) {
            return "";
        }
        String host = value.trim().toLowerCase(Locale.ROOT);
        final int nul = host.indexOf('\0');
        if (nul >= 0) {
            host = host.substring(0, nul);
        }
        if (host.startsWith("[")) {
            final int close = host.indexOf(']');
            return close > 0 ? host.substring(1, close) : host;
        }
        final int colon = host.lastIndexOf(':');
        return colon > 0 && host.indexOf(':') == colon ? host.substring(0, colon) : host;
    }

    private static boolean setDefault(final YamlConfiguration config, final String path, final Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private record Snapshot(
        List<HunterProxyNode> nodes,
        HunterDirectAuthentication directAuthentication,
        List<String> onlineHostnames,
        boolean includeDirectForProxy,
        boolean groupChatEnabled,
        String serverId
    ) {
        private static Snapshot empty() {
            return new Snapshot(List.of(), HunterDirectAuthentication.OFFLINE, List.of(), true, true, "backend");
        }
    }
}
