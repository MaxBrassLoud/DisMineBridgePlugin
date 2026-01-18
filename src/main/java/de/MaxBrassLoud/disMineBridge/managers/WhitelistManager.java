package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.*;
import java.util.Map;
import java.util.UUID;

public class WhitelistManager {

    private final DisMineBridge plugin;

    public WhitelistManager(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    public boolean isWhitelisted(UUID uuid) {
        return Bukkit.getWhitelistedPlayers().stream()
                .anyMatch(p -> p.getUniqueId().equals(uuid));
    }

    public boolean isWhitelisted(String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        return player.isWhitelisted();
    }

    public void addToWhitelist(String minecraftName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(minecraftName);
        player.setWhitelisted(true);
    }

    public void removeFromWhitelist(String minecraftName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(minecraftName);
        player.setWhitelisted(false);
    }

    public void removeFromWhitelist(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        player.setWhitelisted(false);
    }

    public boolean canCreateWhitelistRequest(String discordId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            String query = "SELECT whitelist_status, whitelist_cooldown_until FROM users WHERE discord_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                rs.close();
                stmt.close();
                return true; // Neuer Benutzer kann anfragen
            }

            String status = rs.getString("whitelist_status");
            Long cooldownUntil = rs.getLong("whitelist_cooldown_until");

            rs.close();
            stmt.close();

            // Wenn bereits angenommen, keine neue Anfrage
            if ("approved".equals(status)) {
                return false;
            }

            // Wenn ausstehend, keine neue Anfrage
            if ("pending".equals(status)) {
                return false;
            }

            // Wenn Cooldown aktiv
            if (cooldownUntil != null && cooldownUntil > System.currentTimeMillis()) {
                return false;
            }

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void createWhitelistRequest(String discordId, String minecraftName, Map<String, String> additionalData) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();
        int userId = plugin.getDatabaseManager().createOrGetUser(discordId);

        // User Update
        String update = "UPDATE users SET minecraft_name = ?, whitelist_status = ?, whitelist_request_date = ? WHERE internal_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setString(1, minecraftName);
        stmt.setString(2, "pending");
        stmt.setLong(3, System.currentTimeMillis());
        stmt.setInt(4, userId);
        stmt.executeUpdate();
        stmt.close();

        // Ticket erstellen
        plugin.getTicketManager().createTicket(discordId, "whitelist", "Whitelist Anfrage", minecraftName, additionalData);
    }

    public void approveWhitelistRequest(String discordId, String minecraftName) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();
        int userId = plugin.getDatabaseManager().createOrGetUser(discordId);

        // User Update
        String update = "UPDATE users SET whitelist_status = ? WHERE internal_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setString(1, "approved");
        stmt.setInt(2, userId);
        stmt.executeUpdate();
        stmt.close();

        // Zur Minecraft Whitelist hinzufügen
        Bukkit.getScheduler().runTask(plugin, () -> {
            addToWhitelist(minecraftName);
            plugin.getLogger().info("Spieler " + minecraftName + " wurde zur Whitelist hinzugefügt (Discord ID: " + discordId + ")");
        });
    }

    public void denyWhitelistRequest(String discordId, String reason) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();
        int userId = plugin.getDatabaseManager().createOrGetUser(discordId);

        int cooldownDays = plugin.getConfig().getInt("whitelist.cooldown-after-rejection", 7);
        long cooldownUntil = System.currentTimeMillis() + (cooldownDays * 24L * 60L * 60L * 1000L);

        String update = "UPDATE users SET whitelist_status = ?, whitelist_cooldown_until = ? WHERE internal_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setString(1, "denied");
        stmt.setLong(2, cooldownUntil);
        stmt.setInt(3, userId);
        stmt.executeUpdate();
        stmt.close();
    }

    public void handleDiscordLeave(String discordId) {
        try {
            if (!plugin.getConfig().getBoolean("whitelist.remove-on-discord-leave", true)) {
                return;
            }

            Connection conn = plugin.getDatabaseManager().getConnection();

            String query = "SELECT minecraft_uuid, whitelist_status FROM users WHERE discord_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String uuidStr = rs.getString("minecraft_uuid");
                String status = rs.getString("whitelist_status");

                if ("approved".equals(status) && uuidStr != null) {
                    UUID uuid = UUID.fromString(uuidStr);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        removeFromWhitelist(uuid);
                        plugin.getLogger().info("Spieler mit UUID " + uuid + " wurde von der Whitelist entfernt (Discord Austritt)");
                    });
                }
            }

            rs.close();
            stmt.close();

            // User Status updaten
            plugin.getDatabaseManager().setUserOnServer(discordId, false);
            plugin.getDatabaseManager().updateWhitelistStatus(
                    plugin.getDatabaseManager().createOrGetUser(discordId),
                    "server-left"
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getWhitelistStatus(String discordId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            String query = "SELECT whitelist_status FROM users WHERE discord_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();

            String status = "none";
            if (rs.next()) {
                status = rs.getString("whitelist_status");
            }

            rs.close();
            stmt.close();
            return status;
        } catch (SQLException e) {
            e.printStackTrace();
            return "none";
        }
    }

    public long getCooldownRemaining(String discordId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            String query = "SELECT whitelist_cooldown_until FROM users WHERE discord_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();

            long cooldown = 0;
            if (rs.next()) {
                Long cooldownUntil = rs.getLong("whitelist_cooldown_until");
                if (cooldownUntil != null) {
                    long remaining = cooldownUntil - System.currentTimeMillis();
                    cooldown = remaining > 0 ? remaining : 0;
                }
            }

            rs.close();
            stmt.close();
            return cooldown;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}