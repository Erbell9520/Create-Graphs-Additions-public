package com.createstats.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class WebDashboard {
    private static final String RESOURCE = "/assets/create_stats/web/dashboard.html";

    // read once, it's in the jar
    private static volatile String cached;

    private WebDashboard() {
    }

    public static String page() {
        final String hit = cached;
        if (hit != null) {
            return hit;
        }
        final String loaded = load();
        cached = loaded;
        return loaded;
    }

    private static String load() {
        try (InputStream in = WebDashboard.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return fallback("The dashboard document is missing from this build.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException | RuntimeException failed) {
            return fallback("The dashboard document could not be read.");
        }
    }

    private static String fallback(String reason) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="referrer" content="no-referrer">
                <title>Stats Display Mk III</title>
                <style>
                  html, body { margin: 0; background: #06070a; color: #98a0ad;
                    font: 13px/1.7 system-ui, -apple-system, "Segoe UI", sans-serif; }
                  main { max-width: 34rem; margin: 18vh auto; padding: 0 1.5rem; }
                  h1 { color: #eceef2; font-size: 15px; letter-spacing: .02em; margin: 0 0 .4rem; }
                  h1 span { color: #ff5c90; }
                  p { margin: 0 0 .8rem; }
                </style>
                </head>
                <body>
                <main>
                  <h1>STATS DISPLAY <span>MK III</span></h1>
                  <p>""" + reason + """
                </p>
                  <p>The server itself is working &mdash; this page came from your own game client.
                  Switch the interface off and on again, or reinstall the mod jar.</p>
                </main>
                </body>
                </html>
                """;
    }
}
