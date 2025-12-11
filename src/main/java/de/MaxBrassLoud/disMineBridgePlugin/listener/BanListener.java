package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BanListener implements Listener {

    private static final Logger logger = Logger.getLogger("DisMineBridge");

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String uuid = event.getUniqueId().toString();

        try {
            String sql = "SELECT reason, expire, pardon FROM bans WHERE uuid = ? ORDER BY expire DESC";
            PreparedStatement stmt = DatabaseManager.getInstance().prepareStatement(sql, uuid);
            ResultSet rs = stmt.executeQuery();

            Instant now = Instant.now();
            boolean isBanned = false;
            String reason = "Kein Grund angegeben";
            Instant latestExpire = Instant.EPOCH;

            while (rs.next()) {
                boolean pardoned = rs.getBoolean("pardon");
                long expireLong = rs.getLong("expire");

                if (expireLong <= 0 || pardoned) continue;

                Instant expire = Instant.ofEpochMilli(expireLong);

                // Nur aktive Bans berücksichtigen
                if (now.isBefore(expire)) {
                    isBanned = true;
                    String currentReason = rs.getString("reason");
                    if (currentReason != null && !currentReason.trim().isEmpty()) {
                        reason = currentReason;
                    }
                    if (expire.isAfter(latestExpire)) {
                        latestExpire = expire;
                    }
                }
            }

            rs.close();
            stmt.close();

            if (isBanned) {
                Duration remaining = Duration.between(now, latestExpire);
                String timeLeft = formatDuration(remaining);

                String kickMessage = ChatColor.RED + "Du bist gebannt!\n" +
                        ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n" +
                        ChatColor.GRAY + "Verbleibende Zeit: " + ChatColor.YELLOW + timeLeft;

                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Überprüfen des Bans für UUID: " + uuid, e);
            // Bei Datenbankfehler den Spieler NICHT blockieren (sicherer)
        }
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        if (totalSeconds < 0) totalSeconds = 0;

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty() || seconds > 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}