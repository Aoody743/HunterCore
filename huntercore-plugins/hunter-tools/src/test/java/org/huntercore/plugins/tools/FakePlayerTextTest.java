package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class FakePlayerTextTest {
    @Test
    void tokenizesWhitespaceAndCommas() {
        assertEquals(List.of("move", "1", "-2", "jump"), FakePlayerText.tokens(" move, 1   -2, jump "));
        assertEquals(List.of(), FakePlayerText.tokens(" , \t, "));
        assertEquals(List.of(), FakePlayerText.tokens(null));
    }

    @Test
    void sanitizesChatForSingleLineUseAndLengthLimit() {
        assertEquals("hello  world", FakePlayerText.sanitizeChat(" \nhello\r world\n "));
        assertEquals("", FakePlayerText.sanitizeChat(null));

        final String longMessage = "a".repeat(159) + " tail";
        assertEquals("a".repeat(159), FakePlayerText.sanitizeChat(longMessage));
    }

    @Test
    void detectsCommonAiMetaTextCaseInsensitively() {
        assertTrue(FakePlayerText.looksLikeAiMetaText("We Need To plan before answering"));
        assertTrue(FakePlayerText.looksLikeAiMetaText("RECENT CHAT: player said hello"));
        assertFalse(FakePlayerText.looksLikeAiMetaText("Let'sgo build a house"));
        assertFalse(FakePlayerText.looksLikeAiMetaText("Build a house near spawn"));
    }

    @Test
    void truncatesPlainTextAfterNormalizingLineBreaks() {
        assertEquals("hello world", FakePlayerText.truncatePlain(" hello\nworld ", 20));
        assertEquals("hello...", FakePlayerText.truncatePlain(" hello world ", 5));
        assertEquals("", FakePlayerText.truncatePlain(null, 5));
    }

    @Test
    void matchesAndRemovesOnlyTheFirstNameCaseInsensitively() {
        assertTrue(FakePlayerText.containsName("Ava, ask ava to follow", " ava "));
        assertFalse(FakePlayerText.containsName("Ava is here", ""));
        assertFalse(FakePlayerText.containsName(null, "Ava"));
        assertEquals(", ask ava to follow", FakePlayerText.removeFirstName("Ava, ask ava to follow", "ava"));
        assertEquals("no target", FakePlayerText.removeFirstName("no target", "ava"));
    }

    @Test
    void clampsAndFormatsNumbersIndependentlyOfDefaultLocale() {
        assertEquals(-1.0D, FakePlayerText.clamp(-2.0D, -1.0D, 1.0D));
        assertEquals(0.25D, FakePlayerText.clamp(0.25D, -1.0D, 1.0D));
        assertEquals(1.0D, FakePlayerText.clamp(2.0D, -1.0D, 1.0D));

        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.35", FakePlayerText.format(12.345D));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void reportsDeepestErrorAndBoundsItsLength() {
        final IllegalArgumentException root = new IllegalArgumentException("root cause");
        assertEquals("root cause", FakePlayerText.cleanError(new IllegalStateException("wrapper", root)));
        assertEquals("NullPointerException", FakePlayerText.cleanError(new NullPointerException()));
        assertEquals("x".repeat(160) + "...", FakePlayerText.cleanError(new RuntimeException("x".repeat(161))));
    }
}
