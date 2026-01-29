package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PunishmentManager {

    private final DisMineBridge plugin;
    private final DatabaseManager db;
    private final Map<String, List<PunishmentReason>> cachedReasons;
    private final Map<String, ThresholdConfig> thresholds;
    private final Map<Integer, Integer> severityWeights;
    private boolean usePointSystem;

    public PunishmentManager(DisMineBridge plugin) {
        this.plugin = plugin;
        this.db = DatabaseManager.getInstance();
        this.cachedReasons = new HashMap<>();
        this.thresholds = new LinkedHashMap<>();
        this.severityWeights = new HashMap<>();

        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection punishmentConfig = plugin.getConfig().getConfigurationSection("punishment");

        if (punishmentConfig == null) {
            plugin.getLogger().warning("Punishment-Konfiguration fehlt! Verwende Standardwerte.");
            usePointSystem = false;
            return;
        }

        usePointSystem = punishmentConfig.getBoolean("use-point-system", true);

        // Lade Schweregrad-Gewichtungen
        ConfigurationSection weights = punishmentConfig.getConfigurationSection("severity-weights");
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                try {
                    int severity = Integer.parseInt(key);
                    int points = weights.getInt(key);
                    severityWeights.put(severity, points);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Ungültige Schweregrad-Konfiguration: " + key);
                }
            }
        }

        // Lade Schwellenwerte
        ConfigurationSection thresholdsSection = punishmentConfig.getConfigurationSection("thresholds");
        if (thresholdsSection != null) {
            for (String key : thresholdsSection.getKeys(false)) {
                ConfigurationSection threshold = thresholdsSection.getConfigurationSection(key);
                if (threshold != null) {
                    thresholds.put(key, new ThresholdConfig(
                            threshold.getInt("min-points"),
                            threshold.getInt("max-points"),
                            threshold.getString("action"),
                            threshold.getString("duration"),
                            threshold.getString("message")
                    ));
                }
            }
        }

        plugin.getLogger().info("Punishment-System geladen: " +
                (usePointSystem ? "Punktesystem AKTIV" : "Legacy-System"));
    }

    /**
     * Lädt alle Gründe für einen bestimmten Punishment-Typ
     */
    public List<PunishmentReason> getReasons(String type) {
        if (cachedReasons.containsKey(type)) {
            return new ArrayList<>(cachedReasons.get(type));
        }

        try {
            List<PunishmentReason> reasons = db.getPunishmentReasonsByType(type);
            cachedReasons.put(type, reasons);
            return new ArrayList<>(reasons);
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der Punishment Reasons: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean addReason(String type, String reason, String duration) {
        try {
            db.addPunishmentReason(type, reason, duration);
            cachedReasons.remove(type);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Hinzufügen des Punishment Reason: " + e.getMessage());
            return false;
        }
    }

    public boolean removeReason(int reasonId) {
        try {
            db.removePunishmentReason(reasonId);
            cachedReasons.clear();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Entfernen des Punishment Reason: " + e.getMessage());
            return false;
        }
    }

    public PunishmentReason getReasonByName(String reasonName) {
        try {
            return db.getPunishmentReasonByName(reasonName);
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Suchen des Punishment Reason: " + e.getMessage());
            return null;
        }
    }

    /**
     * Führt eine automatisch eskalierende Bestrafung durch (mit Schweregrad aus DB)
     */
    public boolean executePunishment(Player target, String reasonName, Player executor) {
        PunishmentReason reason = getReasonByName(reasonName);
        if (reason == null) {
            return false;
        }

        // Verwende Schweregrad aus Datenbank
        int severity = reason.getSeverity();

        try {
            String uuid = target.getUniqueId().toString();
            String executorName = executor != null ? executor.getName() : "CONSOLE";

            if (usePointSystem) {
                return executePunishmentWithPoints(target, reason, executor, severity, uuid, executorName);
            } else {
                return executePunishmentLegacy(target, reason, executor, uuid, executorName);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Ausführen der Bestrafung: " + e.getMessage());
            return false;
        }
    }

    /**
     * Überladen: Mit manuellem Schweregrad (optional)
     */
    public boolean executePunishment(Player target, String reasonName, Player executor, int severity) {
        if (severity < 1 || severity > 10) {
            plugin.getLogger().warning("Ungültiger Schweregrad: " + severity + " (muss 1-10 sein)");
            return executePunishment(target, reasonName, executor); // Nutze DB-Schweregrad
        }

        PunishmentReason reason = getReasonByName(reasonName);
        if (reason == null) {
            return false;
        }

        try {
            String uuid = target.getUniqueId().toString();
            String executorName = executor != null ? executor.getName() : "CONSOLE";

            if (usePointSystem) {
                return executePunishmentWithPoints(target, reason, executor, severity, uuid, executorName);
            } else {
                return executePunishmentLegacy(target, reason, executor, uuid, executorName);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Ausführen der Bestrafung: " + e.getMessage());
            return false;
        }
    }

    /**
     * Punktebasiertes Bestrafungssystem
     */
    private boolean executePunishmentWithPoints(Player target, PunishmentReason reason,
                                                Player executor, int severity, String uuid,
                                                String executorName) throws SQLException {

        // Berechne Punkte für diesen Verstoß
        int basePoints = severityWeights.getOrDefault(severity, 50);

        // Hole Wiederholungsfaktor
        int violationCount = db.getViolationCount(uuid, reason.getReason());
        double repeatMultiplier = getRepeatOffenseMultiplier(violationCount);

        // Hole zeitbasierten Multiplikator
        double timeMultiplier = getTimeBasedMultiplier(uuid, reason.getReason());

        // Berechne finale Punkte
        int finalPoints = (int) Math.ceil(basePoints * repeatMultiplier * timeMultiplier);

        // Füge Punkte hinzu
        db.addPunishmentPoints(uuid, target.getName(), finalPoints, reason.getReason(),
                severity, executorName);

        // Hole Gesamtpunkte
        int totalPoints = db.getTotalPunishmentPoints(uuid);

        // Prüfe Spezialregeln
        String specialAction = checkSpecialRules(reason.getReason(), violationCount);
        if (specialAction != null) {
            return executeSpecialPunishment(target, reason, executorName, specialAction,
                    totalPoints, finalPoints);
        }

        // Bestimme Strafe basierend auf Punkten
        ThresholdConfig threshold = getThresholdForPoints(totalPoints);
        if (threshold == null) {
            plugin.getLogger().warning("Keine Schwelle für " + totalPoints + " Punkte gefunden!");
            return false;
        }

        // Warne bei bestimmten Punktzahlen
        notifyPointWarning(target, totalPoints);

        // Führe Strafe aus
        return executePunishmentAction(target, reason, threshold, executorName,
                totalPoints, finalPoints, violationCount + 1);
    }

    /**
     * Legacy Bestrafungssystem (ohne Punkte)
     */
    private boolean executePunishmentLegacy(Player target, PunishmentReason reason,
                                            Player executor, String uuid,
                                            String executorName) throws SQLException {
        int violationCount = db.getViolationCount(uuid, reason.getReason());
        PunishmentLevel level = determinePunishmentLevel(violationCount);
        return executePunishmentLevel(target, reason, level, executor, violationCount + 1);
    }

    /**
     * Berechnet Multiplikator für wiederholte Verstöße
     */
    private double getRepeatOffenseMultiplier(int violationCount) {
        ConfigurationSection multipliers = plugin.getConfig()
                .getConfigurationSection("punishment.repeat-offense-multipliers.same-reason");

        if (multipliers == null || !plugin.getConfig()
                .getBoolean("punishment.repeat-offense-multipliers.enabled", true)) {
            return 1.0;
        }

        switch (violationCount) {
            case 1: return multipliers.getDouble("2nd-offense", 1.5);
            case 2: return multipliers.getDouble("3rd-offense", 2.0);
            case 3: return multipliers.getDouble("4th-offense", 3.0);
            case 4:
            default: return multipliers.getDouble("5th-offense", 5.0);
        }
    }

    /**
     * Berechnet zeitbasierten Multiplikator
     */
    private double getTimeBasedMultiplier(String uuid, String reason) throws SQLException {
        ConfigurationSection timeConfig = plugin.getConfig()
                .getConfigurationSection("punishment.repeat-offense-multipliers.time-based");

        if (timeConfig == null || !timeConfig.getBoolean("enabled", true)) {
            return 1.0;
        }

        long lastViolation = db.getLastViolationTime(uuid, reason);
        if (lastViolation == 0) return 1.0;

        long hoursSince = ChronoUnit.HOURS.between(
                Instant.ofEpochMilli(lastViolation),
                Instant.now()
        );

        if (hoursSince < 24) {
            return timeConfig.getDouble("within-24h", 1.5);
        } else if (hoursSince < 168) { // 7 Tage
            return timeConfig.getDouble("within-7d", 1.2);
        }

        return 1.0;
    }

    /**
     * Prüft Spezialregeln
     */
    private String checkSpecialRules(String reasonName, int violationCount) {
        ConfigurationSection specialRules = plugin.getConfig()
                .getConfigurationSection("punishment.special-rules");

        if (specialRules == null) return null;

        String reasonLower = reasonName.toLowerCase();

        // DDoS-Drohung
        if (reasonLower.contains("ddos") || reasonLower.contains("dos")) {
            ConfigurationSection ddos = specialRules.getConfigurationSection("ddos-threat");
            if (ddos != null && ddos.getBoolean("enabled") && ddos.getBoolean("instant-permban")) {
                return "INSTANT_PERMBAN";
            }
        }

        // Rassismus/Extremismus
        if (reasonLower.contains("rassismus") || reasonLower.contains("extremismus") ||
                reasonLower.contains("racism") || reasonLower.contains("extremism")) {
            ConfigurationSection racism = specialRules.getConfigurationSection("racism-extremism");
            if (racism != null && racism.getBoolean("enabled") && racism.getBoolean("instant-permban")) {
                return "INSTANT_PERMBAN";
            }
        }

        // Griefing
        if (reasonLower.contains("grief")) {
            ConfigurationSection grief = specialRules.getConfigurationSection("griefing");
            if (grief != null && grief.getBoolean("enabled")) {
                switch (violationCount) {
                    case 0: return "TEMPBAN:" + grief.getString("first-offense", "3d");
                    case 1: return "TEMPBAN:" + grief.getString("second-offense", "14d");
                    case 2: return "TEMPBAN:" + grief.getString("third-offense", "30d");
                    default: return "TEMPBAN:" + grief.getString("fourth-offense", "perm");
                }
            }
        }

        // Hacking
        if (reasonLower.contains("hack") || reasonLower.contains("cheat")) {
            ConfigurationSection hack = specialRules.getConfigurationSection("hacking");
            if (hack != null && hack.getBoolean("enabled")) {
                String minDuration = hack.getString("min-ban-duration", "14d");
                return "TEMPBAN:" + minDuration;
            }
        }

        return null;
    }

    /**
     * Führt Spezialstrafe aus
     */
    private boolean executeSpecialPunishment(Player target, PunishmentReason reason,
                                             String executorName, String specialAction,
                                             int totalPoints, int addedPoints) throws SQLException {
        LanguageManager lang = plugin.getLanguageManager();

        if (specialAction.equals("INSTANT_PERMBAN")) {
            String fullReason = reason.getReason() + " (SOFORTIGER PERM-BAN | +" + addedPoints +
                    " Punkte | Total: " + totalPoints + ")";

            db.createBan(0, target.getUniqueId().toString(), target.getName(), fullReason,
                    0, executorName, -1);

            target.kickPlayer(lang.getMessage("minecraft.punish.special.instant-permban")
                    .replace("{reason}", reason.getReason())
                    .replace("{points}", String.valueOf(totalPoints)));

            Bukkit.broadcastMessage(lang.getMessage("minecraft.punish.special.instant-permban-broadcast")
                    .replace("{player}", target.getName())
                    .replace("{reason}", reason.getReason()));

            db.recordViolation(target.getUniqueId().toString(), target.getName(),
                    reason.getReason(), "PERMBAN_SPECIAL", executorName);

            return true;
        }

        if (specialAction.startsWith("TEMPBAN:")) {
            String duration = specialAction.substring(8);
            long expireTime = "perm".equals(duration) ? -1 : parseDuration(duration);

            String fullReason = reason.getReason() + " (SPEZIALREGEL | +" + addedPoints +
                    " Punkte | Total: " + totalPoints + ")";

            db.createBan(0, target.getUniqueId().toString(), target.getName(), fullReason,
                    0, executorName, expireTime);

            target.kickPlayer(lang.getMessage("minecraft.ban.kick-message")
                    .replace("{reason}", fullReason)
                    .replace("{time}", formatDuration(expireTime - System.currentTimeMillis())));

            Bukkit.broadcastMessage(lang.getMessage("minecraft.ban.broadcast")
                    .replace("{player}", target.getName())
                    .replace("{banner}", executorName)
                    .replace("{time}", duration));

            db.recordViolation(target.getUniqueId().toString(), target.getName(),
                    reason.getReason(), "TEMPBAN_SPECIAL:" + duration, executorName);

            return true;
        }

        return false;
    }

    /**
     * Findet passende Schwelle für Punktzahl
     */
    private ThresholdConfig getThresholdForPoints(int points) {
        for (ThresholdConfig threshold : thresholds.values()) {
            if (points >= threshold.minPoints && points <= threshold.maxPoints) {
                return threshold;
            }
        }
        return null;
    }

    /**
     * Warnt Spieler bei bestimmten Punktzahlen
     */
    private void notifyPointWarning(Player target, int totalPoints) {
        List<Integer> warnAtPoints = plugin.getConfig()
                .getIntegerList("punishment.notifications.warn-at-points");

        if (warnAtPoints.contains(totalPoints)) {
            LanguageManager lang = plugin.getLanguageManager();
            target.sendMessage("");
            target.sendMessage(lang.getMessage("minecraft.punish.point-warning.header"));
            target.sendMessage(lang.getMessage("minecraft.punish.point-warning.message")
                    .replace("{points}", String.valueOf(totalPoints)));
            target.sendMessage(lang.getMessage("minecraft.punish.point-warning.footer"));
            target.sendMessage("");
        }
    }

    /**
     * Führt Strafaktion aus (OHNE Punkteanzeige für Spieler)
     */
    private boolean executePunishmentAction(Player target, PunishmentReason reason,
                                            ThresholdConfig threshold, String executorName,
                                            int totalPoints, int addedPoints, int violationNumber)
            throws SQLException {
        LanguageManager lang = plugin.getLanguageManager();
        String uuid = target.getUniqueId().toString();

        // Punkte werden NICHT an Spieler angezeigt, nur für interne Logs
        String fullReason = reason.getReason() + " (Verstoß #" + violationNumber + ")";

        // Log für Admins/Console mit Punkten
        plugin.getLogger().info("[Punishment] " + target.getName() + " | " + fullReason +
                " | +" + addedPoints + " Punkte | Total: " + totalPoints +
                " | Strafe: " + threshold.action);

        switch (threshold.action.toUpperCase()) {
            case "WARN":
                db.createWarn(uuid, target.getName(), fullReason, executorName);

                target.sendMessage("");
                target.sendMessage(lang.getMessage("minecraft.warn.message.header"));
                target.sendMessage(lang.getMessage("minecraft.warn.message.title"));
                target.sendMessage("");
                target.sendMessage(lang.getMessage("minecraft.warn.message.reason")
                        .replace("{reason}", reason.getReason()));
                target.sendMessage(lang.getMessage("minecraft.warn.message.by")
                        .replace("{warner}", executorName));
                target.sendMessage(lang.getMessage("minecraft.punish.escalation-warning"));
                target.sendMessage(lang.getMessage("minecraft.warn.message.footer"));
                target.sendMessage("");

                Bukkit.broadcastMessage(lang.getMessage("minecraft.warn.broadcast")
                        .replace("{player}", target.getName())
                        .replace("{warner}", executorName));
                break;

            case "KICK":
                db.createKick(uuid, target.getName(), fullReason, executorName);

                target.kickPlayer(lang.getMessage("minecraft.kick.kick-message")
                        .replace("{reason}", reason.getReason()));

                Bukkit.broadcastMessage(lang.getMessage("minecraft.kick.broadcast")
                        .replace("{player}", target.getName())
                        .replace("{kicker}", executorName));
                break;

            case "TEMPBAN":
            case "BAN":
                long expireTime = parseDuration(threshold.duration);
                db.createBan(0, uuid, target.getName(), fullReason, 0, executorName, expireTime);

                target.kickPlayer(lang.getMessage("minecraft.ban.kick-message")
                        .replace("{reason}", reason.getReason())
                        .replace("{time}", formatDuration(expireTime - System.currentTimeMillis())));

                Bukkit.broadcastMessage(lang.getMessage("minecraft.ban.broadcast")
                        .replace("{player}", target.getName())
                        .replace("{banner}", executorName)
                        .replace("{time}", threshold.duration));
                break;

            case "PERMBAN":
                db.createBan(0, uuid, target.getName(), fullReason, 0, executorName, -1);

                target.kickPlayer(lang.getMessage("minecraft.ban.kick-message")
                        .replace("{reason}", reason.getReason())
                        .replace("{time}", "PERMANENT"));

                Bukkit.broadcastMessage(lang.getMessage("minecraft.ban.broadcast-permanent")
                        .replace("{player}", target.getName())
                        .replace("{banner}", executorName));
                break;

            case "MUTE":
                // TODO: Mute System implementieren
                plugin.getLogger().warning("MUTE System noch nicht implementiert für: " + target.getName());
                target.sendMessage(lang.getMessage("minecraft.mute.muted")
                        .replace("{reason}", reason.getReason())
                        .replace("{duration}", threshold.duration));
                break;
        }

        db.recordViolation(uuid, target.getName(), reason.getReason(),
                threshold.action + ":" + threshold.duration, executorName);

        return true;
    }


    private PunishmentLevel determinePunishmentLevel(int violationCount) {
        switch (violationCount) {
            case 0: return PunishmentLevel.WARN;
            case 1: return PunishmentLevel.KICK;
            case 2: return PunishmentLevel.TEMPBAN_SHORT;
            case 3: return PunishmentLevel.TEMPBAN_MEDIUM;
            case 4: return PunishmentLevel.TEMPBAN_LONG;
            default: return PunishmentLevel.PERMBAN;
        }
    }

    private boolean executePunishmentLevel(Player target, PunishmentReason reason,
                                           PunishmentLevel level, Player executor,
                                           int violationNumber) {
        // Legacy Code bleibt unverändert für Kompatibilität
        return false;
    }

    private long parseDuration(String duration) {
        long totalMillis = 0;
        StringBuilder number = new StringBuilder();

        for (char c : duration.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() > 0) {
                    int value = Integer.parseInt(number.toString());
                    switch (c) {
                        case 'd': totalMillis += value * 86400000L; break;
                        case 'h': totalMillis += value * 3600000L; break;
                        case 'm': totalMillis += value * 60000L; break;
                        case 's': totalMillis += value * 1000L; break;
                    }
                    number = new StringBuilder();
                }
            }
        }

        return System.currentTimeMillis() + totalMillis;
    }

    private String formatDuration(long millis) {
        if (millis < 0) return "0s";

        long days = millis / 86400000L;
        long hours = (millis % 86400000L) / 3600000L;
        long minutes = (millis % 3600000L) / 60000L;

        if (days > 0) return days + " Tag(e)";
        if (hours > 0) return hours + " Stunde(n)";
        return minutes + " Minute(n)";
    }

    public void reloadCache() {
        cachedReasons.clear();
        loadConfig();
    }

    // Inner Classes
    public enum PunishmentLevel {
        WARN, KICK, TEMPBAN_SHORT, TEMPBAN_MEDIUM, TEMPBAN_LONG, PERMBAN
    }

    public static class PunishmentReason {
        private final int id;
        private final String type;
        private final String reason;
        private final String duration;
        private final int severity;

        public PunishmentReason(int id, String type, String reason, String duration, int severity) {
            this.id = id;
            this.type = type;
            this.reason = reason;
            this.duration = duration;
            this.severity = severity;
        }

        public int getId() { return id; }
        public String getType() { return type; }
        public String getReason() { return reason; }
        public String getDuration() { return duration; }
        public int getSeverity() { return severity; }
    }

    private static class ThresholdConfig {
        final int minPoints;
        final int maxPoints;
        final String action;
        final String duration;
        final String message;

        ThresholdConfig(int minPoints, int maxPoints, String action, String duration, String message) {
            this.minPoints = minPoints;
            this.maxPoints = maxPoints;
            this.action = action;
            this.duration = duration;
            this.message = message;
        }
    }
}