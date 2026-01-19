package de.MaxBrassLoud.disMineBridge.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager.BanInfo;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

public class BanListener implements Listener {

    private final DisMineBridge plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public BanListener(DisMineBridge plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
        this.language = plugin.getLanguageManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String uuid = event.getUniqueId().toString();

        try {
            // Prüfe ob Spieler einen aktiven Ban hat
            if (!database.hasActiveBan(uuid)) {
                return; // Kein aktiver Ban, Spieler darf joinen
            }

            // Hole Ban-Informationen
            BanInfo banInfo = database.getActiveBan(uuid);

            if (banInfo == null) {
                return; // Kein aktiver Ban gefunden
            }

            // Berechne verbleibende Zeit
            Instant now = Instant.now();
            String timeLeft;

            // Prüfe ob permanenter Ban
            if (banInfo.expireTime == -1) {
                timeLeft = language.getMessage("minecraft.ban.permanent");
            } else {
                Instant expire = Instant.ofEpochMilli(banInfo.expireTime);
                Duration remaining = Duration.between(now, expire);
                timeLeft = formatDuration(remaining);
            }

            // Erstelle Kick-Nachricht
            String kickMessage = language.getMessage("minecraft.ban.login-denied")
                    .replace("{reason}", banInfo.reason)
                    .replace("{time}", timeLeft)
                    .replace("{banner}", banInfo.bannerName);

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);

            plugin.getLogger().info("[Ban] Spieler " + event.getName() +
                    " wurde am Login gehindert. Ban-ID: " + banInfo.banId +
                    (banInfo.expireTime == -1 ? " (PERMANENT)" : ""));

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Fehler beim Überprüfen des Bans für UUID: " + uuid, e);

            // Bei Datenbankfehler den Spieler NICHT blockieren (Fail-Safe)
            // Aber logge den Fehler für Admin-Aufmerksamkeit
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