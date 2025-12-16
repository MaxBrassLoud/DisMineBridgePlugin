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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
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
            sender.sendMessage(ChatColor.GRAY + "Beispiele:");
            sender.sendMessage(ChatColor.YELLOW + "  /kick Steve AFK");
            sender.sendMessage(ChatColor.YELLOW + "  /kick Alex Unangemessenes_Verhalten");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden oder nicht online!");
            return true;
        }

        // Grund zusammenbauen (ersetze Underscores wieder mit Leerzeichen)
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                .replace("_", " ");
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
            sender.sendMessage(ChatColor.RED + "❌ Fehler beim Speichern des Kicks!");
            return true;
        }

        // Kick Message erstellen und Spieler kicken
        String kickMessage = ChatColor.RED + "Du wurdest vom Server gekickt!\n" +
                ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason;
        target.kickPlayer(kickMessage);

        // Broadcast an Server
        Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde gekickt!");

        sender.sendMessage(ChatColor.GREEN + "✓ Kick wurde in die Datenbank eingetragen.");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Spieler vorschlagen
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Gründe aus Datenbank laden
            completions = loadReasonsFromDatabase("KICK");

            // Filtern basierend auf Input
            String input = args[1].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        return completions;
    }

    /**
     * Lädt Gründe aus der Datenbank für Tab-Completion
     */
    private List<String> loadReasonsFromDatabase(String type) {
        List<String> reasons = new ArrayList<>();

        try {
            String sql = "SELECT name FROM punishment_reasons WHERE type = ? AND enabled = 1 ORDER BY sort_order";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, type);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String name = rs.getString("name");
                // Ersetze Leerzeichen mit Underscore für bessere Tab-Completion
                reasons.add(name.replace(" ", "_"));
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            // Fallback zu Standard-Gründen
            reasons.addAll(Arrays.asList("AFK", "Unangemessenes_Verhalten", "Namensänderung_erforderlich"));
        }

        // Füge "Eigener_Grund" als Option hinzu
        reasons.add("Eigener_Grund");

        return reasons;
    }
}