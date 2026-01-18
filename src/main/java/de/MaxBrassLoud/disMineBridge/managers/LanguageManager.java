package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private final DisMineBridge plugin;
    private final Map<String, FileConfiguration> languages;
    private String defaultLanguage;

    public LanguageManager(DisMineBridge plugin) {
        this.plugin = plugin;
        this.languages = new HashMap<>();
    }

    public void loadLanguages() {
        defaultLanguage = plugin.getConfig().getString("language.default", "de-DE");

        File languageFolder = new File(plugin.getDataFolder(), "languages");
        if (!languageFolder.exists()) {
            languageFolder.mkdirs();
        }

        // Kopiere Standard-Sprachdatei
        saveDefaultLanguageFile("de-DE.yml");

        // Lade alle Sprachdateien
        File[] files = languageFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String langCode = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                languages.put(langCode, config);
                plugin.getLogger().info("Sprachdatei geladen: " + langCode);
            }
        }

        if (!languages.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("Standard-Sprache " + defaultLanguage + " nicht gefunden! Verwende de-DE.");
            defaultLanguage = "de-DE";
        }
    }

    private void saveDefaultLanguageFile(String fileName) {
        File file = new File(plugin.getDataFolder(), "languages/" + fileName);
        if (!file.exists()) {
            try {
                InputStream in = plugin.getResource("languages/" + fileName);
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Konnte Standard-Sprachdatei nicht erstellen: " + fileName);
            }
        }
    }

    public String getMessage(String path) {
        return getMessage(path, defaultLanguage);
    }

    public String getMessage(String path, String language) {
        FileConfiguration config = languages.get(language);
        if (config == null) {
            config = languages.get(defaultLanguage);
        }

        if (config == null) {
            return "Missing language file!";
        }

        String message = config.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Fehlende Übersetzung: " + path);
            return path;
        }

        // Farbcodes konvertieren
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }
}