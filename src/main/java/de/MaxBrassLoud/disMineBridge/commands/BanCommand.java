package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

public class BanCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public BanCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
        this.language = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.ban") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(language.getMessage("minecraft.ban.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        // Dauer parsen
        String durationStr = args[1];
        Instant expire;
        boolean isPermanent = false;

        // Prüfe auf permanenten Ban
        if (durationStr.equalsIgnoreCase("perm") ||
                durationStr.equalsIgnoreCase("permanent") ||
                durationStr.equalsIgnoreCase("forever")) {
            isPermanent = true;
            expire = Instant.ofEpochMilli(-1); // -1 = permanent
        } else {
            try {
                expire = parseDuration(durationStr);
                if (expire == null) {
                    sender.sendMessage(language.getMessage("minecraft.ban.invalid-duration"));
                    sendDurationExamples(sender);
                    return true;
                }
            } catch (IllegalArgumentException e) {
                sender.sendMessage(language.getMessage("minecraft.ban.invalid-duration-error")
                        .replace("{error}", e.getMessage()));
                return true;
            }
        }

        // Grund zusammenfügen
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                .replace("_", " ")
                .replace("/n", "\n");

        if (reason.trim().isEmpty()) {
            reason = language.getMessage("minecraft.ban.no-reason-given");
        }

        // Prüfe ob Spieler bereits gebannt ist
        try {
            String uuid = null;

            // Versuche UUID zu bekommen (online oder aus Datenbank)
            if (target != null && target.isOnline()) {
                uuid = target.getUniqueId().toString();
            } else {
                // Offline-Spieler - suche in Datenbank
                uuid = database.getMinecraftUuidByName(targetName);

                if (uuid == null) {
                    // Spieler war noch nie auf dem Server
                    sender.sendMessage(language.getMessage("minecraft.ban.player-never-joined")
                            .replace("{player}", targetName));
                    return true;
                }
            }

            // Prüfe ob bereits gebannt
            if (database.hasActiveBan(uuid)) {
                sender.sendMessage(language.getMessage("minecraft.ban.already-banned")
                        .replace("{player}", targetName));
                return true;
            }

            // Erstelle User-IDs
            int targetUserId = database.createOrGetUser(uuid);
            int bannerId = sender instanceof Player ?
                    database.createOrGetUser(((Player) sender).getUniqueId().toString()) : 0;

            // Erstelle Ban
            int banId = database.createBan(
                    targetUserId,
                    uuid,
                    targetName,
                    reason,
                    bannerId,
                    sender.getName(),
                    expire.toEpochMilli()
            );

            if (banId <= 0) {
                sender.sendMessage(language.getMessage("minecraft.ban.database-error"));
                return true;
            }

            // Kick wenn online
            if (target != null && target.isOnline()) {
                String timeLeft = isPermanent ?
                        language.getMessage("minecraft.ban.permanent") :
                        formatDuration(Duration.between(Instant.now(), expire));

                target.kickPlayer(language.getMessage("minecraft.ban.kick-message")
                        .replace("{reason}", reason)
                        .replace("{time}", timeLeft));
            }

            // Broadcast und Bestätigung
            String timeLeft = isPermanent ?
                    language.getMessage("minecraft.ban.permanent") :
                    formatDuration(Duration.between(Instant.now(), expire));

            String broadcastMsg = isPermanent ?
                    language.getMessage("minecraft.ban.broadcast-permanent") :
                    language.getMessage("minecraft.ban.broadcast");

            Bukkit.broadcastMessage(broadcastMsg
                    .replace("{player}", targetName)
                    .replace("{banner}", sender.getName())
                    .replace("{time}", timeLeft));

            String successMsg = isPermanent ?
                    language.getMessage("minecraft.ban.success-permanent") :
                    language.getMessage("minecraft.ban.success");

            sender.sendMessage(successMsg
                    .replace("{player}", targetName)
                    .replace("{time}", timeLeft));

            plugin.getLogger().info("[Ban] " + targetName + " wurde von " + sender.getName() +
                    " gebannt. Grund: " + reason + " | Dauer: " + timeLeft);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen des Bans", e);
            sender.sendMessage(language.getMessage("minecraft.ban.database-error"));
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(language.getMessage("minecraft.ban.usage"));
        sender.sendMessage(language.getMessage("minecraft.ban.examples-header"));
        sender.sendMessage(language.getMessage("minecraft.ban.example-1"));
        sender.sendMessage(language.getMessage("minecraft.ban.example-2"));
        sender.sendMessage(language.getMessage("minecraft.ban.example-3"));
    }

    private void sendDurationExamples(CommandSender sender) {
        sender.sendMessage(language.getMessage("minecraft.ban.valid-formats"));
        sender.sendMessage(language.getMessage("minecraft.ban.format-days"));
        sender.sendMessage(language.getMessage("minecraft.ban.format-hours"));
        sender.sendMessage(language.getMessage("minecraft.ban.format-minutes"));
        sender.sendMessage(language.getMessage("minecraft.ban.format-combined-1"));
        sender.sendMessage(language.getMessage("minecraft.ban.format-combined-2"));
        sender.sendMessage(language.getMessage("minecraft.ban.format-permanent"));
    }

    /**
     * Verbesserte Dauer-Parsing-Methode
     * Unterstützt: 7d, 12h, 30m, 7d12h, 1h30m15s etc.
     */
    private Instant parseDuration(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(language.getMessage("minecraft.ban.error-empty-duration"));
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
                    throw new IllegalArgumentException(
                            language.getMessage("minecraft.ban.error-no-number").replace("{unit}", String.valueOf(c)));
                }

                try {
                    int value = Integer.parseInt(number.toString());
                    if (value < 0) {
                        throw new IllegalArgumentException(language.getMessage("minecraft.ban.error-negative"));
                    }

                    switch (c) {
                        case 'd' -> totalSeconds += value * 86400L;
                        case 'h' -> totalSeconds += value * 3600L;
                        case 'm' -> totalSeconds += value * 60L;
                        case 's' -> totalSeconds += value;
                    }
                    number = new StringBuilder();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            language.getMessage("minecraft.ban.error-invalid-number").replace("{number}", number.toString()));
                }
            } else if (c != ' ') {
                throw new IllegalArgumentException(
                        language.getMessage("minecraft.ban.error-invalid-char").replace("{char}", String.valueOf(c)));
            }
        }

        if (number.length() > 0) {
            throw new IllegalArgumentException(
                    language.getMessage("minecraft.ban.error-missing-unit").replace("{number}", number.toString()));
        }

        if (totalSeconds == 0) {
            throw new IllegalArgumentException(language.getMessage("minecraft.ban.error-zero-duration"));
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
            // Dauer-Vorschläge
            return Arrays.asList("perm", "1h", "12h", "1d", "3d", "7d", "14d", "30d", "1h30m", "7d12h");
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
            List<String> dbReasons = database.getPunishmentReasons(type);

            // Ersetze Leerzeichen mit Underscore für bessere Tab-Completion
            for (String reason : dbReasons) {
                reasons.add(reason.replace(" ", "_"));
            }

        } catch (SQLException e) {
            // Fallback zu Standard-Gründen aus Sprachdatei
            String defaultReasons = language.getMessage("minecraft.ban.default-reasons");
            reasons.addAll(Arrays.asList(defaultReasons.split(",")));
        }

        // Füge "Eigener_Grund" als Option hinzu
        String customReason = language.getMessage("minecraft.ban.custom-reason");
        if (customReason != null && !customReason.equals("minecraft.ban.custom-reason")) {
            reasons.add(customReason.replace(" ", "_"));
        } else {
            reasons.add("Eigener_Grund"); // Fallback wenn Übersetzung fehlt
        }

        return reasons;
    }
}