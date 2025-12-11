package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class mute implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dmb.mute") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Verwendung: /mute <Spieler> <Dauer in Minuten> [Grund]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
            return true;
        }

        long durationMinutes;
        try {
            durationMinutes = Long.parseLong(args[1]);
            if (durationMinutes <= 0) {
                sender.sendMessage(ChatColor.RED + "Dauer muss größer als 0 sein!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige Zahl für Dauer!");
            return true;
        }

        String reason = args.length >= 3
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "Kein Grund angegeben";

        long expire = Instant.now().plusSeconds(durationMinutes * 60).toEpochMilli();

        // Speichere in Datenbank
        String sql = "INSERT INTO mutes (uuid, reason, expire) VALUES (?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                target.getUniqueId().toString(),
                reason,
                expire
        );

        if (result < 0) {
            sender.sendMessage(ChatColor.RED + "Fehler beim Muten!");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + target.getName() + " wurde für " + durationMinutes + " Minuten gemutet.");
        Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde gemutet!");
        target.sendMessage(ChatColor.RED + "Du wurdest gemutet!\n" +
                ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.GRAY + "Dauer: " + ChatColor.YELLOW + durationMinutes + " Minuten\n" +
                ChatColor.GRAY + "Du kannst weder schreiben noch im Voice-Chat sprechen.");

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
        } else if (args.length == 2) {
            completions.addAll(Arrays.asList("5", "10", "30", "60", "1440"));
        }

        return completions;
    }
}