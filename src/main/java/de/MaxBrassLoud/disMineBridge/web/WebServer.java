package de.MaxBrassLoud.disMineBridge.web;

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

            // Serve the frontend HTML
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/") || path.equals("/index.html")) {
                    serveFrontend(exchange);
                } else {
                    send404(exchange);
                }
            });

            // API routes
            server.createContext("/api/auth/discord",   exchange -> apiHandler.handleDiscordAuth(exchange));
            server.createContext("/api/auth/callback",  exchange -> apiHandler.handleDiscordCallback(exchange));
            server.createContext("/api/auth/me",        exchange -> apiHandler.handleMe(exchange));
            server.createContext("/api/auth/logout",    exchange -> apiHandler.handleLogout(exchange));
            server.createContext("/api/players",        exchange -> apiHandler.handlePlayers(exchange));
            server.createContext("/api/player/",        exchange -> apiHandler.handlePlayerDetail(exchange));
            server.createContext("/api/action/ban",     exchange -> apiHandler.handleBan(exchange));
            server.createContext("/api/action/unban",   exchange -> apiHandler.handleUnban(exchange));
            server.createContext("/api/action/kick",    exchange -> apiHandler.handleKick(exchange));
            server.createContext("/api/action/mute",    exchange -> apiHandler.handleMute(exchange));
            server.createContext("/api/action/unmute",  exchange -> apiHandler.handleUnmute(exchange));
            server.createContext("/api/action/warn",    exchange -> apiHandler.handleWarn(exchange));
            server.createContext("/api/action/heal",    exchange -> apiHandler.handleHeal(exchange));
            server.createContext("/api/action/feed",    exchange -> apiHandler.handleFeed(exchange));
            server.createContext("/api/action/starve",  exchange -> apiHandler.handleStarve(exchange));
            server.createContext("/api/action/kill",    exchange -> apiHandler.handleKillPlayer(exchange));
            server.createContext("/api/action/vanish",  exchange -> apiHandler.handleVanish(exchange));
            server.createContext("/api/action/adminmode", exchange -> apiHandler.handleAdminMode(exchange));
            server.createContext("/api/inventory/",     exchange -> apiHandler.handleInventory(exchange));

            server.start();
            plugin.getLogger().info("[WebServer] Gestartet auf Port " + port + " → http://localhost:" + port);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[WebServer] Fehler beim Starten: " + e.getMessage(), e);
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
                exchange.sendResponseHeaders(500, error.length());
                exchange.getResponseBody().write(error.getBytes(StandardCharsets.UTF_8));
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
        exchange.sendResponseHeaders(404, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    public int getPort() {
        return port;
    }
}