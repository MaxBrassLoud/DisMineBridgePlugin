package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat-Manager mit klickbaren Moderator-Buttons und Chat-Replay beim Löschen.
 *
 * Nutzt Paper's native player.sendMessage(Component) direkt –
 * KEIN BukkitAudiences-Adapter, der die ClickEvents zerstört hat.
 *
 * Voraussetzung: Paper (nicht Spigot). Paper hat Adventure eingebaut.
 *
 * Löschen funktioniert via Chat-Replay:
 *   1. 100 leere Zeilen → Chat scrollt weg
 *   2. Verlauf ohne gelöschte Nachricht neu ausspielen
 */
public class ChatManager {

    private static final int REPLAY_COUNT = 100;
    private static final int MAX_HISTORY  = 200;

    private final DisMineBridge plugin;
    private final LanguageManager lang;

    private final LinkedHashMap<UUID, ChatEntry> history = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, ChatEntry> eldest) {
            return size() > MAX_HISTORY;
        }
    };

    public ChatManager(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang   = plugin.getLanguageManager();
    }

    /** Wird in DisMineBridge#onDisable() aufgerufen. */
    public void shutdown() {
        // nichts zu schließen – kein BukkitAudiences mehr
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Senden
    // ══════════════════════════════════════════════════════════════════════════

    public void sendMessage(Player sender, String raw) {
        UUID msgId = UUID.randomUUID();
        ChatEntry entry = new ChatEntry(msgId, sender.getUniqueId(),
                sender.getName(), raw, System.currentTimeMillis());

        // Components beim Senden cachen
        entry.componentForAll  = buildComponent(sender, raw, msgId, false);
        entry.componentForMods = buildComponent(sender, raw, msgId, true);

        synchronized (history) {
            history.put(msgId, entry);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Paper native: player.sendMessage(Component) – ClickEvents bleiben erhalten
            p.sendMessage(isMod(p) ? entry.componentForMods : entry.componentForAll);
        }

        plugin.getLogger().info("[Chat] " + sender.getName() + ": " + raw);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Löschen
    // ══════════════════════════════════════════════════════════════════════════

    public boolean deleteMessage(UUID msgId, Player moderator) {
        return deleteInternal(msgId, moderator.getName(), moderator);
    }

    public boolean deleteMessageConsole(UUID msgId, String deleterName) {
        return deleteInternal(msgId, deleterName, null);
    }

    private boolean deleteInternal(UUID msgId, String deleterName, Player modOrNull) {
        ChatEntry entry;
        synchronized (history) {
            entry = history.get(msgId);
            if (entry == null || entry.deleted) return false;
            entry.deleted   = true;
            entry.deletedBy = deleterName;
        }

        plugin.getLogger().info("[Chat] " + deleterName
                + " löschte Nachricht von " + entry.senderName + ": " + entry.raw);

        // Für alle Spieler Chat leeren + Verlauf neu ausspielen
        for (Player p : Bukkit.getOnlinePlayers()) {
            replayChat(p);
        }

        // Moderator-Bestätigung per Actionbar
        if (modOrNull != null) {
            modOrNull.sendActionBar(
                    Component.text("✓ Nachricht von " + entry.senderName
                            + " gelöscht.", NamedTextColor.GREEN));
        }

        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Chat-Replay
    // ══════════════════════════════════════════════════════════════════════════

    private void replayChat(Player player) {
        // 1. Chat leeren
        Component emptyLine = Component.empty();
        for (int i = 0; i < 100; i++) {
            player.sendMessage(emptyLine);
        }

        // 2. Verlauf zusammenstellen
        List<ChatEntry> snapshot;
        synchronized (history) {
            snapshot = new ArrayList<>(history.values());
        }

        int start = Math.max(0, snapshot.size() - REPLAY_COUNT);
        List<ChatEntry> toReplay = snapshot.subList(start, snapshot.size());

        // 3. Trennlinie
        player.sendMessage(
                Component.text("─────────────────────────────────────────",
                        NamedTextColor.DARK_GRAY));

        // 4. Nachrichten neu senden
        boolean playerIsMod = isMod(player);
        for (ChatEntry e : toReplay) {
            if (e.deleted) {
                player.sendMessage(buildDeletedPlaceholder(e));
            } else {
                // Gecachte Components verwenden
                Component comp = playerIsMod ? e.componentForMods : e.componentForAll;
                if (comp == null) {
                    comp = buildOfflineFallback(e);
                }
                player.sendMessage(comp);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Component-Aufbau
    // ══════════════════════════════════════════════════════════════════════════

    private Component buildComponent(Player sender, String raw,
                                     UUID msgId, boolean modView) {
        TextComponent.Builder builder = Component.text();

        // Prefix
        String prefix = resolvePrefix(sender);
        if (!prefix.isBlank()) {
            builder.append(legacy(prefix)).append(Component.space());
        }

        // Name – klickbar: /msg vorausfüllen
        builder.append(
                Component.text(sender.getName(), resolveNameColor(sender))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Klicken um ", NamedTextColor.GRAY)
                                        .append(Component.text(sender.getName(), NamedTextColor.YELLOW))
                                        .append(Component.text(" anzuschreiben", NamedTextColor.GRAY))))
                        .clickEvent(ClickEvent.suggestCommand(
                                "/msg " + sender.getName() + " "))
        );

        // Trennzeichen + Nachricht
        builder.append(Component.text(" » ", NamedTextColor.DARK_GRAY));
        builder.append(Component.text(raw, NamedTextColor.WHITE));

        // Moderator-Buttons
        if (modView) {
            builder.append(Component.text("  "));

            // [X] – löschen
            builder.append(
                    Component.text("[X]", NamedTextColor.RED, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(
                                    Component.text()
                                            .append(Component.text("Nachricht löschen\n",
                                                    NamedTextColor.RED, TextDecoration.BOLD))
                                            .append(Component.text("Von: ", NamedTextColor.GRAY))
                                            .append(Component.text(sender.getName(),
                                                    NamedTextColor.YELLOW))
                                            .append(Component.newline())
                                            .append(Component.text("\"" + truncate(raw, 40) + "\"",
                                                    NamedTextColor.WHITE))
                                            .append(Component.newline())
                                            .append(Component.text("→ Klicken zum Löschen",
                                                    NamedTextColor.DARK_RED))
                                            .build()))
                            .clickEvent(ClickEvent.runCommand(
                                    "/chatdelete " + msgId))
            );

            builder.append(Component.text(" "));

            // [TP] – teleportieren
            builder.append(
                    Component.text("[TP]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(
                                    Component.text()
                                            .append(Component.text("Zum Spieler teleportieren\n",
                                                    NamedTextColor.AQUA, TextDecoration.BOLD))
                                            .append(Component.text("Spieler: ", NamedTextColor.GRAY))
                                            .append(Component.text(sender.getName(),
                                                    NamedTextColor.YELLOW))
                                            .append(Component.newline())
                                            .append(Component.text("Welt: ", NamedTextColor.GRAY))
                                            .append(Component.text(
                                                    sender.getWorld().getName(),
                                                    NamedTextColor.WHITE))
                                            .append(Component.newline())
                                            .append(Component.text(
                                                    "→ Klicken zum Teleportieren",
                                                    NamedTextColor.DARK_AQUA))
                                            .build()))
                            .clickEvent(ClickEvent.runCommand(
                                    "/chattp " + sender.getName()))
            );
        }

        return builder.build();
    }

    private Component buildDeletedPlaceholder(ChatEntry e) {
        return Component.text()
                .append(Component.text("[✗] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(e.senderName + ": ",
                        NamedTextColor.DARK_GRAY))
                .append(Component.text("Nachricht entfernt",
                        NamedTextColor.DARK_GRAY, TextDecoration.ITALIC))
                .append(e.deletedBy != null
                        ? Component.text(" (von " + e.deletedBy + ")",
                        NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                        : Component.empty())
                .build();
    }

    private Component buildOfflineFallback(ChatEntry e) {
        return Component.text()
                .append(Component.text(e.senderName, NamedTextColor.GRAY))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(e.raw, NamedTextColor.WHITE))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════════════

    private String resolvePrefix(Player player) {
        try {
            @SuppressWarnings("unchecked")
            Class<Object> cls = (Class<Object>)
                    Class.forName("net.milkbowl.vault.chat.Chat");
            Object vault = plugin.getServer().getServicesManager().load(cls);
            if (vault != null) {
                String p = (String) cls
                        .getMethod("getPlayerPrefix",
                                org.bukkit.World.class, Player.class)
                        .invoke(vault, player.getWorld(), player);
                if (p != null && !p.isBlank()) return p;
            }
        } catch (Exception ignored) {}

        if (player.hasPermission("dmb.admin"))         return lang.getMessage("minecraft.chat.prefix-admin");
        if (player.hasPermission("dmb.ban"))           return lang.getMessage("minecraft.chat.prefix-mod");
        if (player.hasPermission("dmb.chat.moderate")) return lang.getMessage("minecraft.chat.prefix-support");
        return lang.getMessage("minecraft.chat.prefix-player");
    }

    private TextColor resolveNameColor(Player player) {
        if (player.isOp() || player.hasPermission("dmb.admin")) return NamedTextColor.RED;
        if (player.hasPermission("dmb.ban"))                     return NamedTextColor.BLUE;
        if (player.hasPermission("dmb.chat.moderate"))           return NamedTextColor.AQUA;
        return NamedTextColor.WHITE;
    }

    private boolean isMod(Player p) {
        return p.hasPermission("dmb.chat.moderate") || p.hasPermission("dmb.admin");
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(org.bukkit.ChatColor
                        .translateAlternateColorCodes('&', s));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public ChatEntry getEntry(UUID msgId) {
        synchronized (history) { return history.get(msgId); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Datenklasse
    // ══════════════════════════════════════════════════════════════════════════

    public static class ChatEntry {
        public final UUID   id;
        public final UUID   senderUuid;
        public final String senderName;
        public final String raw;
        public final long   timestamp;

        public volatile boolean   deleted          = false;
        public volatile String    deletedBy        = null;
        public volatile Component componentForAll  = null;
        public volatile Component componentForMods = null;

        ChatEntry(UUID id, UUID senderUuid, String senderName, String raw, long ts) {
            this.id         = id;
            this.senderUuid = senderUuid;
            this.senderName = senderName;
            this.raw        = raw;
            this.timestamp  = ts;
        }
    }
}