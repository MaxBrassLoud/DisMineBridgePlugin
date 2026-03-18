package de.MaxBrassLoud.disMineBridge.features.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import de.MaxBrassLoud.disMineBridge.DisMineBridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Embedded HTTP server hosted directly by the plugin.
 * Port is configurable via config.yml → web.port (default: 8080)
 */
public class WebServer {

    private final DisMineBridge plugin;
    private HttpServer server;
    private final int port;

    public WebServer(DisMineBridge plugin) {
        this.plugin = plugin;
        this.port = plugin.getConfig().getInt("web.port", 8080);
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            WebApiHandler apiHandler = new WebApiHandler(plugin);

            // ── Frontend ──────────────────────────────────────
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                // OPTIONS-Preflight auch für die Root erlauben
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    addCorsHeaders(exchange);
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                if (path.equals("/") || path.equals("/index.html")) {
                    serveFrontend(exchange);
                } else {
                    send404(exchange);
                }
            });

            // ── Auth ──────────────────────────────────────────
            server.createContext("/api/auth/discord",  ex -> apiHandler.handleDiscordAuth(ex));
            server.createContext("/api/auth/callback", ex -> apiHandler.handleDiscordCallback(ex));
            server.createContext("/api/auth/me",       ex -> apiHandler.handleMe(ex));
            server.createContext("/api/auth/logout",   ex -> apiHandler.handleLogout(ex));

            // ── Players ───────────────────────────────────────
            server.createContext("/api/players",       ex -> apiHandler.handlePlayers(ex));
            server.createContext("/api/player/",       ex -> apiHandler.handlePlayerDetail(ex));
            server.createContext("/api/inventory/",    ex -> apiHandler.handleInventory(ex));

            // ── Actions ───────────────────────────────────────
            server.createContext("/api/action/ban",       ex -> apiHandler.handleBan(ex));
            server.createContext("/api/action/unban",     ex -> apiHandler.handleUnban(ex));
            server.createContext("/api/action/kick",      ex -> apiHandler.handleKick(ex));
            server.createContext("/api/action/mute",      ex -> apiHandler.handleMute(ex));
            server.createContext("/api/action/unmute",    ex -> apiHandler.handleUnmute(ex));
            server.createContext("/api/action/warn",      ex -> apiHandler.handleWarn(ex));
            server.createContext("/api/action/heal",      ex -> apiHandler.handleHeal(ex));
            server.createContext("/api/action/feed",      ex -> apiHandler.handleFeed(ex));
            server.createContext("/api/action/starve",    ex -> apiHandler.handleStarve(ex));
            server.createContext("/api/action/kill",      ex -> apiHandler.handleKillPlayer(ex));
            server.createContext("/api/action/vanish",    ex -> apiHandler.handleVanish(ex));
            server.createContext("/api/action/adminmode", ex -> apiHandler.handleAdminMode(ex));

            server.start();
            plugin.getLogger().info("[WebServer] Gestartet auf Port " + port
                    + " → http://localhost:" + port);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[WebServer] Fehler beim Starten auf Port " + port + ": " + e.getMessage(), e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("[WebServer] Gestoppt.");
        }
    }

    private void serveFrontend(HttpExchange exchange) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/web/index.html")) {
            if (is == null) {
                String error = "Frontend nicht gefunden. Bitte plugin neu laden.";
                byte[] bytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
                return;
            }
            byte[] bytes = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void send404(HttpExchange exchange) throws IOException {
        byte[] body = "Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(404, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",      "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods",     "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers",     "Content-Type, Cookie");
        ex.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
    }

    public int getPort() { return port; }
}