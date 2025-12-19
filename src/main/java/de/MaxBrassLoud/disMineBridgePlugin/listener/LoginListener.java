package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.adminmode.AdminModeManager;
import de.MaxBrassLoud.disMineBridgePlugin.maintenance.MaintenanceManager;
import de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager;
import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static de.MaxBrassLoud.disMineBridgePlugin.adminmode.AdminModeManager.adminModePlayers;

public class LoginListener implements Listener {

    private static final String WHITELIST_MESSAGE =
            ChatColor.RED + "" + ChatColor.BOLD + "NICHT AUF DER WHITELIST\n\n" +
                    ChatColor.GRAY + "Du stehst nicht auf der Whitelist dieses Servers.\n" +
                    ChatColor.GRAY + "Kontaktiere einen Administrator für Zugang.";

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();
        String username = player.getName();
        UUID uuid = player.getUniqueId();

        // Whitelist Check - FIXED
        if (!checkWhitelist(e, player, username, uuid)) {
            return; // Spieler wurde bereits gekickt
        }

        // Maintenance Check - FIXED
        if (!checkMaintenance(e, player, username, uuid)) {
            return; // Spieler wurde bereits gekickt
        }
    }

    private boolean checkWhitelist(PlayerLoginEvent e, Player player, String username, UUID uuid) {
        if (!WhitelistManager.isWhitelistEnabled()) {
            return true; // Whitelist deaktiviert - alle dürfen joinen
        }

        // Bypass Permission prüfen
        if (player.hasPermission("dmb.whitelist.bypass")) {
            return true; // Hat Bypass-Permission
        }

        // Whitelist Check
        if (!WhitelistManager.isWhitelisted(username, uuid)) {
            e.disallow(Result.KICK_WHITELIST, WHITELIST_MESSAGE);
            return false; // Spieler nicht auf Whitelist
        }
        try {
            String sql = "SELECT adminmode FROM users WHERE uuid = ? ";
            PreparedStatement stmt = DatabaseManager.getInstance().prepareStatement(sql, uuid);
            ResultSet rs = stmt.executeQuery();

            if(rs.getInt(0) == 0) {
                adminModePlayers.add(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "" + player.getName() + " im Adminmode");
            }

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }



        return true; // Spieler ist auf Whitelist
    }

    private boolean checkMaintenance(PlayerLoginEvent e, Player player, String username, UUID uuid) {
        if (!MaintenanceManager.isMaintenanceEnabled()) {
            return true; // Wartung deaktiviert - alle dürfen joinen
        }

        // Bypass Permission prüfen
        if (player.hasPermission("dmb.maintenance.bypass")) {
            return true; // Hat Bypass-Permission
        }

        // Whitelist Maintenance Bypass prüfen
        if (WhitelistManager.hasMaintenanceBypass(username, uuid)) {
            return true; // Hat Whitelist-Bypass
        }

        // Kein Bypass - kicke Spieler
        e.disallow(Result.KICK_OTHER, MaintenanceManager.getMaintenanceMessage());
        return false; // Spieler wird gekickt
    }


}