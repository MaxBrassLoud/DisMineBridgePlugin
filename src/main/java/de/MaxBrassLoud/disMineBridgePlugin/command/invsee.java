package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.playerdata.PlayerDataManager;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class invsee implements CommandExecutor, TabCompleter {

    private static final HashMap<UUID, OfflinePlayerData> openInventories = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (!p.hasPermission("dmb.invsee")) {
            p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        if (args.length != 1) {
            p.sendMessage(ChatColor.RED + "Nutze: /invsee <Spieler>");
            return true;
        }

        // Versuche zuerst Online-Spieler
        Player target = Bukkit.getPlayer(args[0]);

        if (target != null) {
            // Erlaube Admins ihre eigene Enderchest zu öffnen
            if (target.equals(p) && !p.hasPermission("dmb.admin")) {
                p.sendMessage(ChatColor.YELLOW + "Du kannst dein eigenes Inventar mit E öffnen.");
                p.sendMessage(ChatColor.GRAY + "» Nur Admins können ihr Inventar über InvSee öffnen.");
                return true;
            }
            openOnlineInventory(p, target);
            return true;
        }

        // Offline-Spieler
        @SuppressWarnings("deprecation")
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[0]);

        if (!offlineTarget.hasPlayedBefore()) {
            p.sendMessage(ChatColor.RED + "Der Spieler " + ChatColor.YELLOW + args[0] +
                    ChatColor.RED + " wurde nicht gefunden.");
            return true;
        }

        // Lade Offline-Daten
        PlayerDataManager.PlayerData data = PlayerDataManager.loadOfflinePlayerData(
                offlineTarget.getUniqueId(),
                offlineTarget.getName()
        );

        if (data != null) {
            openOfflineInventory(p, data);
        } else {
            p.sendMessage(ChatColor.RED + "Keine Inventar-Daten für " + ChatColor.YELLOW + args[0] +
                    ChatColor.RED + " gefunden.");
            p.sendMessage(ChatColor.GRAY + "Der Spieler muss sich mindestens einmal eingeloggt haben.");
        }

        return true;
    }

    private void openOnlineInventory(Player viewer, Player target) {
        // 54 Slots = 6 Zeilen
        Inventory inv = Bukkit.createInventory(null, 54, "§8» §6Inventar: §e" + target.getName());
        PlayerInventory targetInv = target.getInventory();

        // Zeile 1-3: Hauptinventar (Slots 9-35 vom Spieler = 27 Items)
        for (int i = 9; i < 36; i++) {
            inv.setItem(i - 9, targetInv.getItem(i));
        }

        // Zeile 4: Hotbar (Slots 0-8 vom Spieler)
        for (int i = 0; i < 9; i++) {
            inv.setItem(27 + i, targetInv.getItem(i));
        }

        // Zeile 5: Trennlinie mit Glasscheiben
        ItemStack glass = createGlass(ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬");
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, glass);
        }

        // Zeile 6: Rüstung + Offhand mit Info-Items
        setupArmorDisplay(inv, targetInv);

        // Speichere Inventar-Daten
        OfflinePlayerData data = new OfflinePlayerData();
        data.playerName = target.getName();
        data.isOnline = true;
        data.targetUUID = target.getUniqueId();
        openInventories.put(viewer.getUniqueId(), data);

        viewer.openInventory(inv);

        // Verbesserte Benachrichtigung
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage(ChatColor.YELLOW + "  Inventar von " + ChatColor.GOLD + target.getName() +
                ChatColor.GREEN + " (Online)");
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GRAY + "  » Zeile 1-3: " + ChatColor.WHITE + "Hauptinventar");
        viewer.sendMessage(ChatColor.GRAY + "  » Zeile 4:   " + ChatColor.WHITE + "Hotbar");
        viewer.sendMessage(ChatColor.GRAY + "  » Zeile 6:   " + ChatColor.WHITE + "Rüstung & Offhand");
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.YELLOW + "  ✎ Änderungen werden sofort gespeichert");
        viewer.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage("");

        Bukkit.getLogger().info("[InvSee] " + viewer.getName() + " hat das Inventar von " +
                target.getName() + " (online) geöffnet.");
    }

    private void openOfflineInventory(Player viewer, PlayerDataManager.PlayerData data) {
        // 54 Slots = 6 Zeilen
        Inventory inv = Bukkit.createInventory(null, 54,
                ChatColor.DARK_GRAY + "» " + ChatColor.GOLD + "Inventar: " +
                        ChatColor.RED + "(Offline) " + ChatColor.YELLOW + data.name);

        // Zeile 1-3: Hauptinventar (27 Items)
        if (data.inventory != null && data.inventory.length >= 27) {
            for (int i = 0; i < 27 && i < data.inventory.length; i++) {
                inv.setItem(i, data.inventory[i]);
            }
        }

        // Zeile 4: Hotbar (würde in originalem Inventar Slots 0-8 sein)
        // Wird hier nicht separat angezeigt

        // Zeile 5: Trennlinie
        ItemStack glass = createGlass(ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬");
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, glass);
        }

        // Zeile 6: Rüstung (readonly)
        if (data.armor != null && data.armor.length >= 4) {
            inv.setItem(45, createArmorInfo(Material.IRON_HELMET, "§6§lHelm", "§7Rüstung (Readonly)"));
            inv.setItem(46, data.armor[0]); // Helmet

            inv.setItem(47, createArmorInfo(Material.IRON_CHESTPLATE, "§6§lBrustpanzer", "§7Rüstung (Readonly)"));
            inv.setItem(48, data.armor[1]); // Chestplate

            inv.setItem(49, createArmorInfo(Material.IRON_LEGGINGS, "§6§lHose", "§7Rüstung (Readonly)"));
            inv.setItem(50, data.armor[2]); // Leggings

            inv.setItem(51, createArmorInfo(Material.IRON_BOOTS, "§6§lSchuhe", "§7Rüstung (Readonly)"));
            inv.setItem(52, data.armor[3]); // Boots
        }

        // Speichere Daten
        OfflinePlayerData invData = new OfflinePlayerData();
        invData.playerName = data.name;
        invData.isOnline = false;
        invData.targetUUID = data.uuid;
        invData.originalItems = data.inventory;
        openInventories.put(viewer.getUniqueId(), invData);

        viewer.openInventory(inv);

        // Benachrichtigung
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage(ChatColor.YELLOW + "  Inventar von " + ChatColor.GOLD + data.name +
                ChatColor.RED + " (Offline)");
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.YELLOW + "  ⚠ " + ChatColor.GRAY + "Offline-Inventar (Schreibgeschützt)");
        viewer.sendMessage(ChatColor.GRAY + "  » Änderungen werden " + ChatColor.RED + "NICHT " +
                ChatColor.GRAY + "gespeichert");
        viewer.sendMessage(ChatColor.GRAY + "  » Nur zur Ansicht verfügbar");
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage("");

        Bukkit.getLogger().info("[InvSee] " + viewer.getName() + " hat das Offline-Inventar von " +
                data.name + " geöffnet.");
    }

    private void setupArmorDisplay(Inventory inv, PlayerInventory targetInv) {
        // Slot 45: Info Helm
        inv.setItem(45, createArmorInfo(Material.IRON_HELMET, "§6§lHelm",
                "§7Platziere hier die", "§7Kopf-Rüstung"));
        inv.setItem(46, targetInv.getHelmet());

        // Slot 47: Info Brustpanzer
        inv.setItem(47, createArmorInfo(Material.IRON_CHESTPLATE, "§6§lBrustpanzer",
                "§7Platziere hier die", "§7Brust-Rüstung"));
        inv.setItem(48, targetInv.getChestplate());

        // Slot 49: Info Hose
        inv.setItem(49, createArmorInfo(Material.IRON_LEGGINGS, "§6§lHose",
                "§7Platziere hier die", "§7Bein-Rüstung"));
        inv.setItem(50, targetInv.getLeggings());

        // Slot 51: Info Schuhe
        inv.setItem(51, createArmorInfo(Material.IRON_BOOTS, "§6§lSchuhe",
                "§7Platziere hier die", "§7Fuß-Rüstung"));
        inv.setItem(52, targetInv.getBoots());

        // Slot 53: Offhand
        inv.setItem(53, targetInv.getItemInOffHand());
    }

    private ItemStack createGlass(String name) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack createArmorInfo(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static OfflinePlayerData getOpenInventory(UUID viewerUUID) {
        return openInventories.get(viewerUUID);
    }

    public static void removeOpenInventory(UUID viewerUUID) {
        openInventories.remove(viewerUUID);
    }

    public static void saveOnlineInventory(Player target, Inventory inv) {
        PlayerInventory targetInv = target.getInventory();

        // Zeile 1-3: Hauptinventar zurück speichern
        for (int i = 0; i < 27; i++) {
            targetInv.setItem(i + 9, inv.getItem(i));
        }

        // Zeile 4: Hotbar zurück speichern
        for (int i = 0; i < 9; i++) {
            targetInv.setItem(i, inv.getItem(27 + i));
        }

        // Zeile 6: Rüstung + Offhand (Slots 46, 48, 50, 52, 53)
        targetInv.setHelmet(inv.getItem(46));
        targetInv.setChestplate(inv.getItem(48));
        targetInv.setLeggings(inv.getItem(50));
        targetInv.setBoots(inv.getItem(52));
        targetInv.setItemInOffHand(inv.getItem(53));

        target.updateInventory();

        Bukkit.getLogger().info("[InvSee] Inventar von " + target.getName() + " wurde aktualisiert.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("dmb.invsee")) {
            String input = args[0].toLowerCase();

            // Online-Spieler
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    completions.add(player.getName());
                }
            }

            // Offline-Spieler (begrenzt auf 10 für Performance)
            int count = 0;
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (count >= 10) break;
                if (player.getName() != null && player.getName().toLowerCase().startsWith(input)) {
                    if (!completions.contains(player.getName())) {
                        completions.add(player.getName());
                        count++;
                    }
                }
            }
        }

        return completions;
    }

    public static class OfflinePlayerData {
        public String playerName;
        public boolean isOnline;
        public UUID targetUUID;
        public ItemStack[] originalItems;
    }
}