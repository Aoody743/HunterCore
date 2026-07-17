package org.huntercore.plugins.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FakePlayerText {
    private FakePlayerText() {
    }

    static List<String> tokens(final String args) {
        final List<String> values = new ArrayList<>();
        if (args == null) {
            return values;
        }
        for (final String token : args.replace(',', ' ').split("\\s+")) {
            final String trimmed = token.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    static String sanitizeChat(final String message) {
        if (message == null) {
            return "";
        }
        final String sanitized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() > 160 ? sanitized.substring(0, 160).trim() : sanitized;
    }

    static boolean looksLikeAiMetaText(final String message) {
        final String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("we need to")
            || lower.contains("the player wants")
            || lower.contains("the player's request")
            || lower.contains("respond to recent chat")
            || lower.contains("recent chat:")
            || lower.contains("translates to")
            || lower.contains("chain-of-thought")
            || lower.contains("i need to")
            || lower.contains("we should")
            || lower.contains("let's ");
    }

    static String truncatePlain(final String value, final int maxLength) {
        if (value == null) {
            return "";
        }
        final String trimmed = value.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength).trim() + "..." : trimmed;
    }

    static boolean containsName(final String message, final String name) {
        if (message == null || name == null || name.isBlank()) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT));
    }

    static String removeFirstName(final String message, final String name) {
        final String lower = message.toLowerCase(Locale.ROOT);
        final String needle = name.trim().toLowerCase(Locale.ROOT);
        final int index = lower.indexOf(needle);
        if (index < 0) {
            return message;
        }
        return (message.substring(0, index) + " " + message.substring(index + name.trim().length()))
            .replaceAll("\\s+", " ")
            .trim();
    }

    static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    static String format(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String cleanError(final Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        final String message = current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }
}
