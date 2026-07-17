package org.huntercore.network;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public final class HunterTrustedAddressMatcher {
    private final List<Subnet> subnets;

    private HunterTrustedAddressMatcher(final List<Subnet> subnets) {
        this.subnets = List.copyOf(subnets);
    }

    public static HunterTrustedAddressMatcher of(final List<String> values) {
        final List<Subnet> subnets = new ArrayList<>();
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                subnets.add(Subnet.parse(value.trim()));
            }
        }
        return new HunterTrustedAddressMatcher(subnets);
    }

    public boolean matches(final SocketAddress address) {
        if (!(address instanceof final InetSocketAddress inet) || inet.getAddress() == null) {
            return false;
        }
        return this.subnets.stream().anyMatch(subnet -> subnet.matches(inet.getAddress()));
    }

    public boolean empty() {
        return this.subnets.isEmpty();
    }

    private record Subnet(byte[] network, int prefixBits) {
        private static Subnet parse(final String value) {
            final int slash = value.indexOf('/');
            final String literal = slash < 0 ? value : value.substring(0, slash);
            if (!InetAddresses.isInetAddress(literal)) {
                throw new IllegalArgumentException("Trusted proxy address must be an IP literal or CIDR: " + value);
            }
            final byte[] bytes = InetAddresses.forString(literal).getAddress();
            final int maximum = bytes.length * 8;
            final int prefix = slash < 0 ? maximum : Integer.parseInt(value.substring(slash + 1));
            if (prefix < 0 || prefix > maximum) {
                throw new IllegalArgumentException("Invalid CIDR prefix in " + value);
            }
            final byte[] normalized = bytes.clone();
            for (int bit = prefix; bit < maximum; bit++) {
                normalized[bit / 8] &= (byte) ~(1 << (7 - bit % 8));
            }
            return new Subnet(normalized, prefix);
        }

        private boolean matches(final InetAddress address) {
            final byte[] candidate = address.getAddress();
            if (candidate.length != this.network.length) {
                return false;
            }
            for (int bit = 0; bit < this.prefixBits; bit++) {
                final int mask = 1 << (7 - bit % 8);
                if ((candidate[bit / 8] & mask) != (this.network[bit / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
