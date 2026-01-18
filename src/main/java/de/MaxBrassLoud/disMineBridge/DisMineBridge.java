package de.MaxBrassLoud.disMineBridge;

import de.MaxBrassLoud.disMineBridge.commands.WhitelistCommand;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridge.discord.DiscordBot;
import de.MaxBrassLoud.disMineBridge.listeners.PlayerJoinListener;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.TicketManager;
import de.MaxBrassLoud.disMineBridge.managers.WhitelistManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DisMineBridge extends JavaPlugin {

    private static DisMineBridge instance;
    private DatabaseManager databaseManager;
    private LanguageManager languageManager;
    private DiscordBot discordBot;
    private TicketManager ticketManager;
    private WhitelistManager whitelistManager;

    @Override
    public void onEnable() {
        instance = this;

        // Config erstellen
        saveDefaultConfig();

        // Language Manager initialisieren
        languageManager = new LanguageManager(this);
        languageManager.loadLanguages();

        // Datenbank initialisieren
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Konnte keine Verbindung zur Datenbank herstellen!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Manager initialisieren
        whitelistManager = new WhitelistManager(this);
        ticketManager = new TicketManager(this);

        // Discord Bot starten
        String token = getConfig().getString("discord.bot-token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("Kein Discord Bot Token in der Config gefunden!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        discordBot = new DiscordBot(this, token);
        if (!discordBot.start()) {
            getLogger().severe("Discord Bot konnte nicht gestartet werden!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Commands registrieren
        getCommand("whitelist").setExecutor(new WhitelistCommand(this));

        // Listeners registrieren
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        getLogger().info("DisMineBridge erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }


        getLogger().info("DisMineBridge deaktiviert!");
    }

    public static DisMineBridge getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public TicketManager getTicketManager() {
        return ticketManager;
    }

    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
}