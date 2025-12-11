package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class unmute implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dmb.unmute") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Verwendung: /unmute <Spieler>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
            return true;
        }

        String sql = "DELETE FROM mutes WHERE uuid = ?";
        int deleted = DatabaseManager.getInstance().executeUpdate(sql, target.getUniqueId().toString());

        if (deleted > 0) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " wurde entmutet.");
            target.sendMessage(ChatColor.GREEN + "Du wurdest entmutet und kannst wieder schreiben und sprechen!");
            Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde entmutet!");
        } else if (deleted == 0) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " ist nicht gemutet.");
        } else {
            sender.sendMessage(ChatColor.RED + "Fehler beim Entmuten!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}