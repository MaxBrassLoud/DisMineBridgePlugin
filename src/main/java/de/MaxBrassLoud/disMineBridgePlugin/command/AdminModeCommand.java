package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.adminmode.AdminModeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminModeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        Player p = (Player) sender;

        if (!p.hasPermission("dmb.adminmode")) {
            p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        // Toggle AdminMode
        if (AdminModeManager.isInAdminMode(p)) {
            // AdminMode deaktivieren
            AdminModeManager.disableAdminMode(p);

            p.sendMessage("");
            p.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "ADMINMODE DEAKTIVIERT");
            p.sendMessage("");
            p.sendMessage(ChatColor.GRAY + "» Deine Daten wurden wiederhergestellt");
            p.sendMessage(ChatColor.GRAY + "» Du wurdest zurück teleportiert");
            p.sendMessage(ChatColor.GRAY + "» Inventar, Rüstung, Health, Hunger wiederhergestellt");
            p.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            p.sendMessage("");
        } else {
            // AdminMode aktivieren
            boolean success = AdminModeManager.enableAdminMode(p);

            if (success) {
                p.sendMessage("");
                p.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                p.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "ADMINMODE AKTIVIERT");
                p.sendMessage("");
                p.sendMessage(ChatColor.GRAY + "» Deine Daten wurden gespeichert");
                p.sendMessage(ChatColor.GRAY + "» Du bist nun im Creative-Modus");
                p.sendMessage(ChatColor.GRAY + "» Inventar geleert, Health & Hunger aufgefüllt");
                p.sendMessage(ChatColor.YELLOW + "» Nutze " + ChatColor.GOLD + "/adminmode" + ChatColor.YELLOW + " erneut zum Deaktivieren");
                p.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                p.sendMessage("");
            } else {
                p.sendMessage(ChatColor.RED + "Fehler beim Aktivieren des AdminModes.");
            }
        }

        return true;
    }
}