package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class kick implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.kick") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /kick <Spieler> <Grund>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden oder nicht online!");
            return true;
        }

        // Grund zusammenbauen
        String reason = String.join(" ", args).substring(args[0].length()).trim();
        if (reason.isEmpty()) reason = "Kein Grund angegeben";

        // In Datenbank speichern
        String sql = "INSERT INTO kicks (uuid, name, reason, kicker) VALUES (?, ?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                target.getUniqueId().toString(),
                target.getName(),
                reason,
                sender.getName()
        );

        if (result < 0) {
            sender.sendMessage(ChatColor.RED + "Fehler beim Speichern des Kicks!");
            return true;
        }

        // Kick Message erstellen und Spieler kicken
        String kickMessage = ChatColor.RED + "Du wurdest vom Server gekickt!\n" +
                ChatColor.GRAY + "Grund: " + ChatColor.YELLOW + reason;
        target.kickPlayer(kickMessage);

        // Broadcast an Server (ohne Grund)
        Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde gekickt!");

        sender.sendMessage(ChatColor.GREEN + "Kick wurde in die Datenbank eingetragen.");

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