package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HunterCommandSyntaxTest {
    @Test
    void parsesToggleAliases() {
        assertEquals(Boolean.TRUE, HunterCommandSyntax.parseToggle("ENABLED"));
        assertEquals(Boolean.FALSE, HunterCommandSyntax.parseToggle("no"));
        assertNull(HunterCommandSyntax.parseToggle("sometimes"));
    }

    @Test
    void normalizesCpuModeAliases() {
        assertTrue(HunterCommandSyntax.validCpuMode("HIGH_CORE"));
        assertTrue(HunterCommandSyntax.validCpuMode("performance"));
        assertFalse(HunterCommandSyntax.validCpuMode("turbo"));
        assertEquals("high-core", HunterCommandSyntax.normalizeCpuMode("core"));
        assertEquals("multi-thread", HunterCommandSyntax.normalizeCpuMode("performance"));
        assertEquals("single-thread", HunterCommandSyntax.normalizeCpuMode("balanced"));
    }

    @Test
    void matchesCompletionsCaseInsensitivelyInSourceOrder() {
        assertEquals(
            List.of("Status", "stop", "start"),
            HunterCommandSyntax.matching("st", List.of("Status", "stop", "start", "reload"))
        );
    }

    @Test
    void extractsNamespacedCommandRootAndArguments() {
        assertEquals("give", HunterCommandSyntax.commandRoot("/minecraft:give Steve stone 2"));
        assertEquals("say", HunterCommandSyntax.normalizeWebCommand("///Say hello"));
        assertArrayEquals(
            new String[] {"Steve", "stone", "2"},
            HunterCommandSyntax.commandArguments("/minecraft:give Steve   stone 2")
        );
        assertArrayEquals(new String[0], HunterCommandSyntax.commandArguments("/help"));
    }

    @Test
    void mapsHelpAliases() {
        assertTrue(HunterCommandSyntax.isHelp("?"));
        assertEquals("gm", HunterCommandSyntax.helpTopic("gmc"));
        assertEquals("enderchest", HunterCommandSyntax.helpTopic("ec"));
        assertEquals("custom", HunterCommandSyntax.helpTopic("custom"));
    }
}
