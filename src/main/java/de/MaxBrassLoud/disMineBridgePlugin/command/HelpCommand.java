package de.MaxBrassLoud.disMineBridgePlugin.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            showMainHelp(sender);
            return true;
        }

        String category = args[0].toLowerCase();

        switch (category) {
            case "moderation", "mod" -> showModerationHelp(sender);
            case "admin", "administration" -> showAdminHelp(sender);
            case "utility", "utils", "tools" -> showUtilityHelp(sender);
            case "system" -> showSystemHelp(sender);
            default -> showMainHelp(sender);
        }

        return true;
    }

    private void showMainHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "DISMINEBRIDGE HILFE" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Kategorien:");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "/dmb help moderation " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "Ban, Kick, Warn, Mute");
        sender.sendMessage(ChatColor.GOLD + "/dmb help admin " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "InvSee, EnderSee, AdminMode, Vanish");
        sender.sendMessage(ChatColor.GOLD + "/dmb help utility " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "Whitelist, Maintenance");
        sender.sendMessage(ChatColor.GOLD + "/dmb help system " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "Info, Reload, Status");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Plugin Version: " + ChatColor.WHITE + "1.0");
        sender.sendMessage(ChatColor.GRAY + "Autor: " + ChatColor.WHITE + "MaxBrassLoud");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showModerationHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "MODERATION" +
                ChatColor.RESET + ChatColor.RED + " ▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        if (sender.hasPermission("dmb.ban")) {
            sender.sendMessage(ChatColor.YELLOW + "/ban <Spieler> <Grund> <Dauer>");
            sender.sendMessage(ChatColor.GRAY + "  » Bannt einen Spieler temporär");
            sender.sendMessage(ChatColor.DARK_GRAY + "  Beispiel: /ban Steve Hacking 7d");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.unban")) {
            sender.sendMessage(ChatColor.YELLOW + "/unban <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Entbannt einen Spieler");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.kick")) {
            sender.sendMessage(ChatColor.YELLOW + "/kick <Spieler> <Grund>");
            sender.sendMessage(ChatColor.GRAY + "  » Kickt einen Spieler vom Server");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.warn")) {
            sender.sendMessage(ChatColor.YELLOW + "/warn <Spieler> <Grund>");
            sender.sendMessage(ChatColor.GRAY + "  » Verwarnt einen Spieler");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.mute")) {
            sender.sendMessage(ChatColor.YELLOW + "/mute <Spieler> <Minuten> [Grund]");
            sender.sendMessage(ChatColor.GRAY + "  » Muted einen Spieler (Text + Voice)");
            sender.sendMessage(ChatColor.DARK_GRAY + "  Beispiel: /mute Steve 30 Spam");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.unmute")) {
            sender.sendMessage(ChatColor.YELLOW + "/unmute <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Entmuted einen Spieler");
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "ADMINISTRATION" +
                ChatColor.RESET + ChatColor.AQUA + " ▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        if (sender.hasPermission("dmb.invsee")) {
            sender.sendMessage(ChatColor.YELLOW + "/invsee <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Zeigt und bearbeitet Inventar eines Spielers");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.endersee")) {
            sender.sendMessage(ChatColor.YELLOW + "/endersee <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Zeigt und bearbeitet Enderchest eines Spielers");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.adminmode")) {
            sender.sendMessage(ChatColor.YELLOW + "/adminmode");
            sender.sendMessage(ChatColor.GRAY + "  » Aktiviert AdminMode (speichert dein Inventar)");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.vanish")) {
            sender.sendMessage(ChatColor.YELLOW + "/vanish [Spieler] [on|off]");
            sender.sendMessage(ChatColor.GRAY + "  » Macht dich oder andere unsichtbar");
            sender.sendMessage(ChatColor.YELLOW + "/vanish list");
            sender.sendMessage(ChatColor.GRAY + "  » Zeigt alle im Vanish");
            sender.sendMessage(ChatColor.YELLOW + "/vanish info");
            sender.sendMessage(ChatColor.GRAY + "  » Zeigt deinen Vanish-Status");
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showUtilityHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "UTILITY" +
                ChatColor.RESET + ChatColor.GREEN + " ▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        if (sender.hasPermission("dmb.whitelist")) {
            sender.sendMessage(ChatColor.YELLOW + "/whitelist on|off");
            sender.sendMessage(ChatColor.GRAY + "  » Aktiviert/Deaktiviert die Whitelist");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist add <Spieler> [bypass]");
            sender.sendMessage(ChatColor.GRAY + "  » Fügt Spieler zur Whitelist hinzu");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist remove <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Entfernt Spieler von der Whitelist");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist list|check|info");
            sender.sendMessage(ChatColor.GRAY + "  » Verschiedene Whitelist-Infos");
            sender.sendMessage("");
        }

        if (sender.hasPermission("dmb.maintenance")) {
            sender.sendMessage(ChatColor.YELLOW + "/maintenance on|off");
            sender.sendMessage(ChatColor.GRAY + "  » Aktiviert/Deaktiviert Wartungsmodus");
            sender.sendMessage(ChatColor.YELLOW + "/maintenance add <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Gibt Wartungs-Bypass");
            sender.sendMessage(ChatColor.YELLOW + "/maintenance remove <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "  » Entfernt Wartungs-Bypass");
            sender.sendMessage(ChatColor.YELLOW + "/maintenance list|check|info");
            sender.sendMessage(ChatColor.GRAY + "  » Verschiedene Wartungs-Infos");
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showSystemHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "SYSTEM" +
                ChatColor.RESET + ChatColor.LIGHT_PURPLE + " ▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "/dmb info");
        sender.sendMessage(ChatColor.GRAY + "  » Zeigt Plugin-Informationen");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "/dmb status");
        sender.sendMessage(ChatColor.GRAY + "  » Zeigt System-Status (DB, Features, etc.)");
        sender.sendMessage("");

        if (sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/dmb reload");
            sender.sendMessage(ChatColor.GRAY + "  » Lädt Config neu");
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.LIGHT_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("moderation", "admin", "utility", "system")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}