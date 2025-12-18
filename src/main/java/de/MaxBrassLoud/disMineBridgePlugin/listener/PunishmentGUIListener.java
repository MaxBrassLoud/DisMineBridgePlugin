package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.command.PunishCommand;
import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridgePlugin.gui.PunishmentGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener für das Punishment-GUI mit vollständiger Integration
 */
public class PunishmentGUIListener implements Listener {

    private static final Map<UUID, PunishmentGUI.GUISession> sessions = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Prüfe welches GUI geöffnet ist
        if (title.contains("Spieler wählen")) {
            handlePlayerSelectionClick(event, player);
            return;
        }

        if (title.contains("Offline-Spieler")) {
            handleOfflinePlayerClick(event, player);
            return;
        }

        if (title.contains("Strafen:")) {
            handleMainMenuClick(event, player);
            return;
        }

        if (title.contains("Grund wählen:")) {
            handleReasonMenuClick(event, player);
            return;
        }

        if (title.contains("Dauer wählen")) {
            handleDurationMenuClick(event, player);
            return;
        }
    }

    /**
     * Behandelt Klicks in der Spieler-Auswahl
     */
    private void handlePlayerSelectionClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        // Offline-Spieler Button
        if (clicked.getType() == Material.SKELETON_SKULL) {
            PunishCommand.openOfflinePlayerGUI(player);
            return;
        }

        // Spieler-Kopf geklickt
        if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            OfflinePlayer target = meta.getOwningPlayer();

            if (target != null) {
                createSession(player, target);
                PunishmentGUI.openMainMenu(player, target);
            }
        }
    }

    /**
     * Behandelt Klicks in der Offline-Spieler Auswahl
     */
    private void handleOfflinePlayerClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        // Zurück-Button
        if (clicked.getType() == Material.ARROW) {
            player.performCommand("punish");
            return;
        }

        // Spieler-Kopf geklickt
        if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            OfflinePlayer target = meta.getOwningPlayer();

            if (target != null) {
                createSession(player, target);
                PunishmentGUI.openMainMenu(player, target);
            }
        }
    }

    /**
     * Behandelt Klicks im Hauptmenü
     */
    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE ||
                clicked.getType() == Material.PLAYER_HEAD) {
            return;
        }

        PunishmentGUI.GUISession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "Fehler: Keine gültige Session gefunden.");
            player.closeInventory();
            return;
        }

        Material type = clicked.getType();

        // Zurück-Button
        if (type == Material.ARROW) {
            player.closeInventory();
            sessions.remove(player.getUniqueId());
            player.performCommand("punish");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);

        // Ban
        if (type == Material.BARRIER) {
            session.currentType = PunishmentGUI.PunishmentType.BAN;
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.BAN);
        }
        // Mute
        else if (type == Material.REDSTONE_BLOCK) {
            session.currentType = PunishmentGUI.PunishmentType.MUTE;
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.MUTE);
        }
        // Kick
        else if (type == Material.IRON_BOOTS) {
            session.currentType = PunishmentGUI.PunishmentType.KICK;
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.KICK);
        }
        // Warn
        else if (type == Material.PAPER) {
            session.currentType = PunishmentGUI.PunishmentType.WARN;
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.WARN);
        }
        // Historie
        else if (type == Material.BOOK) {
            PunishmentGUI.openHistoryMenu(player, target);
        }
    }

    /**
     * Behandelt Klicks im Grund-Menü
     */
    private void handleReasonMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        PunishmentGUI.GUISession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }

        // Zurück-Button
        if (clicked.getType() == Material.ARROW) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
            PunishmentGUI.openMainMenu(player, target);
            return;
        }

        // Benutzerdefinierter Grund
        if (clicked.getType() == Material.WRITABLE_BOOK) {
            player.closeInventory();
            startCustomReasonConversation(player, session);
            return;
        }

        // Grund wurde gewählt
        if (clicked.getType() == Material.PAPER && clicked.hasItemMeta()) {
            String reason = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            session.selectedReason = reason;

            // Für KICK und WARN direkt ausführen (keine Dauer nötig)
            if (session.currentType == PunishmentGUI.PunishmentType.KICK ||
                    session.currentType == PunishmentGUI.PunishmentType.WARN) {
                executePunishment(player, session);
            } else {
                // Für BAN und MUTE: Dauer-Auswahl öffnen
                OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
                PunishmentGUI.openDurationMenu(player, target, session.currentType, reason);
            }
        }
    }

    /**
     * Behandelt Klicks im Dauer-Menü
     */
    private void handleDurationMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        PunishmentGUI.GUISession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }

        // Zurück-Button
        if (clicked.getType() == Material.ARROW) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
            PunishmentGUI.openReasonMenu(player, target, session.currentType);
            return;
        }

        // Benutzerdefinierte Dauer
        if (clicked.getType() == Material.NAME_TAG) {
            player.closeInventory();
            startCustomDurationConversation(player, session);
            return;
        }

        // Empfohlene Dauer (CLOCK)
        if (clicked.getType() == Material.CLOCK) {
            session.selectedDuration = session.previousOffenses > 0 ?
                    session.currentType.getBaseDuration() * (session.previousOffenses + 1) :
                    session.currentType.getBaseDuration();
            executePunishment(player, session);
            return;
        }

        // Dauer-Option gewählt (LIME_DYE oder GRAY_DYE)
        if (clicked.getType() == Material.LIME_DYE || clicked.getType() == Material.GRAY_DYE) {
            if (clicked.getType() == Material.GRAY_DYE) {
                player.sendMessage(ChatColor.RED + "Diese Dauer ist zu kurz aufgrund vorheriger Vergehen!");
                return;
            }

            String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            long duration = parseDurationFromDisplayName(displayName);

            if (duration >= session.minimumDuration) {
                session.selectedDuration = duration;
                executePunishment(player, session);
            } else {
                player.sendMessage(ChatColor.RED + "Diese Dauer ist zu kurz! Mindestens: " +
                        PunishmentGUI.formatDuration(session.minimumDuration));
            }
            return;
        }

        // Execute-Button (EMERALD)
        if (clicked.getType() == Material.EMERALD) {
            if (session.selectedDuration > 0) {
                executePunishment(player, session);
            } else {
                player.sendMessage(ChatColor.RED + "Bitte wähle zuerst eine Dauer aus!");
            }
        }
    }

    /**
     * Startet Konversation für benutzerdefinierten Grund
     */
    private void startCustomReasonConversation(Player player, PunishmentGUI.GUISession session) {
        ConversationFactory factory = new ConversationFactory(Bukkit.getPluginManager().getPlugin("DisMineBridge"))
                .withFirstPrompt(new CustomReasonPrompt())
                .withLocalEcho(false)
                .withTimeout(60)
                .addConversationAbandonedListener(event -> {
                    if (!event.gracefulExit()) {
                        player.sendMessage(ChatColor.RED + "Eingabe abgebrochen.");
                        sessions.remove(player.getUniqueId());
                    }
                });

        Conversation conversation = factory.buildConversation(player);
        conversation.getContext().setSessionData("session", session);
        conversation.begin();
    }

    /**
     * Startet Konversation für benutzerdefinierte Dauer
     */
    private void startCustomDurationConversation(Player player, PunishmentGUI.GUISession session) {
        ConversationFactory factory = new ConversationFactory(Bukkit.getPluginManager().getPlugin("DisMineBridge"))
                .withFirstPrompt(new CustomDurationPrompt())
                .withLocalEcho(false)
                .withTimeout(60)
                .addConversationAbandonedListener(event -> {
                    if (!event.gracefulExit()) {
                        player.sendMessage(ChatColor.RED + "Eingabe abgebrochen.");
                        sessions.remove(player.getUniqueId());
                    }
                });

        Conversation conversation = factory.buildConversation(player);
        conversation.getContext().setSessionData("session", session);
        conversation.begin();
    }

    /**
     * Prompt für benutzerdefinierten Grund
     */
    private class CustomReasonPrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                    ChatColor.YELLOW + "Gib den Grund für die Strafe ein:\n" +
                    ChatColor.GRAY + "(Schreibe 'abbrechen' zum Abbrechen)\n" +
                    ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input.equalsIgnoreCase("abbrechen") || input.equalsIgnoreCase("cancel")) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "Abgebrochen.");
                return Prompt.END_OF_CONVERSATION;
            }

            PunishmentGUI.GUISession session = (PunishmentGUI.GUISession) context.getSessionData("session");
            if (session == null) return Prompt.END_OF_CONVERSATION;

            session.selectedReason = input.replace("_", " ");

            Player player = (Player) context.getForWhom();

            // Für KICK und WARN direkt ausführen
            if (session.currentType == PunishmentGUI.PunishmentType.KICK ||
                    session.currentType == PunishmentGUI.PunishmentType.WARN) {
                executePunishment(player, session);
            } else {
                // Für BAN und MUTE: Dauer-Auswahl öffnen
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("DisMineBridge"), () -> {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
                    PunishmentGUI.openDurationMenu(player, target, session.currentType, session.selectedReason);
                });
            }

            return Prompt.END_OF_CONVERSATION;
        }
    }

    /**
     * Prompt für benutzerdefinierte Dauer
     */
    private class CustomDurationPrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            PunishmentGUI.GUISession session = (PunishmentGUI.GUISession) context.getSessionData("session");
            String minDuration = session != null ? PunishmentGUI.formatDuration(session.minimumDuration) : "0";

            return ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                    ChatColor.YELLOW + "Gib die Dauer ein:\n" +
                    ChatColor.GRAY + "Format: " + ChatColor.WHITE + "7d, 12h, 30m, 1h30m\n" +
                    ChatColor.GRAY + "Mindestdauer: " + ChatColor.YELLOW + minDuration + "\n" +
                    ChatColor.GRAY + "(Schreibe 'abbrechen' zum Abbrechen)\n" +
                    ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input.equalsIgnoreCase("abbrechen") || input.equalsIgnoreCase("cancel")) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "Abgebrochen.");
                return Prompt.END_OF_CONVERSATION;
            }

            PunishmentGUI.GUISession session = (PunishmentGUI.GUISession) context.getSessionData("session");
            if (session == null) return Prompt.END_OF_CONVERSATION;

            Player player = (Player) context.getForWhom();

            try {
                long duration = PunishmentGUI.parseDuration(input);

                if (duration < session.minimumDuration) {
                    player.sendMessage(ChatColor.RED + "Dauer zu kurz! Mindestens: " +
                            PunishmentGUI.formatDuration(session.minimumDuration));
                    return Prompt.END_OF_CONVERSATION;
                }

                session.selectedDuration = duration;
                executePunishment(player, session);

            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Ungültige Dauer! Format: 7d, 12h, 30m");
            }

            return Prompt.END_OF_CONVERSATION;
        }
    }

    /**
     * Führt die gewählte Strafe aus
     */
    private void executePunishment(Player punisher, PunishmentGUI.GUISession session) {
        String targetName = session.targetName;
        UUID targetUUID = session.targetUUID;
        String reason = session.selectedReason;
        PunishmentGUI.PunishmentType type = session.currentType;
        long duration = session.selectedDuration;

        punisher.closeInventory();

        switch (type) {
            case BAN -> executeBan(punisher, targetName, targetUUID, reason, duration);
            case MUTE -> executeMute(punisher, targetName, targetUUID, reason, duration);
            case KICK -> executeKick(punisher, targetName, targetUUID, reason);
            case WARN -> executeWarn(punisher, targetName, targetUUID, reason);
        }

        sessions.remove(punisher.getUniqueId());
    }

    private void executeBan(Player punisher, String targetName, UUID targetUUID, String reason, long durationMillis) {
        long expire = Instant.now().plusMillis(durationMillis).toEpochMilli();

        String sql = "INSERT INTO bans (uuid, name, reason, banner, expire) VALUES (?, ?, ?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                targetUUID.toString(),
                targetName,
                reason,
                punisher.getName(),
                expire
        );

        if (result > 0) {
            String timeLeft = PunishmentGUI.formatDuration(durationMillis);

            // Kicke wenn online
            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null && target.isOnline()) {
                target.kickPlayer(ChatColor.RED + "Du wurdest gebannt!\n" +
                        ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n" +
                        ChatColor.GRAY + "Dauer: " + ChatColor.YELLOW + timeLeft + "\n" +
                        ChatColor.GRAY + "Von: " + ChatColor.WHITE + punisher.getName());
            }

            Bukkit.broadcastMessage(ChatColor.GOLD + targetName + ChatColor.RED + " wurde gebannt! (" + timeLeft + ")");
            punisher.sendMessage(ChatColor.GREEN + "✔ Ban wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            punisher.sendMessage(ChatColor.GRAY + "  Dauer: " + ChatColor.YELLOW + timeLeft);
            punisher.sendMessage(ChatColor.GRAY + "  Grund: " + ChatColor.WHITE + reason);
        } else {
            punisher.sendMessage(ChatColor.RED + "✖ Fehler beim Bannen!");
        }
    }

    private void executeMute(Player punisher, String targetName, UUID targetUUID, String reason, long durationMillis) {
        long expire = Instant.now().plusMillis(durationMillis).toEpochMilli();

        String sql = "INSERT INTO mutes (uuid, reason, expire) VALUES (?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                targetUUID.toString(),
                reason,
                expire
        );

        if (result > 0) {
            String timeLeft = PunishmentGUI.formatDuration(durationMillis);

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null && target.isOnline()) {
                target.sendMessage(ChatColor.RED + "Du wurdest gemutet!\n" +
                ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.GRAY + "Dauer: " + ChatColor.YELLOW + timeLeft + "\n" +
                ChatColor.GRAY + "Von: " + ChatColor.WHITE + punisher.getName());
            }

            Bukkit.broadcastMessage(ChatColor.GOLD + targetName + ChatColor.RED + " wurde gemutet! (" + timeLeft + ")");
            punisher.sendMessage(ChatColor.GREEN + "✔ Mute wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            punisher.sendMessage(ChatColor.GRAY + "  Dauer: " + ChatColor.YELLOW + timeLeft);
            punisher.sendMessage(ChatColor.GRAY + "  Grund: " + ChatColor.WHITE + reason);
        } else {
            punisher.sendMessage(ChatColor.RED + "✖ Fehler beim Muten!");
        }
    }

    private void executeKick(Player punisher, String targetName, UUID targetUUID, String reason) {
        String sql = "INSERT INTO kicks (uuid, name, reason, kicker) VALUES (?, ?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                targetUUID.toString(),
                targetName,
                reason,
                punisher.getName()
        );

        if (result > 0) {
            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null && target.isOnline()) {
                target.kickPlayer(ChatColor.RED + "Du wurdest gekickt!\n" +
                        ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n" +
                        ChatColor.GRAY + "Von: " + ChatColor.WHITE + punisher.getName());
            } else {
                punisher.sendMessage(ChatColor.YELLOW + "⚠ Spieler ist offline, Kick wurde gespeichert.");
            }

            Bukkit.broadcastMessage(ChatColor.GOLD + targetName + ChatColor.RED + " wurde gekickt!");
            punisher.sendMessage(ChatColor.GREEN + "✔ Kick wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            punisher.sendMessage(ChatColor.GRAY + "  Grund: " + ChatColor.WHITE + reason);
        } else {
            punisher.sendMessage(ChatColor.RED + "✖ Fehler beim Kicken!");
        }
    }

    private void executeWarn(Player punisher, String targetName, UUID targetUUID, String reason) {
        String sql = "INSERT INTO warns (uuid, name, reason, warner) VALUES (?, ?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                targetUUID.toString(),
                targetName,
                reason,
                punisher.getName()
        );

        if (result > 0) {
            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null && target.isOnline()) {
                target.sendMessage("");
                target.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "      DU WURDEST VERWARNT!");
                target.sendMessage("");
                target.sendMessage(ChatColor.YELLOW + "Grund: " + ChatColor.WHITE + reason);
                target.sendMessage(ChatColor.YELLOW + "Von: " + ChatColor.WHITE + punisher.getName());
                target.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                target.sendMessage("");
            }

            Bukkit.broadcastMessage(ChatColor.GOLD + targetName + ChatColor.RED + " wurde verwarnt!");
            punisher.sendMessage(ChatColor.GREEN + "✔ Warnung wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            punisher.sendMessage(ChatColor.GRAY + "  Grund: " + ChatColor.WHITE + reason);
        } else {
            punisher.sendMessage(ChatColor.RED + "✖ Fehler beim Verwarnen!");
        }
    }

    /**
     * Parst Dauer aus Display-Namen wie "1 Tag", "7 Tage", "30 Minuten"
     */
    private long parseDurationFromDisplayName(String displayName) {
        if (displayName.contains("Permanent")) return Long.MAX_VALUE;

        String[] parts = displayName.split(" ");
        if (parts.length < 2) return 0;

        try {
            int value = Integer.parseInt(parts[0]);
            String unit = parts[1].toLowerCase();

            if (unit.startsWith("tag")) return value * 86400000L;
            if (unit.startsWith("stunde")) return value * 3600000L;
            if (unit.startsWith("minute")) return value * 60000L;

        } catch (NumberFormatException e) {
            return 0;
        }

        return 0;
    }

    /**
     * Erstellt eine neue GUI-Session
     */
    public static void createSession(Player viewer, OfflinePlayer target) {
        PunishmentGUI.GUISession session = new PunishmentGUI.GUISession();
        session.viewerUUID = viewer.getUniqueId();
        session.targetUUID = target.getUniqueId();
        session.targetName = target.getName();

        sessions.put(viewer.getUniqueId(), session);
    }

    /**
     * Entfernt eine Session
     */
    public static void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    /**
     * Holt eine Session
     */
    public static PunishmentGUI.GUISession getSession(UUID uuid) {
        return sessions.get(uuid);
    }
}