package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridgePlugin.playerdata.PlayerDataManager;
import de.MaxBrassLoud.disMineBridgePlugin.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LoginNotificationListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Prüfe ob erste Join
        if (!PlayerDataManager.hasPlayerData(player.getUniqueId())) {
            PlayerDataManager.createFirstJoinEntry(player);
        }

        // Prüfe Offline-Strafen
        List<String> notifications = checkOfflinePunishments(player);

        if (!notifications.isEmpty()) {
            // Sende Benachrichtigungen
            player.sendMessage(MessageManager.getLoginNotificationHeader());

            for (String notification : notifications) {
                player.sendMessage(notification);
            }

            player.sendMessage(MessageManager.getLoginNotificationFooter());
        }
    }

    private List checkOfflinePunishments(Player player) {
        List notifications = new ArrayList<>();

        try {
            String sql = "SELECT * FROM offline_punishments WHERE uuid = ? AND notified = 0";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String type = rs.getString("type");
                String reason = rs.getString("reason");
                String punisher = rs.getString("punisher");
                long duration = rs.getLong("duration");

                String notification = formatNotification(type, reason, punisher, duration);
                notifications.add(notification);

                // Markiere als benachrichtigt
                int id = rs.getInt("id");
                markAsNotified(id);
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return notifications;
    }

    private String formatNotification(String type, String reason, String punisher, long duration) {
        String durationStr = formatDuration(Duration.ofMillis(duration - System.currentTimeMillis()));

        return switch (type) {
            case "BAN" -> MessageManager.getLoginNewBan(reason, punisher, durationStr);
            case "MUTE" -> MessageManager.getLoginNewMute(reason, punisher, durationStr);
            case "WARN" -> MessageManager.getLoginNewWarn(reason, punisher);
            case "KICK" -> MessageManager.getLoginNewKick(reason, punisher);
            default -> "";
        };
    }

    private void markAsNotified(int id) {
        String sql = "UPDATE offline_punishments SET notified = 1 WHERE id = ?";
        DatabaseManager.getInstance().executeUpdate(sql, id);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}