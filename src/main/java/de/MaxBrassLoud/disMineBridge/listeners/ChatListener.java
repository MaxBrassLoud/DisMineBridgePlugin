package de.MaxBrassLoud.disMineBridge.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.MuteManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Verhindert, dass gemutete Spieler im Chat schreiben oder Chat-Befehle nutzen
 */
public class ChatListener implements Listener {

    private final DisMineBridge plugin;
    private final MuteManager muteManager;
    private final LanguageManager language;
    private final List<String> blockedCommands;

    public ChatListener(DisMineBridge plugin) {
        this.plugin = plugin;
        this.muteManager = plugin.getMuteManager();
        this.language = plugin.getLanguageManager();

        // Lade blockierte Commands aus Config
        this.blockedCommands = loadBlockedCommands();
    }

    /**
     * Lädt blockierte Commands aus der Config
     */
    private List<String> loadBlockedCommands() {
        List<String> commands = new ArrayList<>();

        // Versuche aus Config zu laden
        if (plugin.getConfig().contains("mute.blocked-commands")) {
            commands.addAll(plugin.getConfig().getStringList("mute.blocked-commands"));
        }

        // Fallback: Standard-Commands falls Config leer
        if (commands.isEmpty()) {
            commands.addAll(Arrays.asList(
                    "tell", "msg", "w", "whisper", "m", "pm", "dm",     // Private Nachrichten
                    "me", "action",                                      // Action Messages
                    "say", "broadcast", "bc", "shout", "announce",      // Broadcast
                    "r", "reply",                                        // Reply
                    "mail", "email",                                     // Mail Plugins
                    "helpop",                                            // HelpOP
                    "ac", "adminchat", "sc", "staffchat",               // Staff Chat
                    "gc", "globalchat", "lc", "localchat",              // Chat Channels
                    "ch", "channel",                                     // Channel Commands
                    "party", "p",                                        // Party Chat
                    "guild", "g", "gchat",                               // Guild Chat
                    "clan", "c", "cchat",                                // Clan Chat
                    "team", "t", "tchat",                                // Team Chat
                    "faction", "f", "fchat",                             // Faction Chat
                    "trade", "tradechat", "tc",                          // Trade Chat
                    "emote"                                              // Emote
            ));
        }

        plugin.getLogger().info("Mute-System: " + commands.size() + " Chat-Befehle werden blockiert");
        return commands;
    }

    /**
     * Behandelt Spieler-Join für Voice-Chat Mute-Synchronisation
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Informiere MuteManager über Join (für Voice-Chat Synchronisation)
        muteManager.onPlayerJoin(player);

        // Optional: Zeige Mute-Status bei Join
        if (muteManager.isMuted(player.getUniqueId())) {
            long expireTime = muteManager.getMuteExpireTime(player.getUniqueId());
            long remaining = expireTime - System.currentTimeMillis();
            String timeLeft = formatDuration(remaining);

            player.sendMessage(language.getMessage("minecraft.mute.chat-blocked")
                    .replace("{time}", timeLeft));

            if (muteManager.isVoiceChatEnabled()) {
                player.sendMessage(language.getMessage("minecraft.mute.voice-blocked"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Prüfe ob Spieler gemutet ist
        if (muteManager.isMuted(uuid)) {
            event.setCancelled(true);

            // Berechne verbleibende Zeit
            long expireTime = muteManager.getMuteExpireTime(uuid);
            long remaining = expireTime - System.currentTimeMillis();
            String timeLeft = formatDuration(remaining);

            // Sende Nachricht an Spieler
            player.sendMessage(language.getMessage("minecraft.mute.chat-blocked")
                    .replace("{time}", timeLeft));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Prüfe ob Spieler gemutet ist
        if (!muteManager.isMuted(uuid)) {
            return;
        }

        // Hole den Befehl (ohne /)
        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0].substring(1); // Entferne das /

        // Prüfe ob es ein blockierter Chat-Befehl ist
        if (isBlockedCommand(command)) {
            event.setCancelled(true);

            // Berechne verbleibende Zeit
            long expireTime = muteManager.getMuteExpireTime(uuid);
            long remaining = expireTime - System.currentTimeMillis();
            String timeLeft = formatDuration(remaining);

            // Sende Nachricht an Spieler
            player.sendMessage(language.getMessage("minecraft.mute.command-blocked")
                    .replace("{time}", timeLeft)
                    .replace("{command}", command));
        }
    }

    /**
     * Prüft ob ein Command geblockt werden soll
     */
    private boolean isBlockedCommand(String command) {
        // Direkte Übereinstimmung
        if (blockedCommands.contains(command)) {
            return true;
        }

        // Prüfe ob es mit einem blockierten Befehl beginnt (für Aliases)
        for (String blocked : blockedCommands) {
            if (command.startsWith(blocked)) {
                return true;
            }
        }

        // Spezielle Prüfungen für Minecraft-eigene Commands
        // Beispiel: /minecraft:tell sollte auch geblockt werden
        if (command.contains(":")) {
            String actualCommand = command.split(":")[1];
            return isBlockedCommand(actualCommand);
        }

        return false;
    }

    /**
     * Formatiert Millisekunden zu lesbarem String
     */
    private String formatDuration(long millis) {
        if (millis < 0) millis = 0;

        long days = millis / 86400000L;
        long hours = (millis % 86400000L) / 3600000L;
        long minutes = (millis % 3600000L) / 60000L;
        long seconds = (millis % 60000L) / 1000L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty() || seconds > 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}