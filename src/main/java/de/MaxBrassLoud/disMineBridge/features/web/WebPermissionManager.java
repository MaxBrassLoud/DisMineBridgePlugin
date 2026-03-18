package de.MaxBrassLoud.disMineBridge.features.web;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages which Discord users have permission to access the web dashboard.
 * Permissions are stored in the plugin database table `web_permissions`.
 *
 * Discord-UUID-Verknüpfung kommt aus der bestehenden `users` Tabelle.
 * Sucht zuerst nach minecraft_uuid, dann als Fallback nach minecraft_name.
 */
public class WebPermissionManager {

    private final DisMineBridge plugin;

    public WebPermissionManager(DisMineBridge plugin) {
        this.plugin = plugin;
        createTable();
    }

    // ────────────────────────────────────────────────
    //  Table setup
    // ────────────────────────────────────────────────

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS web_permissions (" +
                "discord_id TEXT PRIMARY KEY," +
                "minecraft_uuid TEXT NOT NULL," +
                "minecraft_name TEXT NOT NULL," +
                "granted_by TEXT NOT NULL," +
                "granted_at INTEGER NOT NULL" +
                ")";
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WebPermissions] Fehler beim Erstellen der Tabelle: " + e.getMessage(), e);
        }
    }

    // ────────────────────────────────────────────────
    //  Grant / Revoke
    // ────────────────────────────────────────────────

    public boolean grantPermission(UUID minecraftUuid, String minecraftName, String grantedBy) {
        String discordId = getDiscordIdForPlayer(minecraftUuid, minecraftName);
        if (discordId == null) {
            return false;
        }

        String sql = "INSERT OR REPLACE INTO web_permissions " +
                "(discord_id, minecraft_uuid, minecraft_name, granted_by, granted_at) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, discordId);
            ps.setString(2, minecraftUuid.toString());
            ps.setString(3, minecraftName);
            ps.setString(4, grantedBy);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WebPermissions] Grant-Fehler: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean revokePermission(UUID minecraftUuid, String minecraftName) {
        String discordId = getDiscordIdForPlayer(minecraftUuid, minecraftName);
        if (discordId == null) return false;

        String sql = "DELETE FROM web_permissions WHERE discord_id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, discordId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WebPermissions] Revoke-Fehler: " + e.getMessage(), e);
            return false;
        }
    }

    // ────────────────────────────────────────────────
    //  Permission Check
    // ────────────────────────────────────────────────

    public boolean hasPermission(String discordId) {
        String sql = "SELECT 1 FROM web_permissions WHERE discord_id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WebPermissions] Check-Fehler: " + e.getMessage(), e);
            return false;
        }
    }

    // ────────────────────────────────────────────────
    //  Lookup Helpers
    // ────────────────────────────────────────────────

    /**
     * Sucht Discord-ID zuerst per UUID, dann per Name als Fallback.
     * Fallback nötig wenn minecraft_uuid noch NULL ist (z.B. vor dem ersten Join).
     */
    private String getDiscordIdForPlayer(UUID uuid, String minecraftName) {
        // ── Schritt 1: Suche per UUID ──
        if (uuid != null) {
            String sql = "SELECT discord_id FROM users WHERE minecraft_uuid = ? LIMIT 1";
            try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String discordId = rs.getString("discord_id");
                        if (discordId != null && !discordId.isBlank()) {
                            return discordId;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[WebPermissions] UUID-Lookup-Fehler: " + e.getMessage(), e);
            }
        }

        // ── Schritt 2: Fallback per Minecraft-Name ──
        if (minecraftName != null && !minecraftName.isBlank()) {
            plugin.getLogger().info("[WebPermissions] UUID-Lookup fehlgeschlagen, versuche Namens-Lookup für: " + minecraftName);
            String sql = "SELECT discord_id FROM users WHERE minecraft_name = ? LIMIT 1";
            try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, minecraftName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String discordId = rs.getString("discord_id");
                        if (discordId != null && !discordId.isBlank()) {
                            plugin.getLogger().info("[WebPermissions] Discord-ID über Namen gefunden für: " + minecraftName);
                            return discordId;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[WebPermissions] Name-Lookup-Fehler: " + e.getMessage(), e);
            }
        }

        // ── Schritt 3: Nichts gefunden ──
        plugin.getLogger().warning("[WebPermissions] Kein Discord-Link gefunden für UUID=" + uuid + " / Name=" + minecraftName);
        plugin.getLogger().warning("[WebPermissions] Stelle sicher dass der Spieler seinen Discord-Account verknüpft hat!");
        return null;
    }

    /**
     * Prüft ob ein Spieler seinen Discord-Account verknüpft hat.
     * Nutzt jetzt auch den Name-Fallback.
     */
    public boolean hasDiscordLink(UUID uuid, String minecraftName) {
        return getDiscordIdForPlayer(uuid, minecraftName) != null;
    }

    // Überschriebene Methode für Rückwärtskompatibilität (nur UUID)
    public boolean hasDiscordLink(UUID uuid) {
        return getDiscordIdForPlayer(uuid, null) != null;
    }

    // ────────────────────────────────────────────────
    //  DB Helper
    // ────────────────────────────────────────────────

    private Connection getConnection() throws SQLException {
        return plugin.getDatabaseManager().getConnection();
    }
}