package com.createstats.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// client side only, works on servers you don't own
@OnlyIn(Dist.CLIENT)
public final class LocalWebServer {
    private static final LocalWebServer INSTANCE = new LocalWebServer();

    private static final int THREADS = 2;

    private static final int STOP_GRACE = 1;

    public static final String SNAPSHOT_PATH = "/api/snapshot";

    private HttpServer server;
    private ThreadPoolExecutor pool;
    private String token = "";
    private int port;

    private LocalWebServer() {
    }

    public static LocalWebServer get() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return this.server != null;
    }

    public int port() {
        return this.port;
    }

    public String url() {
        return isRunning()
                ? "http://" + WebSecurity.BIND_HOST + ":" + this.port + "/?t=" + this.token
                : "";
    }

    public synchronized boolean start() {
        if (isRunning()) {
            return true;
        }
        this.token = WebSecurity.newToken();

        for (int i = 0; i < WebSecurity.PORT_TRIES; i++) {
            final int candidate = WebSecurity.PORT_BASE + i;
            try {
                final HttpServer created =
                        HttpServer.create(WebSecurity.bindAddress(candidate), 0);
                this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREADS, factory());
                created.setExecutor(this.pool);
                created.createContext("/", this::handle);
                created.start();

                this.server = created;
                this.port = candidate;
                return true;
            } catch (final BindException taken) {
            } catch (final IOException failed) {
                stop();
                return false;
            }
        }
        this.token = "";
        return false;
    }

    private static ThreadFactory factory() {
        final AtomicInteger n = new AtomicInteger();
        return runnable -> {
            final Thread t = new Thread(runnable, "create-stats-web-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public synchronized void stop() {
        if (this.server != null) {
            this.server.stop(STOP_GRACE);
            this.server = null;
        }
        if (this.pool != null) {
            this.pool.shutdownNow();
            try {
                this.pool.awaitTermination(STOP_GRACE, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.pool = null;
        }

        this.token = "";
        this.port = 0;

        WebFeed.clear();
    }

    public synchronized boolean toggle() {
        if (isRunning()) {
            stop();
            return false;
        }
        return start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            WebSecurity.harden(exchange);

            final String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                respond(exchange, 405, "text/plain; charset=utf-8", "Read only.");
                return;
            }

            if (!WebSecurity.hostAllowed(exchange, this.port)) {
                respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden.");
                return;
            }

            if (WebSecurity.crossOriginRequest(exchange)) {
                respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden.");
                return;
            }

            if (!WebSecurity.constantTimeEquals(tokenOf(exchange), this.token)
                    || this.token.isEmpty()) {
                respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden.");
                return;
            }

            final String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                respond(exchange, 200, "text/html; charset=utf-8", WebDashboard.page());
            } else if (SNAPSHOT_PATH.equals(path)) {
                respond(exchange, 200, "application/json; charset=utf-8", WebFeed.json());
            } else {
                respond(exchange, 404, "text/plain; charset=utf-8", "Not found.");
            }
        } catch (final RuntimeException e) {
            respond(exchange, 500, "text/plain; charset=utf-8", "Error.");
        } finally {
            exchange.close();
        }
    }

    private static String tokenOf(HttpExchange exchange) {
        final String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return "";
        }
        for (final String part : query.split("&")) {
            if (part.startsWith("t=")) {
                return part.substring(2);
            }
        }
        return "";
    }

    private static void respond(HttpExchange exchange, int status, String type, String body)
            throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);

        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
