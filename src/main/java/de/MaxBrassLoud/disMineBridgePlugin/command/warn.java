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

public class warn implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.warn") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /warn <Spieler> <Grund>");
            sender.sendMessage(ChatColor.GRAY + "Beispiele:");
            sender.sendMessage(ChatColor.YELLOW + "  /warn Steve Regelverstoß");
            sender.sendMessage(ChatColor.YELLOW + "  /warn Alex Fehlverhalten");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden oder nicht online!");
            return true;
        }

        // Grund zusammenbauen (ersetze Underscores wieder mit Leerzeichen, /n mit neuer Zeile)
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                .replace("_", " ")
                .replace("/n", "\n");
        if (reason.trim().isEmpty()) reason = "Kein Grund angegeben";

        // Speichere in Datenbank
        String sql = "INSERT INTO warns (uuid, name, reason, warner) VALUES (?, ?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                target.getUniqueId().toString(),
                target.getName(),
                reason,
                sender.getName()
        );

        if (result < 0) {
            sender.sendMessage(ChatColor.RED + "Fehler beim Speichern der Verwarnung!");
            return true;
        }

        // Zähle Gesamtanzahl der Verwarnungen
        int totalWarns = getWarnCount(target);

        // Nachricht an Spieler
        target.sendMessage("");
        target.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "      DU WURDEST VERWARNT!");
        target.sendMessage("");
        target.sendMessage(ChatColor.YELLOW + "Grund: " + ChatColor.WHITE + reason);
        target.sendMessage(ChatColor.YELLOW + "Von: " + ChatColor.WHITE + sender.getName());
        target.sendMessage(ChatColor.YELLOW + "Verwarnungen insgesamt: " + ChatColor.RED + totalWarns);
        target.sendMessage("");

        // Warnung bei vielen Verwarnungen
        if (totalWarns >= 3) {
            target.sendMessage(ChatColor.RED + "⚠ " + ChatColor.DARK_RED + ChatColor.BOLD +
                    "ACHTUNG: " + ChatColor.RED + "Weitere Verwarnungen");
            target.sendMessage(ChatColor.RED + "   führen zu einem automatischen Ban!");
        }

        target.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        target.sendMessage("");

        // Broadcast an Server
        Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde verwarnt!");

        sender.sendMessage(ChatColor.GREEN + "✓ Warnung wurde eingetragen.");
        sender.sendMessage(ChatColor.GRAY + "Spieler hat nun " + ChatColor.YELLOW + totalWarns +
                ChatColor.GRAY + " Verwarnungen.");

        return true;
    }

    /**
     * Zählt die Anzahl der Verwarnungen eines Spielers
     */
    private int getWarnCount(Player player) {
        try {
            String sql = "SELECT COUNT(*) as count FROM warns WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql,
                    player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            int count = 0;
            if (rs.next()) {
                count = rs.getInt("count");
            }

            rs.close();
            ps.close();

            return count;

        } catch (Exception e) {
            return 0;
        }
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
            completions = loadReasonsFromDatabase("WARN");

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
            reasons.addAll(Arrays.asList("Regelverstoß", "Fehlverhalten", "Spamming", "Provokation"));
        }

        // Füge "Eigener_Grund" als Option hinzu
        reasons.add("Eigener_Grund");

        return reasons;
    }
}