package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command zum Verwalten von Straf-Gründen
 * /punishreasons add <BAN|MUTE|KICK|WARN> <Name> <Dauer> [Beschreibung]
 * /punishreasons remove <ID>
 * /punishreasons list [BAN|MUTE|KICK|WARN]
 * /punishreasons edit <ID> <duration|name|description> <Wert>
 */
public class PunishmentReasonsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("dmb.punishreasons")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add", "hinzufügen" -> handleAdd(sender, args);
            case "remove", "delete", "entfernen" -> handleRemove(sender, args);
            case "list", "liste" -> handleList(sender, args);
            case "edit", "bearbeiten" -> handleEdit(sender, args);
            case "enable", "aktivieren" -> handleToggle(sender, args, true);
            case "disable", "deaktivieren" -> handleToggle(sender, args, false);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * Fügt einen neuen Grund hinzu
     * /punishreasons add BAN Hacking 7d "Verwendung von Cheats"
     */
    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Nutze: /punishreasons add <TYP> <Name> <Dauer> [Beschreibung]");
            sender.sendMessage(ChatColor.GRAY + "Beispiel: /punishreasons add BAN Hacking 7d \"Verwendung von Cheats\"");
            return;
        }

        String type = args[1].toUpperCase();
        if (!isValidType(type)) {
            sender.sendMessage(ChatColor.RED + "Ungültiger Typ! Nutze: BAN, MUTE, KICK, WARN");
            return;
        }

        String name = args[2];
        String durationStr = args[3];
        long duration = parseDuration(durationStr);

        if (duration < 0) {
            sender.sendMessage(ChatColor.RED + "Ungültige Dauer! Format: 7d, 12h, 30m");
            return;
        }

        String description = "";
        if (args.length > 4) {
            description = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        }

        // Füge in Datenbank ein
        try {
            String sql = "INSERT INTO punishment_reasons (type, name, description, default_duration, severity, sort_order, created_at, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            // Hole nächste Sort-Order
            int nextSort = getNextSortOrder(type);

            int result = DatabaseManager.getInstance().executeUpdate(sql,
                    type,
                    name,
                    description,
                    duration,
                    2, // Standard-Severity
                    nextSort,
                    System.currentTimeMillis(),
                    sender.getName()
            );

            if (result > 0) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GREEN + "✔ Grund erfolgreich hinzugefügt!");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.YELLOW + "Typ: " + ChatColor.WHITE + type);
                sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + name);
                sender.sendMessage(ChatColor.YELLOW + "Dauer: " + ChatColor.WHITE + formatDuration(duration));
                if (!description.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Beschreibung: " + ChatColor.WHITE + description);
                }
                sender.sendMessage("");
            } else {
                sender.sendMessage(ChatColor.RED + "✖ Fehler beim Hinzufügen!");
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Datenbankfehler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Entfernt einen Grund
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutze: /punishreasons remove <ID>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);

            // Prüfe ob Grund existiert
            String checkSql = "SELECT name FROM punishment_reasons WHERE id = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(checkSql, id);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                sender.sendMessage(ChatColor.RED + "Grund mit ID " + id + " nicht gefunden!");
                rs.close();
                ps.close();
                return;
            }

            String name = rs.getString("name");
            rs.close();
            ps.close();

            // Lösche Grund
            String deleteSql = "DELETE FROM punishment_reasons WHERE id = ?";
            int result = DatabaseManager.getInstance().executeUpdate(deleteSql, id);

            if (result > 0) {
                sender.sendMessage(ChatColor.GREEN + "✔ Grund '" + name + "' (ID: " + id + ") wurde entfernt!");
            } else {
                sender.sendMessage(ChatColor.RED + "✖ Fehler beim Entfernen!");
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige ID!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Datenbankfehler: " + e.getMessage());
        }
    }

    /**
     * Listet alle Gründe auf
     */
    private void handleList(CommandSender sender, String[] args) {
        String filterType = null;
        if (args.length > 1) {
            filterType = args[1].toUpperCase();
            if (!isValidType(filterType)) {
                sender.sendMessage(ChatColor.RED + "Ungültiger Typ! Nutze: BAN, MUTE, KICK, WARN");
                return;
            }
        }

        try {
            String sql;
            PreparedStatement ps;

            if (filterType != null) {
                sql = "SELECT * FROM punishment_reasons WHERE type = ? ORDER BY sort_order, id";
                ps = DatabaseManager.getInstance().prepareStatement(sql, filterType);
            } else {
                sql = "SELECT * FROM punishment_reasons ORDER BY type, sort_order, id";
                ps = DatabaseManager.getInstance().prepareStatement(sql);
            }

            ResultSet rs = ps.executeQuery();

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬ " + ChatColor.BOLD + "STRAF-GRÜNDE" +
                    ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            String currentType = "";
            int count = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String desc = rs.getString("description");
                long duration = rs.getLong("default_duration");
                boolean enabled = rs.getBoolean("enabled");

                // Typ-Header
                if (!type.equals(currentType)) {
                    if (!currentType.isEmpty()) sender.sendMessage("");
                    sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "▸ " + type);
                    currentType = type;
                }

                // Grund-Info
                String status = enabled ? ChatColor.GREEN + "✔" : ChatColor.RED + "✖";
                sender.sendMessage(status + ChatColor.GRAY + " [" + ChatColor.WHITE + id + ChatColor.GRAY + "] " +
                        ChatColor.YELLOW + name + ChatColor.DARK_GRAY + " - " +
                        ChatColor.WHITE + formatDuration(duration));

                if (desc != null && !desc.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "    " + desc);
                }

                count++;
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Insgesamt: " + ChatColor.YELLOW + count + ChatColor.GRAY + " Gründe");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            rs.close();
            ps.close();

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Datenbankfehler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Bearbeitet einen Grund
     */
    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Nutze: /punishreasons edit <ID> <duration|name|description> <Wert>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String field = args[2].toLowerCase();
            String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            String sql = switch (field) {
                case "duration", "dauer" -> {
                    long duration = parseDuration(value);
                    if (duration < 0) {
                        sender.sendMessage(ChatColor.RED + "Ungültige Dauer!");
                        yield null;
                    }
                    yield "UPDATE punishment_reasons SET default_duration = " + duration + " WHERE id = " + id;
                }
                case "name" -> "UPDATE punishment_reasons SET name = '" + value + "' WHERE id = " + id;
                case "description", "desc" -> "UPDATE punishment_reasons SET description = '" + value + "' WHERE id = " + id;
                default -> {
                    sender.sendMessage(ChatColor.RED + "Ungültiges Feld! Nutze: duration, name, description");
                    yield null;
                }
            };

            if (sql != null) {
                int result = DatabaseManager.getInstance().executeUpdate(sql);
                if (result > 0) {
                    sender.sendMessage(ChatColor.GREEN + "✔ Grund erfolgreich bearbeitet!");
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Grund nicht gefunden!");
                }
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige ID!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Datenbankfehler: " + e.getMessage());
        }
    }

    /**
     * Aktiviert/Deaktiviert einen Grund
     */
    private void handleToggle(CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutze: /punishreasons " + args[0] + " <ID>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);

            String sql = "UPDATE punishment_reasons SET enabled = ? WHERE id = ?";
            int result = DatabaseManager.getInstance().executeUpdate(sql, enabled ? 1 : 0, id);

            if (result > 0) {
                sender.sendMessage(ChatColor.GREEN + "✔ Grund wurde " +
                        (enabled ? "aktiviert" : "deaktiviert") + "!");
            } else {
                sender.sendMessage(ChatColor.RED + "✖ Grund nicht gefunden!");
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige ID!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Datenbankfehler: " + e.getMessage());
        }
    }

    /**
     * Zeigt Hilfe an
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬ " + ChatColor.BOLD + "PUNISHMENT REASONS" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/punishreasons list [TYP]");
        sender.sendMessage(ChatColor.GRAY + "  Listet alle Gründe auf");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/punishreasons add <TYP> <Name> <Dauer> [Beschreibung]");
        sender.sendMessage(ChatColor.GRAY + "  Fügt einen neuen Grund hinzu");
        sender.sendMessage(ChatColor.GRAY + "  Beispiel: /punishreasons add BAN Hacking 7d");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/punishreasons remove <ID>");
        sender.sendMessage(ChatColor.GRAY + "  Entfernt einen Grund");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/punishreasons edit <ID> <duration|name|description> <Wert>");
        sender.sendMessage(ChatColor.GRAY + "  Bearbeitet einen Grund");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/punishreasons enable/disable <ID>");
        sender.sendMessage(ChatColor.GRAY + "  Aktiviert/Deaktiviert einen Grund");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    // ============================================================
    //  HELPER METHODS
    // ============================================================

    private boolean isValidType(String type) {
        return type.equals("BAN") || type.equals("MUTE") || type.equals("KICK") || type.equals("WARN");
    }

    private long parseDuration(String str) {
        try {
            str = str.toLowerCase().trim();
            long total = 0;
            StringBuilder number = new StringBuilder();

            for (char c : str.toCharArray()) {
                if (Character.isDigit(c)) {
                    number.append(c);
                } else if (number.length() > 0) {
                    int value = Integer.parseInt(number.toString());
                    switch (c) {
                        case 'd' -> total += value * 86400000L;
                        case 'h' -> total += value * 3600000L;
                        case 'm' -> total += value * 60000L;
                        case 's' -> total += value * 1000L;
                    }
                    number = new StringBuilder();
                }
            }

            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    private String formatDuration(long millis) {
        if (millis == 0) return "Sofort";

        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");

        return sb.toString().trim();
    }

    private int getNextSortOrder(String type) {
        try {
            String sql = "SELECT MAX(sort_order) as max_sort FROM punishment_reasons WHERE type = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, type);
            ResultSet rs = ps.executeQuery();

            int maxSort = 0;
            if (rs.next()) {
                maxSort = rs.getInt("max_sort");
            }

            rs.close();
            ps.close();

            return maxSort + 1;

        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dmb.punishreasons")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "edit", "enable", "disable")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("list")) {
                return Arrays.asList("BAN", "MUTE", "KICK", "WARN")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toUpperCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return Arrays.asList("duration", "name", "description")
                    .stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}