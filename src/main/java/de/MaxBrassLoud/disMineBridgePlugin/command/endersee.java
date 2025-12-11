package de.MaxBrassLoud.disMineBridgePlugin.command;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class endersee implements CommandExecutor, TabCompleter {

    private static final HashMap<UUID, OfflineEnderData> openEnderchests = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (!p.hasPermission("dmb.endersee")) {
            p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        if (args.length != 1) {
            p.sendMessage(ChatColor.RED + "Nutze: /endersee <Spieler>");
            return true;
        }

        // Versuche zuerst Online-Spieler
        Player target = Bukkit.getPlayer(args[0]);

        if (target != null) {
            // Erlaube Admins ihre eigene Enderchest zu öffnen
            if (target.equals(p) && !p.hasPermission("dmb.admin")) {
                p.sendMessage(ChatColor.YELLOW + "Du kannst deine eigene Enderchest normal öffnen.");
                p.sendMessage(ChatColor.GRAY + "» Nur Admins können ihre Enderchest über EnderSee öffnen.");
                return true;
            }
            openOnlineEnderchest(p, target);
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

        p.sendMessage(ChatColor.YELLOW + "⚠ Offline-Enderchests können nicht bearbeitet werden.");
        p.sendMessage(ChatColor.GRAY + "Der Spieler ist offline - Enderchest ist schreibgeschützt.");

        return true;
    }

    private void openOnlineEnderchest(Player viewer, Player target) {
        Inventory enderChest = target.getEnderChest();

        // Speichere Enderchest-Daten
        OfflineEnderData data = new OfflineEnderData();
        data.playerName = target.getName();
        data.isOnline = true;
        data.targetUUID = target.getUniqueId();
        openEnderchests.put(viewer.getUniqueId(), data);

        viewer.openInventory(enderChest);

        // Verbesserte Benachrichtigung
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.DARK_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage(ChatColor.LIGHT_PURPLE + "  Enderchest von " + ChatColor.GOLD + target.getName() +
                ChatColor.GREEN + " (Online)");
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GRAY + "  » " + ChatColor.WHITE + "27 Slots verfügbar");
        viewer.sendMessage(ChatColor.GRAY + "  » " + ChatColor.YELLOW + "Änderungen werden sofort gespeichert");

        // Zeige Füllgrad
        int usedSlots = 0;
        for (int i = 0; i < 27; i++) {
            if (enderChest.getItem(i) != null && !enderChest.getItem(i).getType().equals(Material.AIR)) {
                usedSlots++;
            }
        }
        double percentage = (usedSlots / 27.0) * 100;
        String fillBar = createFillBar(percentage);

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GRAY + "  Füllgrad: " + fillBar + ChatColor.GRAY + " " +
                String.format("%.1f%%", percentage) + " (" + usedSlots + "/27)");
        viewer.sendMessage(ChatColor.DARK_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        viewer.sendMessage("");

        Bukkit.getLogger().info("[EnderSee] " + viewer.getName() + " hat die Enderchest von " +
                target.getName() + " (online) geöffnet.");
    }

    private String createFillBar(double percentage) {
        int bars = (int) Math.round(percentage / 10); // 10 Balken = 100%
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                bar.append(ChatColor.GREEN + "█");
            } else {
                bar.append(ChatColor.DARK_GRAY + "█");
            }
        }

        return bar.toString();
    }

    public static OfflineEnderData getOpenEnderchest(UUID viewerUUID) {
        return openEnderchests.get(viewerUUID);
    }

    public static void removeOpenEnderchest(UUID viewerUUID) {
        openEnderchests.remove(viewerUUID);
    }

    public static void notifyChange(Player viewer, Player target) {
        viewer.sendMessage(ChatColor.GREEN + "✔ Änderungen an der Enderchest von " +
                ChatColor.YELLOW + target.getName() + ChatColor.GREEN + " gespeichert.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("dmb.endersee")) {
            String input = args[0].toLowerCase();

            // Online-Spieler
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    completions.add(player.getName());
                }
            }

            // Offline-Spieler (begrenzt auf 10)
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

    public static class OfflineEnderData {
        public String playerName;
        public boolean isOnline;
        public UUID targetUUID;
    }
}