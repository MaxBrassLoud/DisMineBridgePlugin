package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.gui.PunishmentGUI;
import de.MaxBrassLoud.disMineBridgePlugin.listener.PunishmentGUIListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Command um die Spieler-Auswahl zu öffnen
 * Verwendung: /punish
 */
public class PunishCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (!player.hasPermission("dmb.punish")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
            return true;
        }

        // Öffne Spieler-Auswahl GUI
        openPlayerSelectionGUI(player);

        return true;
    }

    /**
     * Öffnet ein GUI mit allen Spielern zur Auswahl
     */
    private void openPlayerSelectionGUI(Player viewer) {
        // Hole alle Online-Spieler
        Player[] onlinePlayers = Bukkit.getOnlinePlayers().toArray(new Player[0]);

        // Sortiere alphabetisch
        Arrays.sort(onlinePlayers, Comparator.comparing(Player::getName));

        // Berechne Inventory-Größe (muss Vielfaches von 9 sein)
        int playerCount = onlinePlayers.length;
        int rows = Math.min((int) Math.ceil(playerCount / 9.0) + 1, 6); // Max 6 Zeilen
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_GRAY + "» " + ChatColor.GOLD + "Spieler wählen");

        // Füge Spieler-Köpfe hinzu
        for (int i = 0; i < onlinePlayers.length && i < (size - 9); i++) {
            Player target = onlinePlayers[i];
            inv.setItem(i, createPlayerHead(target));
        }

        // Füge Offline-Spieler Button in letzter Zeile hinzu
        int lastRow = size - 9;
        inv.setItem(lastRow + 4, createOfflinePlayersButton());

        // Fülle leere Slots mit Glas
        fillEmptySlots(inv);

        viewer.openInventory(inv);

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage(ChatColor.YELLOW + "  Wähle einen Spieler aus");
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GRAY + "  » Klicke auf einen Spielerkopf");
        viewer.sendMessage(ChatColor.GRAY + "  » Oder wähle 'Offline-Spieler'");
        viewer.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage("");
    }

    /**
     * Erstellt einen Spieler-Kopf
     */
    private ItemStack createPlayerHead(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.YELLOW + ChatColor.BOLD.toString() + player.getName());

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Online");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Klicke um zu bestrafen");

            meta.setLore(lore);
            skull.setItemMeta(meta);
        }

        return skull;
    }

    /**
     * Erstellt Button für Offline-Spieler
     */
    private ItemStack createOfflinePlayersButton() {
        ItemStack item = new ItemStack(Material.SKELETON_SKULL);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + ChatColor.BOLD.toString() + "Offline-Spieler");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Wähle einen Spieler der");
            lore.add(ChatColor.GRAY + "gerade offline ist");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Klicke zum Öffnen");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Füllt leere Slots mit Glas
     */
    private void fillEmptySlots(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    /**
     * Öffnet Offline-Spieler Auswahl
     */
    public static void openOfflinePlayerGUI(Player viewer) {
        // Hole letzte 45 Offline-Spieler
        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();

        List<OfflinePlayer> recentPlayers = new ArrayList<>();
        for (OfflinePlayer op : offlinePlayers) {
            if (op.hasPlayedBefore() && !op.isOnline()) {
                recentPlayers.add(op);
            }
            if (recentPlayers.size() >= 45) break;
        }

        // Sortiere nach letztem Besuch (neueste zuerst)
        recentPlayers.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "» " + ChatColor.RED + "Offline-Spieler");

        // Füge Spieler-Köpfe hinzu
        for (int i = 0; i < recentPlayers.size() && i < 45; i++) {
            OfflinePlayer op = recentPlayers.get(i);
            inv.setItem(i, createOfflinePlayerHead(op));
        }

        // Zurück-Button
        ItemStack back = new ItemStack(Material.ARROW);
        var meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + ChatColor.BOLD.toString() + "« Zurück");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Zurück zur Spielerauswahl");
            meta.setLore(lore);
            back.setItemMeta(meta);
        }
        inv.setItem(49, back);

        // Fülle Rest
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 45; i < 54; i++) {
            if (i != 49 && inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        viewer.openInventory(inv);
    }

    /**
     * Erstellt einen Offline-Spieler-Kopf
     */
    private static ItemStack createOfflinePlayerHead(OfflinePlayer player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.YELLOW + ChatColor.BOLD.toString() + player.getName());

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.RED + "✖ " + ChatColor.GRAY + "Offline");

            // Zeige letzten Login
            long lastPlayed = player.getLastPlayed();
            if (lastPlayed > 0) {
                String timeAgo = getTimeAgo(System.currentTimeMillis() - lastPlayed);
                lore.add(ChatColor.GRAY + "Zuletzt: " + ChatColor.WHITE + timeAgo);
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "Klicke um zu bestrafen");

            meta.setLore(lore);
            skull.setItemMeta(meta);
        }

        return skull;
    }

    /**
     * Formatiert Zeit-Differenz
     */
    private static String getTimeAgo(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d her";
        if (hours > 0) return hours + "h her";
        if (minutes > 0) return minutes + "m her";
        return "Gerade eben";
    }
}