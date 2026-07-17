package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HunterWebPanelManagerJsonTest {
    @Test
    void preservesLiteralBackslashSequencesInsteadOfConvertingThemTwice() {
        final Map<String, String> parsed = WebJsonObjectParser.parse(
            "{\"username\":\"Player\",\"password\":\"literal\\\\nvalue\"}"
        );

        assertEquals("Player", parsed.get("username"));
        assertEquals("literal\\nvalue", parsed.get("password"));
    }

    @Test
    void decodesStandardEscapesOnce() {
        final Map<String, String> parsed = WebJsonObjectParser.parse(
            "{\"value\":\"quote: \\\"; slash: \\\\; unicode: \\u4f60\"}"
        );

        assertEquals("quote: \"; slash: \\; unicode: 你", parsed.get("value"));
    }

    @Test
    void rejectsMalformedEscapes() {
        assertTrue(WebJsonObjectParser.parse("{\"value\":\"bad\\q\"}").isEmpty());
    }
}
