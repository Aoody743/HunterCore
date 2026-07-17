package org.huntercore.plugins.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal strict parser for the string-only JSON request bodies accepted by the web panel.
 */
final class WebJsonObjectParser {
    private WebJsonObjectParser() {
    }

    static Map<String, String> parse(final String json) {
        if (json == null) {
            return Map.of();
        }
        final Map<String, String> values = new HashMap<>();
        int index = 0;
        while (index < json.length()) {
            final int keyStart = json.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            final int keyEnd = findStringEnd(json, keyStart + 1);
            if (keyEnd < 0) {
                return Map.of();
            }
            final String key = unescape(json.substring(keyStart + 1, keyEnd));
            if (key == null) {
                return Map.of();
            }
            final int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                return Map.of();
            }
            final int valueStart = json.indexOf('"', colon + 1);
            if (valueStart < 0) {
                index = colon + 1;
                continue;
            }
            final int valueEnd = findStringEnd(json, valueStart + 1);
            if (valueEnd < 0) {
                return Map.of();
            }
            final String value = unescape(json.substring(valueStart + 1, valueEnd));
            if (value == null) {
                return Map.of();
            }
            values.put(key, value);
            index = valueEnd + 1;
        }
        return values;
    }

    private static int findStringEnd(final String json, final int start) {
        boolean escaped = false;
        for (int index = start; index < json.length(); index++) {
            final char character = json.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return index;
            }
        }
        return -1;
    }

    private static String unescape(final String value) {
        final StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character != '\\') {
                decoded.append(character);
                continue;
            }
            if (++index >= value.length()) {
                return null;
            }
            switch (value.charAt(index)) {
                case '"' -> decoded.append('"');
                case '\\' -> decoded.append('\\');
                case '/' -> decoded.append('/');
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) {
                        return null;
                    }
                    final String hexadecimal = value.substring(index + 1, index + 5);
                    try {
                        decoded.append((char) Integer.parseInt(hexadecimal, 16));
                    } catch (final NumberFormatException ex) {
                        return null;
                    }
                    index += 4;
                }
                default -> {
                    return null;
                }
            }
        }
        return decoded.toString();
    }
}
