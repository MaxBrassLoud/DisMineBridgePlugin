package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lädt Standard-Bestrafungen beim ersten Start
 */
public class DefaultPunishmentsLoader {

    private final DisMineBridge plugin;
    private final DatabaseManager db;

    public DefaultPunishmentsLoader(DisMineBridge plugin) {
        this.plugin = plugin;
        this.db = DatabaseManager.getInstance();
    }

    /**
     * Lädt alle Default-Strafen in die Datenbank
     */
    public void loadDefaults() {
        try {
            // Prüfe ob bereits Strafen existieren
            if (db.getPunishmentReasonCount() > 0) {
                plugin.getLogger().info("Punishment-Gründe bereits vorhanden, überspringe Default-Import.");
                return;
            }

            plugin.getLogger().info("Lade Standard-Bestrafungen...");

            Map<String, PunishmentData> defaults = getDefaultPunishments();
            int loaded = 0;

            for (Map.Entry<String, PunishmentData> entry : defaults.entrySet()) {
                String reason = entry.getKey();
                PunishmentData data = entry.getValue();

                try {
                    db.addPunishmentReason(data.type, reason, data.duration,
                            data.description, data.severity);
                    loaded++;
                } catch (SQLException e) {
                    plugin.getLogger().warning("Fehler beim Laden von: " + reason);
                }
            }

            plugin.getLogger().info("✓ " + loaded + " Standard-Bestrafungen erfolgreich geladen!");

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der Default-Strafen: " + e.getMessage());
        }
    }

    /**
     * Definiert alle Standard-Bestrafungen
     */
    private Map<String, PunishmentData> getDefaultPunishments() {
        Map<String, PunishmentData> punishments = new LinkedHashMap<>();

        // ============================================
        // CHAT & VERHALTEN
        // ============================================
        punishments.put("Spam",
                new PunishmentData("MUTE", "10m", "Spam im Chat", 1));

        punishments.put("Capslock-Spam",
                new PunishmentData("MUTE", "5m", "Übermäßige Nutzung von Großbuchstaben", 1));

        punishments.put("Leichte Provokation",
                new PunishmentData("MUTE", "15m", "Provokatives Verhalten gegenüber anderen Spielern", 2));

        punishments.put("Respektlosigkeit",
                new PunishmentData("MUTE", "30m", "Respektloses Verhalten", 3));

        punishments.put("Leichte Beleidigung",
                new PunishmentData("MUTE", "1h", "Beleidigung anderer Spieler", 4));

        punishments.put("Schwere Beleidigung",
                new PunishmentData("MUTE", "24h", "Schwere Beleidigung", 6));

        punishments.put("Diskriminierung",
                new PunishmentData("BAN", "7d", "Rassismus, Sexismus oder andere Diskriminierung", 9));

        punishments.put("Beleidigung des Teams",
                new PunishmentData("BAN", "3d", "Beleidigung von Teammitgliedern", 7));

        punishments.put("Drohung",
                new PunishmentData("BAN", "perm", "Drohung gegen Team oder Spieler", 10));

        // ============================================
        // LINKS & WERBUNG
        // ============================================
        punishments.put("Werbelinks",
                new PunishmentData("MUTE", "1h", "Werbung für externe Server, Discord oder Social Media", 4));

        punishments.put("Verbotene Links",
                new PunishmentData("BAN", "perm", "Phishing, Tracking oder Malware Links", 10));

        // ============================================
        // PRIVATSPHÄRE
        // ============================================
        punishments.put("Veröffentlichen privater Nachrichten",
                new PunishmentData("BAN", "3d", "Veröffentlichung privater Konversationen", 7));

        punishments.put("Doxxing",
                new PunishmentData("BAN", "perm", "Veröffentlichung privater Daten", 10));

        // ============================================
        // GRIEFING
        // ============================================
        punishments.put("Leichtes Griefing",
                new PunishmentData("BAN", "12h", "Kleinere Zerstörungen", 5));

        punishments.put("Mittleres Griefing",
                new PunishmentData("BAN", "3d", "Mittelschwere Zerstörungen", 7));

        punishments.put("Schweres Griefing",
                new PunishmentData("BAN", "14d", "Schwere und absichtliche Zerstörungen", 9));

        // ============================================
        // PVP REGELN
        // ============================================
        punishments.put("Spawn-Camping",
                new PunishmentData("BAN", "6h", "Campen am Spawn", 4));

        punishments.put("Base-Camping",
                new PunishmentData("BAN", "12h", "Campen an fremden Basen", 5));

        punishments.put("Kill während Handel",
                new PunishmentData("BAN", "24h", "Spieler während eines Handels töten", 6));

        punishments.put("Kill ohne Zustimmung",
                new PunishmentData("BAN", "30m", "PvP ohne Einverständnis", 3));

        punishments.put("Wiederholtes Töten",
                new PunishmentData("BAN", "6h", "Denselben Spieler mehrfach hintereinander töten", 5));

        // ============================================
        // HACKING & CHEATING
        // ============================================
        punishments.put("X-Ray",
                new PunishmentData("BAN", "7d", "Verwendung von X-Ray", 9));

        punishments.put("Freecam",
                new PunishmentData("BAN", "7d", "Verwendung von Freecam", 9));

        punishments.put("Hack-Client",
                new PunishmentData("BAN", "perm", "Verwendung eines Hack-Clients", 10));

        punishments.put("Exploit ausnutzen",
                new PunishmentData("BAN", "7d", "Ausnutzen von Bugs/Exploits", 8));

        punishments.put("Exploit weitergeben",
                new PunishmentData("BAN", "perm", "Weitergabe von Exploits an andere", 10));

        punishments.put("Anticheat umgehen",
                new PunishmentData("BAN", "perm", "Umgehung des Anticheat-Systems", 10));

        punishments.put("Sicherheitslücken ausnutzen",
                new PunishmentData("BAN", "perm", "Ausnutzen von Sicherheitslücken", 10));

        // ============================================
        // WIRTSCHAFT & SCAMMING
        // ============================================
        punishments.put("Leichtes Scamming",
                new PunishmentData("BAN", "3d", "Kleinerer Betrug", 6));

        punishments.put("Mittleres Scamming",
                new PunishmentData("BAN", "7d", "Mittelschwerer Betrug", 8));

        punishments.put("Schweres Scamming",
                new PunishmentData("BAN", "perm", "Schwerer oder wiederholter Betrug", 10));

        punishments.put("Diebstahl",
                new PunishmentData("BAN", "24h", "Diebstahl von Items oder Ressourcen", 5));

        punishments.put("Echtgeldhandel",
                new PunishmentData("BAN", "perm", "Real Money Trading (RMT)", 10));

        // ============================================
        // TECHNISCH
        // ============================================
        punishments.put("Lag leicht verursachen",
                new PunishmentData("BAN", "30m", "Leichte Lag-Verursachung", 3));

        punishments.put("Lag schwer verursachen",
                new PunishmentData("BAN", "24h", "Schwere Lag-Verursachung", 7));

        punishments.put("Absichtlich Lag erzeugen",
                new PunishmentData("BAN", "perm", "Vorsätzliche Lag-Maschinen", 10));

        // ============================================
        // ACCOUNTS
        // ============================================
        punishments.put("VPN Bannumgehung",
                new PunishmentData("BAN", "perm", "VPN zur Bannumgehung", 10));

        punishments.put("Account-Sharing",
                new PunishmentData("BAN", "7d", "Teilen des Accounts mit anderen", 6));

        punishments.put("Zweitaccounts",
                new PunishmentData("BAN", "3d", "Verwendung von Alt-Accounts", 5));

        punishments.put("Mehrfachaccounts Bannumgehung",
                new PunishmentData("BAN", "perm", "Alts zur Bannumgehung", 10));

        // ============================================
        // TEAM INTERAKTION
        // ============================================
        punishments.put("Team belügen",
                new PunishmentData("BAN", "3d", "Täuschung des Teams", 6));

        punishments.put("Team ignorieren",
                new PunishmentData("BAN", "24h", "Wiederholtes Ignorieren von Anweisungen", 5));

        punishments.put("Bug-Abuse ohne Meldung",
                new PunishmentData("BAN", "3d", "Bugs ausnutzen ohne zu melden", 7));

        // ============================================
        // SONSTIGES
        // ============================================
        punishments.put("Unangemessener Skin/Name",
                new PunishmentData("KICK", "-", "Kick mit Aufforderung zur Änderung", 2));

        punishments.put("Trolling",
                new PunishmentData("BAN", "6h", "Absichtliches Trollen", 4));

        punishments.put("Falsche Reports",
                new PunishmentData("BAN", "1h", "Missbrauch des Report-Systems", 3));

        return punishments;
    }

    /**
     * Helper-Klasse für Punishment-Daten
     */
    private static class PunishmentData {
        final String type;
        final String duration;
        final String description;
        final int severity;

        PunishmentData(String type, String duration, String description, int severity) {
            this.type = type;
            this.duration = duration;
            this.description = description;
            this.severity = severity;
        }
    }
}