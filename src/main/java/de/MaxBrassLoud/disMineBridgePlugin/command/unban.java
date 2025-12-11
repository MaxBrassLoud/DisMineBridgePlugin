package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class unban implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dmb.unban") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /unban <Spieler>");
            return true;
        }

        String playerName = args[0];

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.RED + "Spieler wurde nicht gefunden!");
            return true;
        }

        String sql = "UPDATE bans SET pardon = 1 WHERE uuid = ? AND pardon = 0";
        int rows = DatabaseManager.getInstance().executeUpdate(sql, offlinePlayer.getUniqueId().toString());

        if (rows > 0) {
            sender.sendMessage(ChatColor.GREEN + "Der Spieler " + playerName + " wurde entbannt.");
            Bukkit.getLogger().info("[DisMineBridge] " + playerName + " wurde entbannt von " + sender.getName());
            Bukkit.broadcastMessage(ChatColor.GOLD + playerName + ChatColor.RED + " wurde entbannt!");
        } else if (rows == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Der Spieler ist nicht gebannt oder bereits entbannt.");
        } else {
            sender.sendMessage(ChatColor.RED + "Fehler beim Entbannen. Siehe Konsole.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            // Zeige Offline-Spieler die schon mal auf dem Server waren
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.getName().toLowerCase().startsWith(input)) {
                    completions.add(op.getName());
                }
            }
        }

        return completions;
    }
}