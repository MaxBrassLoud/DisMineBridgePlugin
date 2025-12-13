package de.MaxBrassLoud.disMineBridgePlugin.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Verwaltet alle Nachrichten aus der Config
 */
public class MessageManager {

    private static JavaPlugin plugin;
    public static FileConfiguration config;
    private static final Map<String, String> cachedMessages = new HashMap<>();

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        reload();
    }

    public static void reload() {
        if (plugin == null) {
            throw new IllegalStateException("MessageManager wurde nicht initialisiert! Rufe zuerst MessageManager.init() auf.");
        }
        plugin.reloadConfig();
        config = plugin.getConfig();
        cachedMessages.clear();
        loadMessages();
    }

    private static void loadMessages() {
        // Lade häufig verwendete Nachrichten in Cache
        cacheMessage("messages.prefix");
        cacheMessage("messages.errors.no-permission");
        cacheMessage("messages.errors.player-only");
        cacheMessage("messages.errors.player-not-found");
    }

    private static void cacheMessage(String path) {
        String msg = config.getString(path, "");
        cachedMessages.put(path, ChatColor.translateAlternateColorCodes('&', msg));
    }

    /**
     * Holt eine Nachricht aus der Config
     */
    public static String getMessage(String path) {
        if (cachedMessages.containsKey(path)) {
            return cachedMessages.get(path);
        }

        String msg = config.getString(path, "§cNachricht nicht gefunden: " + path);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Holt eine Nachricht mit Platzhaltern
     */
    public static String getMessage(String path, Map<String, String> placeholders) {
        String msg = getMessage(path);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return msg;
    }

    /**
     * Holt eine Nachricht mit Prefix
     */
    public static String getMessageWithPrefix(String path) {
        return getMessage("messages.prefix") + getMessage(path);
    }

    /**
     * Vereinfachte Methode mit varargs für Platzhalter
     * Beispiel: getMessage("ban.success", "player", "Steve", "duration", "7d")
     */
    public static String getMessage(String path, String... placeholders) {
        String msg = getMessage(path);

        if (placeholders.length % 2 != 0) {
            plugin.getLogger().warning("Ungerade Anzahl an Platzhaltern für: " + path);
            return msg;
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            String key = placeholders[i];
            String value = placeholders[i + 1];
            msg = msg.replace("{" + key + "}", value);
        }

        return msg;
    }

    // ===== SPEZIFISCHE NACHRICHTEN-METHODEN =====

    public static String getNoPermissionMessage() {
        return getMessage("messages.errors.no-permission");
    }

    public static String getPlayerOnlyMessage() {
        return getMessage("messages.errors.player-only");
    }

    public static String getPlayerNotFoundMessage() {
        return getMessage("messages.errors.player-not-found");
    }

    public static String getPlayerNotOnlineMessage(String playerName) {
        return getMessage("messages.errors.player-not-online", "player", playerName);
    }

    public static String getBanSuccessMessage(String player) {
        return getMessage("messages.ban.success", "player", player);
    }

    public static String getBanKickMessage(String reason, String banner, String duration, String date) {
        return getMessage("messages.ban.kick-message",
                "reason", reason,
                "banner", banner,
                "duration", duration,
                "date", date);
    }

    public static String getBanBroadcast(String player) {
        return getMessage("messages.ban.broadcast", "player", player);
    }

    public static String getUnbanSuccessMessage(String player) {
        return getMessage("messages.unban.success", "player", player);
    }

    public static String getUnbanNotBannedMessage() {
        return getMessage("messages.unban.not-banned");
    }

    public static String getKickSuccessMessage(String player) {
        return getMessage("messages.kick.success", "player", player);
    }

    public static String getKickMessage(String reason, String kicker) {
        return getMessage("messages.kick.kick-message",
                "reason", reason,
                "kicker", kicker);
    }

    public static String getWarnSuccessMessage(String player) {
        return getMessage("messages.warn.success", "player", player);
    }

    public static String getWarnMessage(String reason, String warner, String count) {
        return getMessage("messages.warn.warn-message",
                "reason", reason,
                "warner", warner,
                "count", count);
    }

    public static String getMuteSuccessMessage(String player, String duration) {
        return getMessage("messages.mute.success",
                "player", player,
                "duration", duration);
    }

    public static String getMuteMessage(String reason, String muter, String duration) {
        return getMessage("messages.mute.mute-message",
                "reason", reason,
                "muter", muter,
                "duration", duration);
    }

    public static String getMuteChatBlocked(String reason, String remaining) {
        return getMessage("messages.mute.chat-blocked",
                "reason", reason,
                "remaining", remaining);
    }

    public static String getUnmuteSuccessMessage(String player) {
        return getMessage("messages.unmute.success", "player", player);
    }

    public static String getWhitelistEnabledMessage() {
        return getMessage("messages.whitelist.enabled");
    }

    public static String getWhitelistDisabledMessage() {
        return getMessage("messages.whitelist.disabled");
    }

    public static String getMaintenanceEnabledMessage() {
        return getMessage("messages.maintenance.enabled");
    }

    public static String getMaintenanceDisabledMessage() {
        return getMessage("messages.maintenance.disabled");
    }

    public static String getAdminModeEnabledMessage() {
        return getMessage("messages.adminmode.enabled");
    }

    public static String getAdminModeDisabledMessage() {
        return getMessage("messages.adminmode.disabled");
    }

    public static String getVanishEnabledMessage() {
        return getMessage("messages.vanish.enabled");
    }

    public static String getVanishDisabledMessage() {
        return getMessage("messages.vanish.disabled");
    }

    // ===== LOGIN NOTIFICATIONS =====

    public static String getLoginNotificationHeader() {
        return getMessage("messages.login.punishment-header");
    }

    public static String getLoginNotificationFooter() {
        return getMessage("messages.login.punishment-footer");
    }

    public static String getLoginNewBan(String reason, String banner, String duration) {
        return getMessage("messages.login.new-ban",
                "reason", reason,
                "banner", banner,
                "duration", duration);
    }

    public static String getLoginNewMute(String reason, String muter, String duration) {
        return getMessage("messages.login.new-mute",
                "reason", reason,
                "muter", muter,
                "duration", duration);
    }

    public static String getLoginNewWarn(String reason, String warner) {
        return getMessage("messages.login.new-warn",
                "reason", reason,
                "warner", warner);
    }

    public static String getLoginNewKick(String reason, String kicker) {
        return getMessage("messages.login.new-kick",
                "reason", reason,
                "kicker", kicker);
    }
}