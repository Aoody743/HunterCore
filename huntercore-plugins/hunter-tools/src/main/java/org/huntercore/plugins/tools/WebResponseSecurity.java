package org.huntercore.plugins.tools;

import com.sun.net.httpserver.Headers;

final class WebResponseSecurity {
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; frame-src http: https:; connect-src 'self' http: https: ws: wss:";

    private WebResponseSecurity() {
    }

    static void apply(final Headers headers, final boolean trustedHttps) {
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        if (trustedHttps) {
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        } else {
            headers.remove("Strict-Transport-Security");
        }
    }

    static String sessionCookie(
        final String name,
        final String value,
        final long maxAgeSeconds,
        final boolean secure
    ) {
        return name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds
            + (secure ? "; Secure" : "");
    }
}
