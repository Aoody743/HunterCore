package org.huntercore.network.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HunterNetworkProtocolTest {
    @Test
    void roundTripsSnapshot() throws Exception {
        final byte[] encoded = HunterNetworkProtocol.snapshot("main", "velocity-a", List.of(
            new HunterNetworkProtocol.PlayerEntry("Alice", "survival", "00000000-0000-0000-0000-000000000001")
        ));
        assertEquals(
            new HunterNetworkProtocol.Snapshot("main", "velocity-a", List.of(
                new HunterNetworkProtocol.PlayerEntry("Alice", "survival", "00000000-0000-0000-0000-000000000001")
            )),
            HunterNetworkProtocol.decode(encoded)
        );
    }

    @Test
    void roundTripsChat() throws Exception {
        final byte[] encoded = HunterNetworkProtocol.chat("main", "bungee-a", "Alice", "survival", "hello");
        assertEquals(
            new HunterNetworkProtocol.ChatMessage("main", "bungee-a", "Alice", "survival", "hello"),
            HunterNetworkProtocol.decode(encoded)
        );
    }

    @Test
    void rejectsUnknownVersion() {
        assertThrows(IOException.class, () -> HunterNetworkProtocol.decode(new byte[] {99, HunterNetworkProtocol.CHAT}));
    }
}
