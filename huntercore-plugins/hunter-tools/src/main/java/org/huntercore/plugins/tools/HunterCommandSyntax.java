package org.huntercore.plugins.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class HunterCommandSyntax {
    private HunterCommandSyntax() {
    }

    static Boolean parseToggle(final String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "on", "enable", "enabled", "true", "yes" -> Boolean.TRUE;
            case "off", "disable", "disabled", "false", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    static boolean validCpuMode(final String input) {
        if (input == null) {
            return false;
        }
        final String normalized = input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return normalized.equals("single-thread")
            || normalized.equals("high-clock")
            || normalized.equals("high-core")
            || normalized.equals("multi-thread")
            || normalized.equals("single")
            || normalized.equals("multi")
            || normalized.equals("stable")
            || normalized.equals("performance")
            || normalized.equals("clock")
            || normalized.equals("core")
            || normalized.equals("balanced");
    }

    static String normalizeCpuMode(final String input) {
        final String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "high-clock", "clock" -> "high-clock";
            case "high-core", "core" -> "high-core";
            case "multi-thread", "multi", "performance" -> "multi-thread";
            default -> "single-thread";
        };
    }

    static List<String> matching(final String prefix, final Collection<String> values) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    static String normalizeWebCommand(final String command) {
        return command.replaceFirst("^/+", "").trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    static String commandRoot(final String message) {
        final String root = normalizeWebCommand(message);
        final int namespace = root.indexOf(':');
        return namespace >= 0 && namespace + 1 < root.length() ? root.substring(namespace + 1) : root;
    }

    static String[] commandArguments(final String message) {
        final String command = message.replaceFirst("^/+", "").trim();
        final int space = command.indexOf(' ');
        if (space < 0 || space + 1 >= command.length()) {
            return new String[0];
        }
        final String arguments = command.substring(space + 1).trim();
        return arguments.isBlank() ? new String[0] : arguments.split("\\s+");
    }

    static boolean isHelp(final String value) {
        final String normalized = normalizeId(value);
        return normalized.equals("help") || normalized.equals("?") || normalized.equals("usage");
    }

    static String helpTopic(final String command) {
        return switch (normalizeId(command)) {
            case "htps" -> "tps";
            case "gms", "gmc", "gma", "gmsp" -> "gm";
            case "bc" -> "broadcast";
            case "cc" -> "clearchat";
            case "workbench", "wb" -> "craft";
            case "ec" -> "enderchest";
            case "disposal" -> "trash";
            case "player" -> "player";
            case "npc" -> "npc";
            default -> normalizeId(command);
        };
    }

    private static String normalizeId(final String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
