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
 * Verarbeitet alle Chat-Ereignisse.
 *
 * Aufgaben:
 *  1. Gemutete Spieler am Chatten / Chat-Befehlen hindern (unverändertes Verhalten)
 *  2. Den Vanilla-Chat abfangen und durch das formatierte DMB-Chat-System ersetzen
 *     → Nachrichten laufen über ChatManager.sendMessage() (System-Kanal, nicht reportbar)
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
        this.blockedCommands = loadBlockedCommands();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Haupt-Chat-Event: Vanilla abfangen → ChatManager übernimmt
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * HIGHEST-Priorität damit andere Plugins (z.B. WorldGuard) zuerst abbrechen können.
     * Wenn das Event nicht gecancelt ist, übernimmt der ChatManager die Formatierung
     * und cancelt dann selbst das vanilla Event.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // ── 1. Mute-Prüfung ───────────────────────────────────────────────
        if (muteManager.isMuted(uuid)) {
            event.setCancelled(true);
            long remaining = muteManager.getMuteExpireTime(uuid) - System.currentTimeMillis();
            player.sendMessage(language.getMessage("minecraft.mute.chat-blocked")
                    .replace("{time}", formatDuration(remaining)));
            return;
        }

        // ── 2. Vanilla-Chat unterdrücken ──────────────────────────────────
        // Das Event wird gecancelt; die Nachricht übernimmt ChatManager.
        event.setCancelled(true);

        final String raw = event.getMessage();

        // ChatManager.sendMessage() muss auf dem Haupt-Thread laufen,
        // da wir Player#sendMessage() und Bukkit-API nutzen.
        // AsyncPlayerChatEvent läuft im Async-Thread → runTask().
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            // Nochmal prüfen: Spieler könnte in der Zwischenzeit offline gegangen sein
            if (!player.isOnline()) return;
            plugin.getChatManager().sendMessage(player, raw);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Blockierte Befehle für Gemutete (unverändert)
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!muteManager.isMuted(uuid)) return;

        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0].substring(1);

        if (isBlockedCommand(command)) {
            event.setCancelled(true);
            long remaining = muteManager.getMuteExpireTime(uuid) - System.currentTimeMillis();
            player.sendMessage(language.getMessage("minecraft.mute.command-blocked")
                    .replace("{time}", formatDuration(remaining))
                    .replace("{command}", command));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Join – Mute-Status anzeigen (unverändert)
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        muteManager.onPlayerJoin(player);

        if (muteManager.isMuted(player.getUniqueId())) {
            long expireTime = muteManager.getMuteExpireTime(player.getUniqueId());
            long remaining = expireTime - System.currentTimeMillis();
            player.sendMessage(language.getMessage("minecraft.mute.chat-blocked")
                    .replace("{time}", formatDuration(remaining)));
            if (muteManager.isVoiceChatEnabled()) {
                player.sendMessage(language.getMessage("minecraft.mute.voice-blocked"));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Hilfsmethoden
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> loadBlockedCommands() {
        List<String> commands = new ArrayList<>();
        if (plugin.getConfig().contains("mute.blocked-commands")) {
            commands.addAll(plugin.getConfig().getStringList("mute.blocked-commands"));
        }
        if (commands.isEmpty()) {
            commands.addAll(Arrays.asList(
                    "tell", "msg", "w", "whisper", "m", "pm", "dm",
                    "me", "action", "say", "broadcast", "bc", "shout", "announce",
                    "r", "reply", "mail", "email", "helpop",
                    "ac", "adminchat", "sc", "staffchat",
                    "gc", "globalchat", "lc", "localchat",
                    "ch", "channel", "party", "p", "guild", "g", "gchat",
                    "clan", "c", "cchat", "team", "t", "tchat",
                    "faction", "f", "fchat", "trade", "tradechat", "tc", "emote"
            ));
        }
        plugin.getLogger().info("Mute-System: " + commands.size() + " Chat-Befehle werden blockiert");
        return commands;
    }

    private boolean isBlockedCommand(String command) {
        if (blockedCommands.contains(command)) return true;
        for (String blocked : blockedCommands) {
            if (command.startsWith(blocked)) return true;
        }
        if (command.contains(":")) {
            return isBlockedCommand(command.split(":")[1]);
        }
        return false;
    }

    private String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        long days    = millis / 86400000L;
        long hours   = (millis % 86400000L) / 3600000L;
        long minutes = (millis % 3600000L) / 60000L;
        long seconds = (millis % 60000L) / 1000L;
        StringBuilder sb = new StringBuilder();
        if (days    > 0) sb.append(days).append("d ");
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty() || seconds > 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}