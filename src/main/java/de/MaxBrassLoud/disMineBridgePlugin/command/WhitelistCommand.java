package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WhitelistCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[Whitelist] " + ChatColor.RESET;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dmb.whitelist")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "on", "enable", "aktivieren" -> handleWhitelistToggle(sender, true);
            case "off", "disable", "deaktivieren" -> handleWhitelistToggle(sender, false);
            case "add", "hinzufügen" -> {
                if (validatePlayerArgument(sender, args)) {
                    handleAddPlayer(sender, args[1], args.length > 2 && args[2].equalsIgnoreCase("bypass"));
                }
            }
            case "remove", "entfernen" -> {
                if (validatePlayerArgument(sender, args)) {
                    handleRemovePlayer(sender, args[1]);
                }
            }
            case "list", "liste" -> handleListPlayers(sender);
            case "check", "prüfen" -> {
                if (validatePlayerArgument(sender, args)) {
                    handleCheckPlayer(sender, args[1]);
                }
            }
            case "info", "status" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleWhitelistToggle(CommandSender sender, boolean enable) {
        WhitelistManager.setWhitelistEnabled(enable);
        String status = enable ? ChatColor.GREEN + "aktiviert" : ChatColor.RED + "deaktiviert";

        sender.sendMessage("");
        sender.sendMessage(PREFIX + "Whitelist wurde " + ChatColor.BOLD + status + ChatColor.RESET + ".");

        if (enable) {
            sender.sendMessage(ChatColor.GRAY + "» Nur Spieler auf der Whitelist können joinen");
            sender.sendMessage(ChatColor.YELLOW + "» Nutze " + ChatColor.GOLD + "/whitelist add <Spieler>" +
                    ChatColor.YELLOW + " um Spieler hinzuzufügen");

            // Kicke Spieler die nicht auf der Whitelist sind
            int kicked = kickNonWhitelistedPlayers();
            if (kicked > 0) {
                sender.sendMessage(ChatColor.RED + "» " + kicked + " Spieler wurden gekickt");
            }
        } else {
            sender.sendMessage(ChatColor.GRAY + "» Alle Spieler können nun joinen");
        }
        sender.sendMessage("");
    }

    private void handleAddPlayer(CommandSender sender, String playerName, boolean maintenanceBypass) {
        // Prüfe ob Spieler bereits auf der Whitelist ist
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (WhitelistManager.isWhitelisted(playerName, target.getUniqueId())) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + playerName + ChatColor.GRAY + " ist bereits auf der Whitelist.");
            return;
        }

        WhitelistManager.addToWhitelist(playerName, sender.getName(), maintenanceBypass);

        sender.sendMessage("");
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Spieler " + ChatColor.YELLOW + playerName +
                ChatColor.GREEN + " wurde zur Whitelist hinzugefügt.");

        if (maintenanceBypass) {
            sender.sendMessage(ChatColor.GRAY + "» Mit Wartungs-Bypass");
        }

        sender.sendMessage(ChatColor.GRAY + "» Hinzugefügt von: " + sender.getName());
        sender.sendMessage("");

        // Benachrichtige Spieler wenn online
        Player online = Bukkit.getPlayer(playerName);
        if (online != null && online.isOnline()) {
            online.sendMessage("");
            online.sendMessage(ChatColor.GREEN + "✔ Du wurdest zur Whitelist hinzugefügt!");
            online.sendMessage(ChatColor.GRAY + "Von: " + sender.getName());
            online.sendMessage("");
        }

        // Log in Console
        Bukkit.getLogger().info("[Whitelist] " + playerName + " wurde von " + sender.getName() + " zur Whitelist hinzugefügt");
    }

    private void handleRemovePlayer(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!WhitelistManager.isWhitelisted(playerName, target.getUniqueId())) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + playerName + ChatColor.GRAY + " ist nicht auf der Whitelist.");
            return;
        }

        WhitelistManager.removeFromWhitelist(playerName);

        sender.sendMessage("");
        sender.sendMessage(PREFIX + ChatColor.RED + "Spieler " + ChatColor.YELLOW + playerName +
                ChatColor.RED + " wurde von der Whitelist entfernt.");
        sender.sendMessage(ChatColor.GRAY + "» Entfernt von: " + sender.getName());
        sender.sendMessage("");

        // Kicke Spieler wenn online und Whitelist aktiv
        Player online = Bukkit.getPlayer(playerName);
        if (online != null && online.isOnline() && WhitelistManager.isWhitelistEnabled()) {
            if (!online.hasPermission("dmb.whitelist.bypass")) {
                online.kickPlayer(ChatColor.RED + "" + ChatColor.BOLD + "VON WHITELIST ENTFERNT\n\n" +
                        ChatColor.GRAY + "Du wurdest von der Whitelist entfernt.\n" +
                        ChatColor.GRAY + "Kontaktiere einen Administrator für weitere Informationen.");
                sender.sendMessage(ChatColor.YELLOW + "» " + playerName + " wurde vom Server gekickt");
            }
        }

        Bukkit.getLogger().info("[Whitelist] " + playerName + " wurde von " + sender.getName() + " von der Whitelist entfernt");
    }

    private void handleListPlayers(CommandSender sender) {
        List<String> whitelisted = WhitelistManager.getWhitelistedPlayers();

        if (whitelisted.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Die Whitelist ist leer.");
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "WHITELIST (" + whitelisted.size() + ")" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        int count = 0;
        StringBuilder line = new StringBuilder();
        for (String name : whitelisted) {
            if (count > 0) line.append(ChatColor.GRAY + ", ");
            line.append(ChatColor.YELLOW).append(name);
            count++;

            // Zeige 5 Namen pro Zeile
            if (count % 5 == 0) {
                sender.sendMessage(ChatColor.GRAY + "» " + line);
                line = new StringBuilder();
            }
        }

        // Rest anzeigen
        if (line.length() > 0) {
            sender.sendMessage(ChatColor.GRAY + "» " + line);
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Insgesamt: " + ChatColor.YELLOW + whitelisted.size() +
                ChatColor.GRAY + " Spieler");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void handleCheckPlayer(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        boolean whitelisted = WhitelistManager.isWhitelisted(playerName, target.getUniqueId());
        boolean maintenanceBypass = WhitelistManager.hasMaintenanceBypass(playerName, target.getUniqueId());

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬ " + ChatColor.BOLD + "WHITELIST INFO" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Spieler: " + ChatColor.WHITE + playerName);
        sender.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.GRAY + target.getUniqueId());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Whitelist: " + (whitelisted ?
                ChatColor.GREEN + "✔ Ja" : ChatColor.RED + "✖ Nein"));
        sender.sendMessage(ChatColor.YELLOW + "Wartungs-Bypass: " + (maintenanceBypass ?
                ChatColor.GREEN + "✔ Ja" : ChatColor.RED + "✖ Nein"));

        Player online = Bukkit.getPlayer(playerName);
        if (online != null && online.isOnline()) {
            sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.GREEN + "Online");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.GRAY + "Offline");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void handleInfo(CommandSender sender) {
        boolean enabled = WhitelistManager.isWhitelistEnabled();
        List<String> whitelisted = WhitelistManager.getWhitelistedPlayers();
        int online = 0;

        for (String name : whitelisted) {
            Player p = Bukkit.getPlayer(name);
            if (p != null && p.isOnline()) online++;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬ " + ChatColor.BOLD + "WHITELIST STATUS" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Status: " + (enabled ?
                ChatColor.GREEN + "✔ Aktiviert" : ChatColor.RED + "✖ Deaktiviert"));
        sender.sendMessage(ChatColor.YELLOW + "Spieler auf Whitelist: " + ChatColor.WHITE + whitelisted.size());
        sender.sendMessage(ChatColor.YELLOW + "Davon online: " + ChatColor.WHITE + online);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Nutze " + ChatColor.GOLD + "/whitelist list" +
                ChatColor.GRAY + " für alle Spieler");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void handleReload(CommandSender sender) {
        WhitelistManager.reload();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Whitelist wurde neu geladen.");
    }

    private boolean validatePlayerArgument(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutze: /whitelist " + args[0] + " <Spieler>");
            return false;
        }
        return true;
    }

    private int kickNonWhitelistedPlayers() {
        int kicked = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("dmb.whitelist.bypass")) continue;
            if (!WhitelistManager.isWhitelisted(p.getName(), p.getUniqueId())) {
                p.kickPlayer(ChatColor.RED + "" + ChatColor.BOLD + "WHITELIST AKTIVIERT\n\n" +
                        ChatColor.GRAY + "Die Whitelist wurde aktiviert.\n" +
                        ChatColor.GRAY + "Du stehst nicht auf der Whitelist.");
                kicked++;
            }
        }
        return kicked;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "WHITELIST" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist on" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Aktiviert die Whitelist");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist off" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Deaktiviert die Whitelist");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist add <Spieler> [bypass]" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Fügt Spieler hinzu");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist remove <Spieler>" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Entfernt Spieler");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist list" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Zeigt alle Spieler");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist check <Spieler>" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Prüft einen Spieler");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist info" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Zeigt Status");
        sender.sendMessage(ChatColor.YELLOW + "/whitelist reload" + ChatColor.DARK_GRAY + " » " +
                ChatColor.GRAY + "Lädt neu");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dmb.whitelist")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("on", "off", "add", "remove", "list", "check", "info", "reload")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("add") || action.equals("hinzufügen")) {
                // Zeige alle Spieler die NICHT auf der Whitelist sind
                List<String> completions = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!WhitelistManager.isWhitelisted(p.getName(), p.getUniqueId())) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(p.getName());
                        }
                    }
                }
                return completions;
            } else if (action.equals("remove") || action.equals("entfernen") ||
                    action.equals("check") || action.equals("prüfen")) {
                // Zeige Spieler die AUF der Whitelist sind
                return WhitelistManager.getWhitelistedPlayers().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("hinzufügen"))) {
            return Arrays.asList("bypass");
        }

        return new ArrayList<>();
    }
}