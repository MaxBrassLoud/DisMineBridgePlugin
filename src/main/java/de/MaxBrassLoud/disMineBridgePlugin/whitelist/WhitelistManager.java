package de.MaxBrassLoud.disMineBridgePlugin.whitelist;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WhitelistManager {

    private static final Logger logger = Logger.getLogger("DisMineBridge");
    private static FileConfiguration config;
    private static Plugin plugin;
    private static boolean whitelistEnabled = false;

    public static void init(FileConfiguration configuration, Plugin pluginInstance) {
        config = configuration;
        plugin = pluginInstance;
        whitelistEnabled = config.getBoolean("whitelist.enabled", false);
    }

    public static boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public static void setWhitelistEnabled(boolean enabled) {
        whitelistEnabled = enabled;
        config.set("whitelist.enabled", enabled);
        saveConfig();
    }

    public static boolean isWhitelisted(String username, UUID uuid) {
        try {
            // Prüfe nach UUID falls vorhanden
            if (uuid != null && checkByUUID(username, uuid)) {
                return true;
            }
            // Prüfe nach Username
            return checkByUsername(username, uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Whitelist-Check für " + username, e);
            return false;
        }
    }

    private static boolean checkByUUID(String username, UUID uuid) throws Exception {
        String sql = "SELECT username FROM whitelist WHERE uuid = ?";
        PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid.toString());
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String dbUsername = rs.getString("username");
            if (!dbUsername.equals(username)) {
                updateUsername(uuid, username);
            }
            rs.close();
            ps.close();
            return true;
        }
        rs.close();
        ps.close();
        return false;
    }

    private static boolean checkByUsername(String username, UUID uuid) throws Exception {
        String sql = "SELECT uuid FROM whitelist WHERE username = ?";
        PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, username);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String dbUuid = rs.getString("uuid");
            if ((dbUuid == null || dbUuid.isEmpty()) && uuid != null) {
                updateUUID(username, uuid);
            }
            rs.close();
            ps.close();
            return true;
        }
        rs.close();
        ps.close();
        return false;
    }

    public static void addToWhitelist(String username, String addedBy, boolean bypassMaintenance) {
        long now = System.currentTimeMillis();
        DatabaseManager db = DatabaseManager.getInstance();

        String sql;
        if (db.isMySQL()) {
            sql = "INSERT INTO whitelist (username, bypass_maintenance, added_by, added_at, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE bypass_maintenance = ?, last_updated = ?";
            db.executeUpdate(sql, username, bypassMaintenance, addedBy, now, now, bypassMaintenance, now);
        } else {
            sql = "INSERT INTO whitelist (username, bypass_maintenance, added_by, added_at, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?) ON CONFLICT(username) DO UPDATE SET bypass_maintenance = ?, last_updated = ?";
            db.executeUpdate(sql, username, bypassMaintenance ? 1 : 0, addedBy, now, now, bypassMaintenance ? 1 : 0, now);
        }
    }

    public static void removeFromWhitelist(String username) {
        DatabaseManager.getInstance().executeUpdate("DELETE FROM whitelist WHERE username = ?", username);
    }

    private static void updateUUID(String username, UUID uuid) {
        DatabaseManager.getInstance().executeUpdate(
                "UPDATE whitelist SET uuid = ?, last_updated = ? WHERE username = ?",
                uuid.toString(), System.currentTimeMillis(), username
        );
    }

    private static void updateUsername(UUID uuid, String newUsername) {
        DatabaseManager.getInstance().executeUpdate(
                "UPDATE whitelist SET username = ?, last_updated = ? WHERE uuid = ?",
                newUsername, System.currentTimeMillis(), uuid.toString()
        );
    }

    public static void setMaintenanceBypass(String username, boolean bypass) {
        DatabaseManager.getInstance().executeUpdate(
                "UPDATE whitelist SET bypass_maintenance = ?, last_updated = ? WHERE username = ?",
                bypass ? 1 : 0, System.currentTimeMillis(), username
        );
    }

    public static boolean hasMaintenanceBypass(String username, UUID uuid) {
        try {
            String sql = "SELECT bypass_maintenance FROM whitelist WHERE uuid = ? OR username = ?";
            String uuidStr = uuid != null ? uuid.toString() : "";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuidStr, username);
            ResultSet rs = ps.executeQuery();

            boolean bypass = rs.next() && rs.getBoolean("bypass_maintenance");
            rs.close();
            ps.close();
            return bypass;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Maintenance-Bypass-Check für " + username, e);
            return false;
        }
    }

    public static List<String> getWhitelistedPlayers() {
        List<String> players = new ArrayList<>();
        try {
            String sql = "SELECT username FROM whitelist ORDER BY username ASC";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                players.add(rs.getString("username"));
            }

            rs.close();
            ps.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Laden der Whitelist", e);
        }
        return players;
    }

    public static List<String> getMaintenanceBypassPlayers() {
        List<String> players = new ArrayList<>();
        try {
            String sql = "SELECT username FROM whitelist WHERE bypass_maintenance = ? ORDER BY username ASC";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, 1);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                players.add(rs.getString("username"));
            }

            rs.close();
            ps.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Laden der Bypass-Liste", e);
        }
        return players;
    }

    public static void reload() {
        whitelistEnabled = config.getBoolean("whitelist.enabled", false);
        logger.info("Whitelist neu geladen. Status: " + (whitelistEnabled ? "aktiviert" : "deaktiviert"));
    }

    private static void saveConfig() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                config.save(plugin.getDataFolder() + "/config.yml");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern der Config", e);
            }
        });
    }
}