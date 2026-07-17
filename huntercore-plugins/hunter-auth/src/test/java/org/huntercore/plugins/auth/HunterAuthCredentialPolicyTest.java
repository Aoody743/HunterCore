package org.huntercore.plugins.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HunterAuthCredentialPolicyTest {
    @Test
    void acceptsCredentialsThatCanBeReplayedAcrossGameAndWeb() {
        assertTrue(HunterAuthCredentialPolicy.replayable("Correct-password_42!"));
    }

    @Test
    void rejectsWhitespaceAndControlCharacters() {
        for (final String value : new String[] {null, "", " leading", "trailing ", "has space", "line\n", "tab\t", "nul\u0000"}) {
            assertFalse(HunterAuthCredentialPolicy.replayable(value));
        }
    }
}
