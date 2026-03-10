package de.MaxBrassLoud.disMineBridge.web;

import com.sun.net.httpserver.HttpExchange;
import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.util.OfflineInventoryStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles all /api/* endpoints for the DisMineBridge web dashboard.
 */
public class WebApiHandler {

    private final DisMineBridge plugin;
    private final WebSessionManager sessions;
    private final WebPermissionManager permissions;
    private final HttpClient http = HttpClient.newHttpClient();

    // ─── Discord OAuth config (from config.yml) ───
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public WebApiHandler(DisMineBridge plugin) {
        this.plugin = plugin;
        this.sessions = plugin.getWebSessionManager();
        this.permissions = plugin.getWebPermissionManager();
        this.clientId = plugin.getConfig().getString("web.discord.client-id", "");
        this.clientSecret = plugin.getConfig().getString("web.discord.client-secret", "");
        this.redirectUri = plugin.getConfig().getString("web.discord.redirect-uri", "http://localhost:8080/api/auth/callback");
    }

    // ═══════════════════════════════════════════════════
    //  AUTH ENDPOINTS
    // ═══════════════════════════════════════════════════

    /** GET /api/auth/discord → redirect to Discord OAuth */
    public void handleDiscordAuth(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send405(ex); return; }

        String state = sessions.generateState();
        String url = "https://discord.com/oauth2/authorize"
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=identify"
                + "&state=" + state;

        ex.getResponseHeaders().set("Location", url);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    /** GET /api/auth/callback?code=...&state=... */
    public void handleDiscordCallback(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send405(ex); return; }

        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        String code = params.get("code");
        String state = params.get("state");

        if (code == null || !sessions.consumeState(state)) {
            sendJson(ex, 400, "{\"error\":\"Ungültiger OAuth-State\"}");
            return;
        }

        try {
            String tokenBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

            HttpRequest tokenReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                    .build();

            HttpResponse<String> tokenRes = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenData = parseJsonObject(tokenRes.body());
            String accessToken = (String) tokenData.get("access_token");

            if (accessToken == null) {
                sendJson(ex, 401, "{\"error\":\"Token-Austausch fehlgeschlagen\"}");
                return;
            }

            HttpRequest userReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/users/@me"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> userRes = http.send(userReq, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> user = parseJsonObject(userRes.body());
            String discordId = (String) user.get("id");
            String username = (String) user.getOrDefault("username", "Unknown");
            String avatar = (String) user.get("avatar");
            String avatarUrl = avatar != null
                    ? "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatar + ".png"
                    : "https://cdn.discordapp.com/embed/avatars/0.png";

            if (!permissions.hasPermission(discordId)) {
                ex.getResponseHeaders().set("Location", "/?error=no_permission");
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return;
            }

            String sessionToken = sessions.createSession(discordId, username, avatarUrl);

            ex.getResponseHeaders().set("Set-Cookie",
                    "dmb_session=" + sessionToken + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
            ex.getResponseHeaders().set("Location", "/?auth=success");
            ex.sendResponseHeaders(302, -1);
            ex.close();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[WebAPI] OAuth-Fehler: " + e.getMessage(), e);
            sendJson(ex, 500, "{\"error\":\"Interner Serverfehler\"}");
        }
    }

    /** GET /api/auth/me → current session info */
    public void handleMe(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send405(ex); return; }
        WebSessionManager.Session s = getSession(ex);
        if (s == null) { sendJson(ex, 401, "{\"authenticated\":false}"); return; }
        sendJson(ex, 200, String.format(
                "{\"authenticated\":true,\"discordId\":\"%s\",\"username\":\"%s\",\"avatar\":\"%s\"}",
                s.discordId, escapeJson(s.discordUsername), escapeJson(s.discordAvatar)
        ));
    }

    /** POST /api/auth/logout */
    public void handleLogout(HttpExchange ex) throws IOException {
        String token = sessions.extractTokenFromCookie(ex.getRequestHeaders().getFirst("Cookie"));
        if (token != null) sessions.invalidateSession(token);
        ex.getResponseHeaders().set("Set-Cookie", "dmb_session=; Path=/; Max-Age=0");
        sendJson(ex, 200, "{\"success\":true}");
    }

    // ═══════════════════════════════════════════════════
    //  PLAYER ENDPOINTS
    // ═══════════════════════════════════════════════════

    /**
     * GET /api/players → Liste aller Online-Spieler + bekannter Offline-Spieler.
     *
     * Antwortformat:
     * {
     *   "online": [ {...}, ... ],
     *   "offline": [ {...}, ... ]
     * }
     *
     * Offline-Spieler werden aus den JSON-Snapshots des InventoryStoreManagers geladen.
     * Es werden nur Spieler zurückgegeben die mindestens einmal eingeloggt waren
     * (d.h. für die ein JSON-Snapshot existiert).
     */
    public void handlePlayers(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send405(ex); return; }
        if (getSession(ex) == null) { sendJson(ex, 401, "{\"error\":\"Nicht authentifiziert\"}"); return; }
        addCors(ex);

        // ── Online-Spieler ────────────────────────────────────────────────
        StringBuilder sb = new StringBuilder("{\"online\":[");
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        Set<UUID> onlineUuids = new HashSet<>();
        for (int i = 0; i < online.size(); i++) {
            Player p = online.get(i);
            onlineUuids.add(p.getUniqueId());
            if (i > 0) sb.append(",");
            sb.append(buildPlayerJson(p));
        }
        sb.append("],");

        // ── Offline-Spieler aus JSON-Snapshots ────────────────────────────
        sb.append("\"offline\":[");
        List<String> offlineJsons = buildOfflinePlayersJson(onlineUuids);
        for (int i = 0; i < offlineJsons.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(offlineJsons.get(i));
        }
        sb.append("]}");

        sendJson(ex, 200, sb.toString());
    }

    /**
     * Liest alle verfügbaren Offline-Snapshots und gibt die Spieler-JSONs zurück.
     * Spieler die aktuell online sind werden herausgefiltert (onlineUuids).
     */
    private List<String> buildOfflinePlayersJson(Set<UUID> onlineUuids) {
        List<String> result = new ArrayList<>();
        java.nio.file.Path playersDir = plugin.getInventoryStoreManager().getPlayersDir();

        try {
            if (!java.nio.file.Files.exists(playersDir)) return result;

            java.nio.file.Files.list(playersDir)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String uuidStr  = filename.replace(".json", "");
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            // Überspringe aktuell eingeloggte Spieler
                            if (onlineUuids.contains(uuid)) return;

                            OfflineInventoryStore store = plugin.getInventoryStoreManager().storeFor(uuid);
                            OfflineInventoryStore.PlayerSnapshot snap = store.getSnapshot();
                            if (snap == null) return;

                            // Name aus Bukkit holen (gecacht, kein I/O)
                            @SuppressWarnings("deprecation")
                            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            String name = op.getName() != null ? op.getName() : uuidStr;

                            result.add(buildOfflinePlayerJson(uuid, name, snap));
                        } catch (IllegalArgumentException ignored) {
                            // Dateiname ist keine UUID → überspringen
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("[WebAPI] Fehler beim Lesen der Offline-Spieler: " + e.getMessage());
        }

        return result;
    }

    /** GET /api/player/{uuid} → detailed player info (online only) */
    public void handlePlayerDetail(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send405(ex); return; }
        if (getSession(ex) == null) { sendJson(ex, 401, "{\"error\":\"Nicht authentifiziert\"}"); return; }
        addCors(ex);

        String path = ex.getRequestURI().getPath();
        String uuidStr = path.substring("/api/player/".length());

        Player player = findPlayer(uuidStr);
        if (player != null) {
            sendJson(ex, 200, buildDetailedPlayerJson(player));
            return;
        }

        // Offline-Fallback: Snapshot zurückgeben
        try {
            UUID uuid = UUID.fromString(uuidStr);
            OfflineInventoryStore store = plugin.getInventoryStoreManager().storeFor(uuid);
            OfflineInventoryStore.PlayerSnapshot snap = store.getSnapshot();
            if (snap != null) {
                @SuppressWarnings("deprecation")
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String name = op.getName() != null ? op.getName() : uuidStr;
                sendJson(ex, 200, buildOfflinePlayerJson(uuid, name, snap));
                return;
            }
        } catch (IllegalArgumentException ignored) {}

        sendJson(ex, 404, "{\"error\":\"Spieler nicht gefunden\"}");
    }

    // ═══════════════════════════════════════════════════
    //  ACTION ENDPOINTS
    // ═══════════════════════════════════════════════════

    public void handleBan(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        String duration = body.getOrDefault("duration", "perm");
        String reason = body.getOrDefault("reason", "Kein Grund angegeben");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "ban " + target + " " + duration + " " + reason));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"ban\",\"player\":\"" + escapeJson(target) + "\"}");
    }

    public void handleUnban(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "unban " + target));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"unban\",\"player\":\"" + escapeJson(target) + "\"}");
    }

    public void handleKick(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        String reason = body.getOrDefault("reason", "Kein Grund angegeben");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + target + " " + reason));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"kick\",\"player\":\"" + escapeJson(target) + "\"}");
    }

    public void handleMute(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        String duration = body.getOrDefault("duration", "1h");
        String reason = body.getOrDefault("reason", "Kein Grund angegeben");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mute " + target + " " + duration + " " + reason));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"mute\",\"player\":\"" + escapeJson(target) + "\"}");
    }

    public void handleUnmute(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "unmute " + target));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"unmute\",\"player\":\"" + escapeJson(target) + "\"}");
    }

    public void handleWarn(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        String reason = body.getOrDefault("reason", "Kein Grund angegeben");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warn " + target + " " + reason));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"warn\",\"player\":\"" + escapeJson(target) + "\"}");
    }

    public void handleHeal(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        Player player = Bukkit.getPlayer(body.get("player"));
        if (player == null) { sendJson(ex, 404, "{\"error\":\"Spieler nicht online\"}"); return; }
        runSync(() -> { player.setHealth(player.getMaxHealth()); player.setFoodLevel(20); player.setSaturation(20f); });
        sendJson(ex, 200, "{\"success\":true,\"action\":\"heal\"}");
    }

    public void handleFeed(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        Player player = Bukkit.getPlayer(body.get("player"));
        if (player == null) { sendJson(ex, 404, "{\"error\":\"Spieler nicht online\"}"); return; }
        runSync(() -> { player.setFoodLevel(20); player.setSaturation(20f); });
        sendJson(ex, 200, "{\"success\":true,\"action\":\"feed\"}");
    }

    public void handleStarve(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        Player player = Bukkit.getPlayer(body.get("player"));
        if (player == null) { sendJson(ex, 404, "{\"error\":\"Spieler nicht online\"}"); return; }
        runSync(() -> { player.setFoodLevel(0); player.setSaturation(0f); });
        sendJson(ex, 200, "{\"success\":true,\"action\":\"starve\"}");
    }

    public void handleKillPlayer(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        Player player = Bukkit.getPlayer(body.get("player"));
        if (player == null) { sendJson(ex, 404, "{\"error\":\"Spieler nicht online\"}"); return; }
        runSync(() -> player.setHealth(0));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"kill\"}");
    }

    public void handleVanish(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vanish " + target));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"vanish\"}");
    }

    public void handleAdminMode(HttpExchange ex) throws IOException {
        WebSessionManager.Session s = requirePost(ex); if (s == null) return;
        Map<String, String> body = parseBodyParams(ex);
        String target = body.get("player");
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "adminmode " + target));
        sendJson(ex, 200, "{\"success\":true,\"action\":\"adminmode\"}");
    }

    /** GET /api/inventory/{uuid} → serialized inventory + enderchest */
    public void handleInventory(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send405(ex); return; }
        if (getSession(ex) == null) { sendJson(ex, 401, "{\"error\":\"Nicht authentifiziert\"}"); return; }
        addCors(ex);

        String path = ex.getRequestURI().getPath();
        String uuidStr = path.substring("/api/inventory/".length());
        Player player = findPlayer(uuidStr);

        if (player != null) {
            sendJson(ex, 200, buildInventoryJson(player));
            return;
        }

        sendJson(ex, 404, "{\"error\":\"Spieler nicht online\"}");
    }

    // ═══════════════════════════════════════════════════
    //  JSON BUILDERS
    // ═══════════════════════════════════════════════════

    private String buildPlayerJson(Player p) {
        boolean vanished   = plugin.getVanishmanager()    != null && plugin.getVanishmanager().isVanished(p);
        boolean adminMode  = plugin.getAdminModeManager() != null && plugin.getAdminModeManager().isActive(p.getUniqueId());

        return String.format(
                "{\"uuid\":\"%s\",\"name\":\"%s\",\"online\":true," +
                        "\"health\":%.1f,\"maxHealth\":%.1f," +
                        "\"food\":%d,\"gamemode\":\"%s\",\"world\":\"%s\"," +
                        "\"x\":%.1f,\"y\":%.1f,\"z\":%.1f," +
                        "\"vanished\":%b,\"adminMode\":%b," +
                        "\"skinUrl\":\"https://mc-heads.net/avatar/%s/64\"}",
                p.getUniqueId(), escapeJson(p.getName()),
                p.getHealth(), p.getMaxHealth(),
                p.getFoodLevel(), p.getGameMode().name(),
                escapeJson(p.getWorld().getName()),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                vanished, adminMode,
                escapeJson(p.getName())
        );
    }

    /**
     * Baut ein JSON-Objekt für einen Offline-Spieler anhand seines Snapshots.
     */
    private String buildOfflinePlayerJson(UUID uuid, String name, OfflineInventoryStore.PlayerSnapshot snap) {
        return String.format(
                "{\"uuid\":\"%s\",\"name\":\"%s\",\"online\":false," +
                        "\"health\":%.1f,\"maxHealth\":%.1f," +
                        "\"food\":%d,\"gamemode\":\"%s\",\"world\":\"%s\"," +
                        "\"x\":%.1f,\"y\":%.1f,\"z\":%.1f," +
                        "\"vanished\":false,\"adminMode\":false," +
                        "\"lastSeen\":%d," +
                        "\"skinUrl\":\"https://mc-heads.net/avatar/%s/64\"}",
                uuid, escapeJson(name),
                snap.health, snap.maxHealth,
                snap.foodLevel, snap.gameMode,
                escapeJson(snap.worldName),
                snap.x, snap.y, snap.z,
                snap.snapshotAt,
                escapeJson(name)
        );
    }

    private String buildDetailedPlayerJson(Player p) {
        boolean vanished  = plugin.getVanishmanager()    != null && plugin.getVanishmanager().isVanished(p);
        boolean adminMode = plugin.getAdminModeManager() != null && plugin.getAdminModeManager().isActive(p.getUniqueId());

        return String.format(
                "{\"uuid\":\"%s\",\"name\":\"%s\",\"online\":true," +
                        "\"health\":%.1f,\"maxHealth\":%.1f,\"food\":%d,\"saturation\":%.1f," +
                        "\"gamemode\":\"%s\",\"world\":\"%s\"," +
                        "\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"yaw\":%.1f,\"pitch\":%.1f," +
                        "\"level\":%d,\"exp\":%.2f," +
                        "\"ping\":%d," +
                        "\"vanished\":%b,\"adminMode\":%b," +
                        "\"op\":%b," +
                        "\"skinUrl\":\"https://mc-heads.net/avatar/%s/128\"}",
                p.getUniqueId(), escapeJson(p.getName()),
                p.getHealth(), p.getMaxHealth(), p.getFoodLevel(), p.getSaturation(),
                p.getGameMode().name(),
                escapeJson(p.getWorld().getName()),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                p.getLocation().getYaw(), p.getLocation().getPitch(),
                p.getLevel(), p.getExp(),
                p.getPing(),
                vanished, adminMode,
                p.isOp(),
                escapeJson(p.getName())
        );
    }

    private String buildInventoryJson(Player p) {
        StringBuilder sb = new StringBuilder("{\"inventory\":[");
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(itemToJson(inv[i], i));
        }
        sb.append("],\"enderchest\":[");
        ItemStack[] ec = p.getEnderChest().getContents();
        for (int i = 0; i < ec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(itemToJson(ec[i], i));
        }
        sb.append("],\"armor\":[");
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(itemToJson(armor[i], i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String itemToJson(ItemStack item, int slot) {
        if (item == null || item.getType().name().equals("AIR")) {
            return "{\"slot\":" + slot + ",\"empty\":true}";
        }
        String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName() : "";
        return String.format(
                "{\"slot\":%d,\"type\":\"%s\",\"amount\":%d,\"displayName\":\"%s\",\"empty\":false}",
                slot, item.getType().name(), item.getAmount(), escapeJson(stripColor(displayName))
        );
    }

    // ═══════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════

    private WebSessionManager.Session getSession(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String token = sessions.extractTokenFromCookie(cookie);
        return sessions.getSession(token);
    }

    private WebSessionManager.Session requirePost(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { send405(ex); return null; }
        WebSessionManager.Session s = getSession(ex);
        if (s == null) { sendJson(ex, 401, "{\"error\":\"Nicht authentifiziert\"}"); return null; }
        return s;
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        addCors(ex);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void send405(HttpExchange ex) throws IOException {
        sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Cookie");
    }

    private void runSync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private Player findPlayer(String identifier) {
        try {
            return Bukkit.getPlayer(UUID.fromString(identifier));
        } catch (IllegalArgumentException e) {
            return Bukkit.getPlayer(identifier);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) params.put(decode(kv[0]), decode(kv[1]));
        }
        return params;
    }

    private Map<String, String> parseBodyParams(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.startsWith("{")) return parseJsonFlat(body);
        return parseQuery(body);
    }

    private Map<String, String> parseJsonFlat(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        for (String part : json.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"", "");
                String v = kv[1].trim().replace("\"", "");
                map.put(k, v);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1, json.length() - 1);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    private String decode(String s) {
        try { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String stripColor(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-or]", "");
    }
}