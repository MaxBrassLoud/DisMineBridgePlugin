package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Verwendung: /mute <Spieler> <Dauer> <Grund>");
            sender.sendMessage(ChatColor.GRAY + "Beispiele:");
            sender.sendMessage(ChatColor.YELLOW + "  /mute Steve 30m Spam");
            sender.sendMessage(ChatColor.YELLOW + "  /mute Alex 1h30m \"Schwere Beleidigung\"");
            sender.sendMessage(ChatColor.YELLOW + "  /mute Bob 2h Caps-Spam");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
            return true;
        }

        String durationStr = args[1];
        long durationMillis;

        try {
            durationMillis = parseDuration(durationStr);
            if (durationMillis <= 0) {
                sender.sendMessage(ChatColor.RED + "Dauer muss größer als 0 sein!");
                return true;
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige Dauer: " + e.getMessage());
            sender.sendMessage(ChatColor.GRAY + "Gültige Formate:");
            sender.sendMessage(ChatColor.YELLOW + "  • 30m (30 Minuten)");
            sender.sendMessage(ChatColor.YELLOW + "  • 1h (1 Stunde)");
            sender.sendMessage(ChatColor.YELLOW + "  • 1h30m (1 Stunde 30 Minuten)");
            sender.sendMessage(ChatColor.YELLOW + "  • 2h15m30s (2 Std, 15 Min, 30 Sek)");
            return true;
        }

        // Grund ab Argument 3 (Underscores → Leerzeichen, /n → neue Zeile)
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                .replace("_", " ")
                .replace("/n", "\n");

        if (reason.trim().isEmpty()) reason = "Kein Grund angegeben";

        long expire = Instant.now().plusMillis(durationMillis).toEpochMilli();

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

        String durationFormatted = formatDuration(durationMillis);

        sender.sendMessage(ChatColor.GREEN + target.getName() + " wurde für " +
                ChatColor.YELLOW + durationFormatted + ChatColor.GREEN + " gemutet.");
        Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde gemutet!");

        target.sendMessage(ChatColor.RED + "Du wurdest gemutet!\n" +
                ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.GRAY + "Dauer: " + ChatColor.YELLOW + durationFormatted + "\n" +
                ChatColor.GRAY + "Du kannst weder schreiben noch im Voice-Chat sprechen.");

        return true;
    }

    /**
     * Parst Dauer-String zu Millisekunden
     * Unterstützt: 30m, 1h, 2h30m, 1h30m15s etc.
     */
    private long parseDuration(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Dauer darf nicht leer sein");
        }

        str = str.toLowerCase().trim();
        long totalMillis = 0;
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (number.length() == 0) {
                    throw new IllegalArgumentException("Keine Zahl vor '" + c + "'");
                }

                try {
                    int value = Integer.parseInt(number.toString());
                    if (value < 0) {
                        throw new IllegalArgumentException("Negative Werte nicht erlaubt");
                    }

                    switch (c) {
                        case 'd' -> totalMillis += value * 86400000L;
                        case 'h' -> totalMillis += value * 3600000L;
                        case 'm' -> totalMillis += value * 60000L;
                        case 's' -> totalMillis += value * 1000L;
                    }
                    number = new StringBuilder();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Ungültige Zahl: " + number);
                }
            } else if (c != ' ') {
                throw new IllegalArgumentException("Ungültiges Zeichen: '" + c + "'");
            }
        }

        if (number.length() > 0) {
            throw new IllegalArgumentException("Zeiteinheit fehlt nach: " + number);
        }

        return totalMillis;
    }

    /**
     * Formatiert Millisekunden zu lesbarem Format
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.length() == 0 && secs > 0) sb.append(secs).append("s");

        return sb.toString().trim();
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
            // Dauer-Vorschläge
            completions.addAll(Arrays.asList(
                    "15m", "30m", "1h", "2h", "3h", "12h", "24h",
                    "1h30m", "2h30m", "1d", "3d"
            ));
        } else if (args.length == 3) {
            // Gründe aus Datenbank laden
            completions = loadReasonsFromDatabase("MUTE");

            // Filtern basierend auf Input
            String input = args[2].toLowerCase();
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
            reasons.addAll(Arrays.asList("Spam", "Beleidigung", "Caps", "Werbung", "Provokation"));
        }

        // Füge "Eigener_Grund" als Option hinzu
        reasons.add("Eigener_Grund");

        return reasons;
    }
}