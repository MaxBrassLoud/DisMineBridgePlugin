package de.MaxBrassLoud.disMineBridgePlugin.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MaintenanceManager {

    private static final Logger logger = Logger.getLogger("DisMineBridge");
    private static FileConfiguration config;
    private static Plugin plugin;
    private static boolean maintenanceEnabled = false;

    private static final String DEFAULT_MESSAGE =
            "§c§lWARTUNGSMODUS\n\n" +
                    "§7Der Server befindet sich aktuell im Wartungsmodus.\n" +
                    "§7Bitte versuche es später erneut.\n\n" +
                    "§8Für weitere Informationen besuche unseren Discord.";

    public static void init(FileConfiguration configuration, Plugin pluginInstance) {
        config = configuration;
        plugin = pluginInstance;
        maintenanceEnabled = config.getBoolean("maintenance.enabled", false);

        // Setze Default-Message falls nicht vorhanden
        if (!config.contains("maintenance.kick-message")) {
            config.set("maintenance.kick-message", DEFAULT_MESSAGE);
            saveConfigSync();
        }

        logger.info("MaintenanceManager initialisiert. Status: " +
                (maintenanceEnabled ? "aktiviert" : "deaktiviert"));
    }

    public static boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public static void setMaintenanceEnabled(boolean enabled) {
        maintenanceEnabled = enabled;
        config.set("maintenance.enabled", enabled);
        saveConfig();

        logger.info("Wartungsmodus " + (enabled ? "aktiviert" : "deaktiviert"));
    }

    public static String getMaintenanceMessage() {
        String message = config.getString("maintenance.kick-message", DEFAULT_MESSAGE);
        // Ersetze \n mit echten Zeilenumbrüchen
        message = message.replace("\\n", "\n");
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void setMaintenanceMessage(String message) {
        config.set("maintenance.kick-message", message);
        saveConfig();
        logger.info("Wartungsnachricht wurde aktualisiert");
    }

    public static void reload() {
        try {
            // Config neu laden
            plugin.reloadConfig();
            config = plugin.getConfig();
            maintenanceEnabled = config.getBoolean("maintenance.enabled", false);

            logger.info("MaintenanceManager neu geladen. Status: " +
                    (maintenanceEnabled ? "aktiviert" : "deaktiviert"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Neuladen des MaintenanceManagers", e);
        }
    }

    private static void saveConfig() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                config.save(configFile);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern der Config", e);
            }
        });
    }

    private static void saveConfigSync() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            config.save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern der Config", e);
        }
    }

    /**
     * Gibt die Anzahl der Spieler mit Wartungs-Bypass zurück
     */
    public static int getBypassCount() {
        // Diese Methode könnte auch direkt aus WhitelistManager kommen
        return de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager.getMaintenanceBypassPlayers().size();
    }

    /**
     * Prüft ob Wartungsmodus aktiv ist und gibt formatierte Status-Info zurück
     */
    public static String getStatusString() {
        if (maintenanceEnabled) {
            return ChatColor.RED + "✖ Wartungsmodus aktiv";
        } else {
            return ChatColor.GREEN + "✔ Normalbetrieb";
        }
    }
}