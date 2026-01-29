package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.MuteManager;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final DatabaseManager database;
    private final LanguageManager language;
    private final MuteManager muteManager;

    public MuteCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.database = DatabaseManager.getInstance();
        this.language = plugin.getLanguageManager();
        this.muteManager = plugin.getMuteManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.mute") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(language.getMessage("minecraft.mute.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(language.getMessage("minecraft.mute.player-not-found")
                    .replace("{player}", targetName));
            return true;
        }

        // Dauer parsen
        String durationStr = args[1];
        long expireTime;

        try {
            expireTime = parseDuration(durationStr);
            if (expireTime == 0) {
                sender.sendMessage(language.getMessage("minecraft.mute.invalid-duration"));
                sendDurationExamples(sender);
                return true;
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(language.getMessage("minecraft.mute.invalid-duration-error")
                    .replace("{error}", e.getMessage()));
            return true;
        }

        // Grund zusammenfügen
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                .replace("_", " ");

        if (reason.trim().isEmpty()) {
            reason = language.getMessage("minecraft.mute.no-reason-given");
        }

        try {
            // Prüfe ob bereits gemutet
            if (muteManager.isMuted(target.getUniqueId())) {
                sender.sendMessage(language.getMessage("minecraft.mute.already-muted")
                        .replace("{player}", targetName));
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // Erstelle Mute
            String executorName = sender.getName();
            int muteId = database.createMute(
                    target.getUniqueId().toString(),
                    target.getName(),
                    reason,
                    executorName,
                    expireTime
            );

            if (muteId <= 0) {
                sender.sendMessage(language.getMessage("minecraft.mute.database-error"));
                return true;
            }

            // Aktiviere Mute im MuteManager (KORRIGIERT: mutePlayer statt mutePlaye)
            muteManager.mutePlayer(target.getUniqueId(), expireTime);

            // Nachricht an Spieler
            String duration = formatDuration(expireTime - System.currentTimeMillis());
            target.sendMessage(language.getMessage("minecraft.mute.muted")
                    .replace("{reason}", reason)
                    .replace("{duration}", duration));

            // Broadcast
            Bukkit.broadcastMessage(language.getMessage("minecraft.mute.broadcast")
                    .replace("{player}", target.getName())
                    .replace("{muter}", executorName)
                    .replace("{duration}", duration));

            // Bestätigung an Executor
            sender.sendMessage(language.getMessage("minecraft.mute.success")
                    .replace("{player}", target.getName())
                    .replace("{duration}", duration));

            plugin.getLogger().info("[Mute] " + targetName + " wurde von " + executorName +
                    " gemutet. Grund: " + reason + " | Dauer: " + duration);

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Erstellen des Mutes: " + e.getMessage());
            sender.sendMessage(language.getMessage("minecraft.mute.database-error"));
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(language.getMessage("minecraft.mute.usage"));
        sender.sendMessage(language.getMessage("minecraft.mute.examples-header"));
        sender.sendMessage(language.getMessage("minecraft.mute.example-1"));
        sender.sendMessage(language.getMessage("minecraft.mute.example-2"));
        sender.sendMessage(language.getMessage("minecraft.mute.example-3"));
    }

    private void sendDurationExamples(CommandSender sender) {
        sender.sendMessage(language.getMessage("minecraft.mute.valid-formats"));
        sender.sendMessage(language.getMessage("minecraft.mute.format-minutes"));
        sender.sendMessage(language.getMessage("minecraft.mute.format-hours"));
        sender.sendMessage(language.getMessage("minecraft.mute.format-days"));
        sender.sendMessage(language.getMessage("minecraft.mute.format-combined"));
    }

    /**
     * Parst Dauer-String (z.B. "10m", "1h", "2h30m")
     */
    private long parseDuration(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(language.getMessage("minecraft.mute.error-empty-duration"));
        }

        str = str.toLowerCase().trim();
        long totalMillis = 0;
        StringBuilder number = new StringBuilder();

        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (number.length() == 0) {
                    throw new IllegalArgumentException(
                            language.getMessage("minecraft.mute.error-no-number")
                                    .replace("{unit}", String.valueOf(c)));
                }

                try {
                    int value = Integer.parseInt(number.toString());
                    if (value < 0) {
                        throw new IllegalArgumentException(
                                language.getMessage("minecraft.mute.error-negative"));
                    }

                    switch (c) {
                        case 'd': totalMillis += value * 86400000L; break;
                        case 'h': totalMillis += value * 3600000L; break;
                        case 'm': totalMillis += value * 60000L; break;
                        case 's': totalMillis += value * 1000L; break;
                    }
                    number = new StringBuilder();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            language.getMessage("minecraft.mute.error-invalid-number")
                                    .replace("{number}", number.toString()));
                }
            } else if (c != ' ') {
                throw new IllegalArgumentException(
                        language.getMessage("minecraft.mute.error-invalid-char")
                                .replace("{char}", String.valueOf(c)));
            }
        }

        if (number.length() > 0) {
            throw new IllegalArgumentException(
                    language.getMessage("minecraft.mute.error-missing-unit")
                            .replace("{number}", number.toString()));
        }

        if (totalMillis == 0) {
            throw new IllegalArgumentException(
                    language.getMessage("minecraft.mute.error-zero-duration"));
        }

        return System.currentTimeMillis() + totalMillis;
    }

    /**
     * Formatiert Dauer zu lesbarem String
     */
    private String formatDuration(long millis) {
        if (millis < 0) millis = 0;

        long days = millis / 86400000L;
        long hours = (millis % 86400000L) / 3600000L;
        long minutes = (millis % 3600000L) / 60000L;
        long seconds = (millis % 60000L) / 1000L;

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
            // Spieler vorschlagen
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            // Dauer-Vorschläge
            return Arrays.asList("5m", "10m", "15m", "30m", "1h", "2h", "6h", "12h", "24h");
        }

        if (args.length == 3) {
            // Gründe aus Datenbank laden
            try {
                List<String> reasons = database.getPunishmentReasons("MUTE");
                for (String reason : reasons) {
                    completions.add(reason.replace(" ", "_"));
                }
            } catch (SQLException e) {
                // Fallback
                String defaultReasons = language.getMessage("minecraft.mute.default-reasons");
                completions.addAll(Arrays.asList(defaultReasons.split(",")));
            }

            String customReason = language.getMessage("minecraft.mute.custom-reason");
            completions.add(customReason.replace(" ", "_"));

            String input = args[2].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        return Collections.emptyList();
    }
}