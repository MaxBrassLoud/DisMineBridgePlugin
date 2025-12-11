package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            sender.sendMessage(ChatColor.RED + "Benutzung: /ban <Spieler> <Grund> <Dauer z.B. 1h30m>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        String durationStr = args[args.length - 1];
        Instant expire;

        try {
            expire = parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige Dauer. Beispiel: 1h30m, 45m, 2d");
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
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
        sender.sendMessage(ChatColor.GREEN + "✓ Ban erfolgreich gesetzt. Dauer: " + timeLeft);
        Bukkit.broadcastMessage(ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde gebannt!");
        return true;
    }

    private Instant parseDuration(String str) {
        str = str.toLowerCase().trim();
        long seconds = 0;
        StringBuilder number = new StringBuilder();

        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.isEmpty()) {
                    throw new IllegalArgumentException("Keine Zahl vor Zeiteinheit");
                }
                int value = Integer.parseInt(number.toString());
                switch (c) {
                    case 'd' -> seconds += value * 86400L;
                    case 'h' -> seconds += value * 3600L;
                    case 'm' -> seconds += value * 60L;
                    case 's' -> seconds += value;
                    default -> throw new IllegalArgumentException("Unbekannte Zeiteinheit: " + c);
                }
                number = new StringBuilder();
            }
        }

        if (!number.isEmpty()) {
            throw new IllegalArgumentException("Zeiteinheit fehlt nach Zahl");
        }

        if (seconds == 0) {
            throw new IllegalArgumentException("Dauer muss größer als 0 sein");
        }

        return Instant.now().plusSeconds(seconds);
    }

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
        if (args.length == 1) {
            List<String> online = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    online.add(p.getName());
                }
            }
            return online;
        }
        return Collections.emptyList();
    }
}