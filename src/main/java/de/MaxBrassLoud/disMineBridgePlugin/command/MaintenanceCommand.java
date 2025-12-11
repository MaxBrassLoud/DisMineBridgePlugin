package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.maintenance.MaintenanceManager;
import de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager;
import de.MaxBrassLoud.disMineBridgePlugin.serverlist.ServerlistManager;
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

public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[Wartung] " + ChatColor.RESET;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("dmb.maintenance")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung dafür.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "on", "enable", "aktivieren" -> handleMaintenanceToggle(sender, true);
            case "off", "disable", "deaktivieren" -> handleMaintenanceToggle(sender, false);

            case "add", "hinzufügen" -> {
                if (validatePlayerArgument(sender, args)) handleAddBypass(sender, args[1]);
            }
            case "remove", "entfernen" -> {
                if (validatePlayerArgument(sender, args)) handleRemoveBypass(sender, args[1]);
            }
            case "list", "liste" -> handleListBypass(sender);

            case "check", "prüfen" -> {
                if (validatePlayerArgument(sender, args)) handleCheckPlayer(sender, args[1]);
            }

            case "info", "status" -> handleInfo(sender);

            case "message", "msg" -> handleSetMessage(sender, args);

            case "reload" -> handleReload(sender);

            default -> sendHelp(sender);
        }

        return true;
    }

    // -----------------------------------------------------
    //  WARTUNG EIN / AUS
    // -----------------------------------------------------
    private void handleMaintenanceToggle(CommandSender sender, boolean enable) {

        MaintenanceManager.setMaintenanceEnabled(enable);

        if (enable) {

            // MOTD auf Wartungsmodus setzen
            String motd = ChatColor.translateAlternateColorCodes('&', "&6Wartungsmodus");
            ServerlistManager.setMOTD(motd);

            sender.sendMessage("");
            sender.sendMessage(PREFIX + ChatColor.GOLD + "Wartungsmodus wurde " + ChatColor.BOLD +
                    "aktiviert" + ChatColor.RESET + ChatColor.GOLD + ".");
            sender.sendMessage(ChatColor.GRAY + "» Server-MOTD auf Wartungsmodus gesetzt.");

            int kicked = kickPlayersWithoutBypass();
            if (kicked > 0) sender.sendMessage(ChatColor.RED + "» " + kicked + " Spieler wurden gekickt.");

            broadcastToTeam(ChatColor.GOLD + "⚠ " + ChatColor.YELLOW + "Wartungsmodus wurde von "
                    + sender.getName() + " aktiviert!");

        } else {

            // MOTD auf gespeicherte Serverlist Nachricht setzen
            String motd = ChatColor.translateAlternateColorCodes('&', ServerlistManager.getMessage());
            ServerlistManager.setMOTD(motd);

            sender.sendMessage("");
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Wartungsmodus wurde " + ChatColor.BOLD +
                    "deaktiviert" + ChatColor.RESET + ChatColor.GREEN + ".");
            sender.sendMessage(ChatColor.GRAY + "» Server-MOTD wurde auf die normale Nachricht gesetzt.");

            broadcastToTeam(ChatColor.GREEN + "✔ " + ChatColor.YELLOW +
                    "Wartungsmodus wurde von " + sender.getName() + " deaktiviert!");
        }
    }

    // -----------------------------------------------------
    //  Bypass hinzufügen
    // -----------------------------------------------------
    private void handleAddBypass(CommandSender sender, String playerName) {
        if (WhitelistManager.hasMaintenanceBypass(playerName, null)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + playerName +
                    ChatColor.GRAY + " hat bereits Bypass.");
            return;
        }

        WhitelistManager.setMaintenanceBypass(playerName, true);

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Spieler " + ChatColor.YELLOW + playerName +
                ChatColor.GREEN + " hat nun Bypass.");

        Player online = Bukkit.getPlayer(playerName);
        if (online != null) {
            online.sendMessage(ChatColor.GREEN + "✔ Du hast Wartungs-Bypass erhalten.");
        }
    }

    // -----------------------------------------------------
    //  Bypass entfernen
    // -----------------------------------------------------
    private void handleRemoveBypass(CommandSender sender, String playerName) {
        if (!WhitelistManager.hasMaintenanceBypass(playerName, null)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + playerName +
                    ChatColor.GRAY + " hat keinen Bypass.");
            return;
        }

        WhitelistManager.setMaintenanceBypass(playerName, false);

        sender.sendMessage(PREFIX + ChatColor.RED + "Bypass entfernt von " + playerName + ".");

        Player online = Bukkit.getPlayer(playerName);
        if (online != null && MaintenanceManager.isMaintenanceEnabled()) {
            if (!online.hasPermission("dmb.maintenance.bypass")) {
                online.kickPlayer(MaintenanceManager.getMaintenanceMessage());
            }
        }
    }

    // -----------------------------------------------------
    //  Liste Bypass
    // -----------------------------------------------------
    private void handleListBypass(CommandSender sender) {
        List<String> bypassed = WhitelistManager.getMaintenanceBypassPlayers();

        sender.sendMessage(ChatColor.GOLD + "Bypass-Spieler: " + bypassed.size());
        for (String name : bypassed) sender.sendMessage(ChatColor.GRAY + " - " + name);
    }

    // -----------------------------------------------------
    //  Check
    // -----------------------------------------------------
    private void handleCheckPlayer(CommandSender sender, String playerName) {

        Player target = Bukkit.getPlayer(playerName);

        boolean perm = target != null && target.hasPermission("dmb.maintenance.bypass");
        boolean wl = WhitelistManager.hasMaintenanceBypass(playerName,
                target != null ? target.getUniqueId() : null);

        sender.sendMessage(ChatColor.YELLOW + "Permission-Bypass: " + (perm ? "§a✔" : "§c✖"));
        sender.sendMessage(ChatColor.YELLOW + "Whitelist-Bypass: " + (wl ? "§a✔" : "§c✖"));
    }

    // -----------------------------------------------------
    //  Infos
    // -----------------------------------------------------
    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Wartung: " +
                (MaintenanceManager.isMaintenanceEnabled() ? "§cAktiv" : "§aDeaktiviert"));
    }

    // -----------------------------------------------------
    //  Kick Nachricht setzen
    // -----------------------------------------------------
    private void handleSetMessage(CommandSender sender, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutze: /maintenance message <Text>");
            return;
        }

        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                .replace("\\n", "\n");

        MaintenanceManager.setMaintenanceMessage(msg);

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Nachricht gesetzt.");
    }

    private void handleReload(CommandSender sender) {
        MaintenanceManager.reload();
        sender.sendMessage(PREFIX + "Reload abgeschlossen.");
    }

    // -----------------------------------------------------
    //  Hilfsfunktionen
    // -----------------------------------------------------
    private boolean validatePlayerArgument(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutze: /maintenance " + args[0] + " <Spieler>");
            return false;
        }
        return true;
    }

    private int kickPlayersWithoutBypass() {
        int kicked = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasMaintenanceAccess(p)) {
                p.kickPlayer(MaintenanceManager.getMaintenanceMessage());
                kicked++;
            }
        }
        return kicked;
    }

    private boolean hasMaintenanceAccess(Player p) {
        return p.hasPermission("dmb.maintenance.bypass") ||
                WhitelistManager.hasMaintenanceBypass(p.getName(), p.getUniqueId());
    }

    private void broadcastToTeam(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("dmb.maintenance")) p.sendMessage(msg);
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "/maintenance on   §7- Wartung aktivieren");
        s.sendMessage(ChatColor.GOLD + "/maintenance off  §7- Wartung deaktivieren");
        s.sendMessage(ChatColor.GOLD + "/maintenance add <Spieler>");
        s.sendMessage(ChatColor.GOLD + "/maintenance remove <Spieler>");
        s.sendMessage(ChatColor.GOLD + "/maintenance list");
        s.sendMessage(ChatColor.GOLD + "/maintenance info");
        s.sendMessage(ChatColor.GOLD + "/maintenance message <Text>");
    }

    // -----------------------------------------------------
    //  TAB COMPLETE
    // -----------------------------------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("dmb.maintenance")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("on","off","add","remove","list","check","info","message","reload")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !WhitelistManager.hasMaintenanceBypass(p.getName(), p.getUniqueId()))
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return WhitelistManager.getMaintenanceBypassPlayers().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
