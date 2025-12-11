package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MuteListener implements Listener {

    private static final Logger logger = Logger.getLogger("DisMineBridge");
    private final boolean voicechatEnabled;
    private final Map<UUID, Long> lastMuteNotification = new HashMap<>();
    private static final long NOTIFICATION_COOLDOWN = 3000; // 3 Sekunden

    public MuteListener() {
        Plugin voice = Bukkit.getPluginManager().getPlugin("voicechat");
        this.voicechatEnabled = (voice != null);

        if (voicechatEnabled) {
            logger.info("[DisMineBridge] Simple Voice Chat erkannt - Voice-Mute aktiv!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        MuteInfo muteInfo = getMuteInfo(uuid);

        if (muteInfo != null && muteInfo.isMuted) {
            event.setCancelled(true);

            // Cooldown für Benachrichtigungen
            long now = System.currentTimeMillis();
            Long lastNotif = lastMuteNotification.get(uuid);

            if (lastNotif == null || (now - lastNotif) > NOTIFICATION_COOLDOWN) {
                sendMuteNotification(event.getPlayer(), muteInfo);
                lastMuteNotification.put(uuid, now);
            }
        }
    }

    private void sendMuteNotification(org.bukkit.entity.Player player, MuteInfo info) {
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "         DU BIST GEMUTET");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Grund: " + ChatColor.WHITE + info.reason);
        player.sendMessage(ChatColor.GRAY + "  Verbleibend: " + ChatColor.YELLOW + info.remainingTime);
        player.sendMessage("");

        if (voicechatEnabled) {
            player.sendMessage(ChatColor.GRAY + "  » Du kannst weder schreiben noch sprechen");
        } else {
            player.sendMessage(ChatColor.GRAY + "  » Du kannst nicht schreiben");
        }

        player.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    /**
     * Prüft, ob ein Spieler aktuell gemutet ist.
     * Diese Methode wird auch vom VoiceChatMutePlugin verwendet.
     */
    public boolean isPlayerMuted(UUID uuid) {
        MuteInfo info = getMuteInfo(uuid);
        return info != null && info.isMuted;
    }

    /**
     * Gibt detaillierte Mute-Informationen zurück
     */
    public MuteInfo getMuteInfo(UUID uuid) {
        try {
            String sql = "SELECT reason, expire FROM mutes WHERE uuid = ? ORDER BY expire DESC LIMIT 1";
            PreparedStatement stmt = DatabaseManager.getInstance().prepareStatement(sql, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            MuteInfo info = null;
            if (rs.next()) {
                long expire = rs.getLong("expire");
                long now = Instant.now().toEpochMilli();

                if (expire > now) {
                    info = new MuteInfo();
                    info.isMuted = true;
                    info.reason = rs.getString("reason");
                    info.expireTime = expire;
                    info.remainingTime = formatDuration(Duration.ofMillis(expire - now));
                }
            }

            rs.close();
            stmt.close();

            return info;

        } catch (Exception e) {
            logger.log(Level.WARNING, "[DisMineBridge] Fehler beim Mute-Check: " + e.getMessage());
            return null;
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

    /**
     * Bereinigt alte Benachrichtigungen (wird bei Quit aufgerufen)
     */
    public void cleanup(UUID uuid) {
        lastMuteNotification.remove(uuid);
    }

    public static class MuteInfo {
        public boolean isMuted;
        public String reason;
        public long expireTime;
        public String remainingTime;
    }
}