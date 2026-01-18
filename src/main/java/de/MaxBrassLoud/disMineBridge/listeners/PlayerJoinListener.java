package de.MaxBrassLoud.disMineBridge.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.WhitelistManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.sql.*;

public class PlayerJoinListener implements Listener {

    private final DisMineBridge plugin;

    public PlayerJoinListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        boolean whitelist = plugin.getConfig().getBoolean("whitelist-enabled");

        if (!whitelist) {
            return;
        }

        if (player.isOp()) {
            return;
        }

        if (player.hasPermission("dmb.whitelist.bypass")) {
            return;
        }
        // Whitelist Check
        if (!plugin.getWhitelistManager().isWhitelisted(player.getUniqueId())) {

            LanguageManager lang = plugin.getLanguageManager();
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                    lang.getMessage("minecraft.whitelist.not-whitelisted"));

        }

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LanguageManager lang = plugin.getLanguageManager();

        // In Datenbank aktualisieren/erstellen
        updatePlayerData(player);

        // Welcome Nachricht
        if (!player.hasPlayedBefore()) {
            event.setJoinMessage(lang.getMessage("minecraft.join.first-join")
                    .replace("{player}", player.getName()));
            player.sendMessage(lang.getMessage("minecraft.join.welcome")
                    .replace("{player}", player.getName()));
        }
    }

    private void updatePlayerData(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = plugin.getDatabaseManager().getConnection();

                // Suche nach User mit diesem Minecraft Namen
                String query = "SELECT internal_id, minecraft_uuid FROM users WHERE minecraft_name = ?";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, player.getName());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int userId = rs.getInt("internal_id");
                    String storedUUID = rs.getString("minecraft_uuid");

                    // UUID aktualisieren falls geändert oder nicht vorhanden
                    if (storedUUID == null || !storedUUID.equals(player.getUniqueId().toString())) {
                        plugin.getDatabaseManager().updateMinecraftData(
                                userId,
                                player.getName(),
                                player.getUniqueId()
                        );
                    }
                }

                rs.close();
                stmt.close();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}