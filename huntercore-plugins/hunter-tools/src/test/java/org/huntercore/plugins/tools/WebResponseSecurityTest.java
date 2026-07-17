package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

class WebResponseSecurityTest {
    @Test
    void localHttpCookieRemainsUsableByDefault() {
        final String cookie = WebResponseSecurity.sessionCookie("HCSESSION", "token", 60L, false);
        assertEquals("HCSESSION=token; Path=/; HttpOnly; SameSite=Lax; Max-Age=60", cookie);
    }

    @Test
    void trustedHttpsEnablesSecureCookieAndTransportPolicy() {
        final String cookie = WebResponseSecurity.sessionCookie("HCSESSION", "token", 60L, true);
        assertTrue(cookie.endsWith("; Secure"));

        final Headers headers = new Headers();
        WebResponseSecurity.apply(headers, true);
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertTrue(headers.getFirst("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertTrue(headers.containsKey("Strict-Transport-Security"));

        WebResponseSecurity.apply(headers, false);
        assertFalse(headers.containsKey("Strict-Transport-Security"));
    }
}
