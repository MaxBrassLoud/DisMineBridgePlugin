package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.command.PunishCommand;
import de.MaxBrassLoud.disMineBridgePlugin.gui.PunishmentGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener für das Punishment-GUI
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
    }

    /**
     * Behandelt Klicks in der Spieler-Auswahl
     */
    private void handlePlayerSelectionClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Glasscheiben ignorieren
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
                // Erstelle Session und öffne Punishment-GUI
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

        // Glasscheiben ignorieren
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
                // Erstelle Session und öffne Punishment-GUI
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

        // Glasscheiben und Spieler-Kopf ignorieren
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE ||
                clicked.getType() == Material.PLAYER_HEAD) {
            return;
        }

        // Hole Session
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

        // Ban
        if (type == Material.BARRIER) {
            session.currentType = PunishmentGUI.PunishmentType.BAN;
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.BAN);
        }
        // Mute
        else if (type == Material.REDSTONE_BLOCK) {
            session.currentType = PunishmentGUI.PunishmentType.MUTE;
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.MUTE);
        }
        // Kick
        else if (type == Material.IRON_BOOTS) {
            session.currentType = PunishmentGUI.PunishmentType.KICK;
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.KICK);
        }
        // Warn
        else if (type == Material.PAPER) {
            session.currentType = PunishmentGUI.PunishmentType.WARN;
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUUID);
            PunishmentGUI.openReasonMenu(player, target, PunishmentGUI.PunishmentType.WARN);
        }
        // Historie
        else if (type == Material.BOOK) {
            player.sendMessage(ChatColor.YELLOW + "Historie-Feature kommt bald!");
        }
    }

    /**
     * Behandelt Klicks im Grund-Menü
     */
    private void handleReasonMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Glasscheiben ignorieren
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

        // Grund wurde gewählt
        if (clicked.getType() == Material.PAPER && clicked.hasItemMeta()) {
            String reason = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            session.selectedReason = reason;

            // Benutzerdefiniert?
            if (reason.equals("Benutzerdefiniert")) {
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage(ChatColor.YELLOW + "Bitte gib den Grund im Chat ein:");
                player.sendMessage(ChatColor.GRAY + "Format: " + ChatColor.WHITE + "<Grund> [Dauer]");
                player.sendMessage(ChatColor.GRAY + "Beispiel: " + ChatColor.WHITE + "Hacking 7d");
                player.sendMessage(ChatColor.DARK_GRAY + "(Schreibe 'abbrechen' zum Abbrechen)");
                player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("");
                return;
            }

            // Führe Strafe aus
            executePunishment(player, session);
        }
    }

    /**
     * Führt die gewählte Strafe aus
     */
    private void executePunishment(Player player, PunishmentGUI.GUISession session) {
        String targetName = session.targetName;
        String reason = session.selectedReason;
        PunishmentGUI.PunishmentType type = session.currentType;

        player.closeInventory();

        // Führe entsprechenden Command aus
        switch (type) {
            case BAN -> {
                player.performCommand("ban " + targetName + " " + reason + " 7d");
                player.sendMessage(ChatColor.GREEN + "✔ Ban wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            }
            case MUTE -> {
                player.performCommand("mute " + targetName + " 30 " + reason);
                player.sendMessage(ChatColor.GREEN + "✔ Mute wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            }
            case KICK -> {
                player.performCommand("kick " + targetName + " " + reason);
                player.sendMessage(ChatColor.GREEN + "✔ Kick wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            }
            case WARN -> {
                player.performCommand("warn " + targetName + " " + reason);
                player.sendMessage(ChatColor.GREEN + "✔ Warnung wurde ausgesprochen für " + ChatColor.YELLOW + targetName);
            }
        }

        sessions.remove(player.getUniqueId());
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