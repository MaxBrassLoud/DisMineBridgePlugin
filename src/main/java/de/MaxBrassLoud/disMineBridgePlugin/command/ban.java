package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class ban implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.ban") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /ban <Spieler> <Dauer> <Grund>");
            sender.sendMessage(ChatColor.GRAY + "Beispiele:");
            sender.sendMessage(ChatColor.YELLOW + "  /ban Steve 7d Hacking");
            sender.sendMessage(ChatColor.YELLOW + "  /ban Alex 1h30m \"Schwere Beleidigung\"");
            sender.sendMessage(ChatColor.YELLOW + "  /ban Bob 2d12h Verwendung von Exploits");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        // Dauer ist jetzt das 2. Argument
        String durationStr = args[1];
        Instant expire;

        try {
            expire = parseDuration(durationStr);
            if (expire == null) {
                sender.sendMessage(ChatColor.RED + "Ungültige Dauer!");
                sender.sendMessage(ChatColor.GRAY + "Gültige Formate:");
                sender.sendMessage(ChatColor.YELLOW + "  • 7d (7 Tage)");
                sender.sendMessage(ChatColor.YELLOW + "  • 12h (12 Stunden)");
                sender.sendMessage(ChatColor.YELLOW + "  • 30m (30 Minuten)");
                sender.sendMessage(ChatColor.YELLOW + "  • 7d12h (7 Tage und 12 Stunden)");
                sender.sendMessage(ChatColor.YELLOW + "  • 1h30m15s (1 Stunde, 30 Min, 15 Sek)");
                return true;
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige Dauer: " + e.getMessage());
            return true;
        }

        // Grund ist ab dem 3. Argument (alles zusammengefügt, Underscores → Leerzeichen)
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                .replace("_", " ")
                .replace("/n", "\n");  // /n wird zu echter neuer Zeile

        if (reason.trim().isEmpty()) reason = "Kein Grund angegeben";

        String timeLeft = formatDuration(Duration.between(Instant.now(), expire));
        UUID targetUuid = target != null ? target.getUniqueId() : null;

        // Speichere in Datenbank
        String sql = "INSERT INTO bans (uuid, name, reason, banner, expire) VALUES (?, ?, ?, ?, ?)";
        int result = DatabaseManager.getInstance().executeUpdate(sql,
                targetUuid != null ? targetUuid.toString() : null,
                targetName,
                reason,
                sender.getName(),
                expire.toEpochMilli()
        );

        if (result < 0) {
            sender.sendMessage(ChatColor.RED + "❌ Fehler beim Speichern in der Datenbank!");
            return true;
        }

        // Kick wenn online
        if (target != null && target.isOnline()) {
            String finalReason = reason;
            target.kickPlayer(ChatColor.RED + "Du wurdest gebannt!\n" +
                    ChatColor.GRAY + "Grund: " + ChatColor.WHITE + finalReason + "\n" +
                    ChatColor.GRAY + "Verbleibend: " + ChatColor.YELLOW + timeLeft);
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + targetName + ChatColor.RED + " wurde gebannt!");
        sender.sendMessage(ChatColor.GREEN + "✓ Ban erfolgreich gesetzt. Dauer: " + ChatColor.YELLOW + timeLeft);

        return true;
    }

    /**
     * Verbesserte Dauer-Parsing-Methode
     * Unterstützt: 7d, 12h, 30m, 7d12h, 1h30m15s etc.
     */
    private Instant parseDuration(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Dauer darf nicht leer sein");
        }

        str = str.toLowerCase().trim();
        long totalSeconds = 0;
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (number.length() == 0) {
                    throw new IllegalArgumentException("Keine Zahl vor Zeiteinheit '" + c + "'");
                }

                try {
                    int value = Integer.parseInt(number.toString());
                    if (value < 0) {
                        throw new IllegalArgumentException("Negative Werte sind nicht erlaubt");
                    }

                    switch (c) {
                        case 'd' -> totalSeconds += value * 86400L;  // Tage
                        case 'h' -> totalSeconds += value * 3600L;   // Stunden
                        case 'm' -> totalSeconds += value * 60L;     // Minuten
                        case 's' -> totalSeconds += value;           // Sekunden
                    }
                    number = new StringBuilder(); // Reset für nächste Zahl
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Ungültige Zahl: " + number);
                }
            } else if (c != ' ') {
                throw new IllegalArgumentException("Ungültiges Zeichen: '" + c + "' (erlaubt: d, h, m, s)");
            }
        }

        if (number.length() > 0) {
            throw new IllegalArgumentException("Zeiteinheit fehlt nach Zahl: " + number);
        }

        if (totalSeconds == 0) {
            throw new IllegalArgumentException("Dauer muss größer als 0 sein");
        }

        return Instant.now().plusSeconds(totalSeconds);
    }

    /**
     * Formatiert Duration zu lesbarem String
     */
    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        if (totalSeconds < 0) totalSeconds = 0;

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.isEmpty() || seconds > 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Spieler-Namen vorschlagen
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            // Dauer-Vorschläge beim 2. Argument
            return Arrays.asList("1h", "12h", "1d", "3d", "7d", "14d", "30d", "1h30m", "7d12h");
        }

        if (args.length == 3) {
            // Gründe aus Datenbank laden
            completions = loadReasonsFromDatabase("BAN");

            // Filtern basierend auf Input
            String input = args[2].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        return Collections.emptyList();
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
            reasons.addAll(Arrays.asList("Hacking", "Griefing", "Beleidigung", "Spam", "Werbung", "Betrug"));
        }

        // Füge "Eigener_Grund" als Option hinzu
        reasons.add("Eigener_Grund");

        return reasons;
    }
}