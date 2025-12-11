package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.DisMineBridgePlugin;
import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridgePlugin.maintenance.MaintenanceManager;
import de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager;
import org.bukkit.Bukkit;
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

public class DMBCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridgePlugin plugin;

    public DMBCommand(DisMineBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            showMainInfo(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "help", "hilfe", "?" -> new HelpCommand().onCommand(sender, cmd, label,
                    Arrays.copyOfRange(args, 1, args.length));
            case "info", "about" -> showDetailedInfo(sender);
            case "status" -> showStatus(sender);
            case "reload" -> handleReload(sender);
            case "version", "ver", "v" -> showVersion(sender);
            default -> showMainInfo(sender);
        }

        return true;
    }

    private void showMainInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "       DISMINEBRIDGE");
        sender.sendMessage(ChatColor.GRAY + "    Moderation & Administration Plugin");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "  /dmb help " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "Zeigt alle Befehle");
        sender.sendMessage(ChatColor.YELLOW + "  /dmb status " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "Zeigt System-Status");
        sender.sendMessage(ChatColor.YELLOW + "  /dmb info " + ChatColor.DARK_GRAY + "» " +
                ChatColor.GRAY + "Detaillierte Infos");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_GRAY + "  Version: " + ChatColor.WHITE + "1.0");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showDetailedInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "PLUGIN INFO" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + "DisMineBridge");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + "1.0");
        sender.sendMessage(ChatColor.YELLOW + "Autor: " + ChatColor.WHITE + "MaxBrassLoud");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Features:");
        sender.sendMessage(ChatColor.GRAY + "  » Moderation (Ban, Kick, Warn, Mute)");
        sender.sendMessage(ChatColor.GRAY + "  » Administration (InvSee, EnderSee, AdminMode)");
        sender.sendMessage(ChatColor.GRAY + "  » Whitelist & Wartungsmodus");
        sender.sendMessage(ChatColor.GRAY + "  » Vanish System");
        sender.sendMessage(ChatColor.GRAY + "  » Voice-Chat Mute Integration");
        sender.sendMessage(ChatColor.GRAY + "  » SQLite & MySQL Support");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_GRAY + "Nutze " + ChatColor.GOLD + "/dmb help" +
                ChatColor.DARK_GRAY + " für alle Befehle");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showStatus(CommandSender sender) {
        DatabaseManager db = DatabaseManager.getInstance();
        boolean dbOnline = db != null;
        String dbType = dbOnline ? db.getType().name() : "OFFLINE";

        boolean whitelistEnabled = WhitelistManager.isWhitelistEnabled();
        int whitelistCount = WhitelistManager.getWhitelistedPlayers().size();

        boolean maintenanceEnabled = MaintenanceManager.isMaintenanceEnabled();
        int bypassCount = MaintenanceManager.getBypassCount();

        boolean voicechat = Bukkit.getPluginManager().getPlugin("voicechat") != null;

        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "SYSTEM STATUS" +
                ChatColor.RESET + ChatColor.AQUA + " ▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        // Datenbank
        sender.sendMessage(ChatColor.YELLOW + "Datenbank:");
        sender.sendMessage(ChatColor.GRAY + "  » Typ: " + ChatColor.WHITE + dbType);
        sender.sendMessage(ChatColor.GRAY + "  » Status: " + (dbOnline ?
                ChatColor.GREEN + "✔ Online" : ChatColor.RED + "✖ Offline"));
        sender.sendMessage("");

        // Server
        sender.sendMessage(ChatColor.YELLOW + "Server:");
        sender.sendMessage(ChatColor.GRAY + "  » Spieler: " + ChatColor.WHITE +
                onlinePlayers + "/" + maxPlayers);
        sender.sendMessage(ChatColor.GRAY + "  » TPS: " + ChatColor.WHITE +
                String.format("%.2f", getTPS()));
        sender.sendMessage("");

        // Features
        sender.sendMessage(ChatColor.YELLOW + "Features:");
        sender.sendMessage(ChatColor.GRAY + "  » Whitelist: " + (whitelistEnabled ?
                ChatColor.RED + "✖ Aktiv (" + whitelistCount + ")" :
                ChatColor.GREEN + "✔ Deaktiviert"));
        sender.sendMessage(ChatColor.GRAY + "  » Wartung: " + (maintenanceEnabled ?
                ChatColor.RED + "✖ Aktiv (" + bypassCount + " Bypass)" :
                ChatColor.GREEN + "✔ Deaktiviert"));
        sender.sendMessage(ChatColor.GRAY + "  » VoiceChat: " + (voicechat ?
                ChatColor.GREEN + "✔ Integriert" :
                ChatColor.YELLOW + "○ Nicht gefunden"));
        sender.sendMessage("");

        sender.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Lade Config neu...");

        try {
            plugin.reloadConfig();
            WhitelistManager.reload();
            MaintenanceManager.reload();

            sender.sendMessage(ChatColor.GREEN + "✔ Config erfolgreich neu geladen!");
            Bukkit.getLogger().info("[DisMineBridge] Config neu geladen von " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "✖ Fehler beim Neuladen!");
            plugin.getLogger().severe("Fehler beim Reload: " + e.getMessage());
        }
    }

    private void showVersion(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "DisMineBridge " + ChatColor.WHITE + "v1.0" +
                ChatColor.GRAY + " by MaxBrassLoud");
    }

    private double getTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object tps = server.getClass().getField("recentTps").get(server);
            return ((double[]) tps)[0];
        } catch (Exception e) {
            return 20.0; // Fallback
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("help", "info", "status", "version"));

            if (sender.hasPermission("dmb.admin")) {
                completions.add("reload");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return Arrays.asList("moderation", "admin", "utility", "system").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}