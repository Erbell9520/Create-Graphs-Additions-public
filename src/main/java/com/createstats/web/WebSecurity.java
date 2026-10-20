package com.createstats.web;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Base64;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public final class WebSecurity {
    private WebSecurity() {
    }

    public static final String BIND_HOST = "127.0.0.1";

    public static final int PORT_BASE = 47820;

    public static final int PORT_TRIES = 12;

    private static final int TOKEN_BYTES = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String newToken() {
        final byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static InetSocketAddress bindAddress(int port) {
        return new InetSocketAddress(BIND_HOST, port);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        final byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    public static boolean hostAllowed(HttpExchange exchange, int port) {
        final String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        final String expectedIp = BIND_HOST + ":" + port;
        final String expectedName = "localhost:" + port;
        return host.equals(expectedIp) || host.equals(expectedName);
    }

    public static boolean crossOriginRequest(HttpExchange exchange) {
        final String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin != null && !origin.isEmpty();
    }

    public static void harden(HttpExchange exchange) {
        final Headers h = exchange.getResponseHeaders();
        h.set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
                        + "img-src data:; connect-src 'self'; base-uri 'none'; form-action 'none'; "
                        + "frame-ancestors 'none'");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Cache-Control", "no-store");
    }
}
