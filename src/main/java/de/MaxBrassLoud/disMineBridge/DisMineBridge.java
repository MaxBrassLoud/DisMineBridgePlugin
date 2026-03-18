package de.MaxBrassLoud.disMineBridge;

import de.MaxBrassLoud.disMineBridge.commands.*;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridge.discord.DiscordBot;
import de.MaxBrassLoud.disMineBridge.features.adminmode.AdminModeManager;
import de.MaxBrassLoud.disMineBridge.features.invsee.InventoryStoreManager;
import de.MaxBrassLoud.disMineBridge.features.vanish.VanishManager;
import de.MaxBrassLoud.disMineBridge.listeners.*;
import de.MaxBrassLoud.disMineBridge.managers.*;
import de.MaxBrassLoud.disMineBridge.features.web.WebPermissionManager;
import de.MaxBrassLoud.disMineBridge.features.web.WebServer;
import de.MaxBrassLoud.disMineBridge.features.web.WebSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DisMineBridge extends JavaPlugin {

    private static DisMineBridge instance;
    private DatabaseManager databaseManager;
    private LanguageManager languageManager;
    private PunishmentManager punishmentManager;
    private MuteManager muteManager;
    private VanishManager vanishManager;
    private WhitelistManager whitelistManager;
    private TicketManager ticketManager;
    private DiscordBot discordBot;
    private AdminModeManager adminModeManager;
    private InventoryStoreManager inventoryStoreManager;
    private WebServer webServer;
    private WebSessionManager webSessionManager;
    private WebPermissionManager webPermissionManager;
    private ChatManager chatManager;




    @Override
    public void onEnable() {
        instance = this;

        // Config erstellen
        saveDefaultConfig();

        // Database Manager
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Fehler beim Verbinden zur Datenbank!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Language Manager
        this.languageManager = new LanguageManager(this);
        languageManager.loadLanguages();

        // Punishment Manager
        this.punishmentManager = new PunishmentManager(this);

        // Mute Manager
        this.muteManager = new MuteManager(this);
        muteManager.loadActiveMutes();

        this.vanishManager = new VanishManager(this);

        this.whitelistManager = new WhitelistManager(this);

        this.ticketManager = new TicketManager(this);
        String token = getConfig().getString("discord.bot-token");
        discordBot = new DiscordBot(this, token);
        if (!discordBot.start()) {
            getLogger().severe("Discord Bot konnte nicht gestartet werden!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.adminModeManager = new AdminModeManager(this);

        this.inventoryStoreManager = new InventoryStoreManager(this);

        this.webSessionManager = new WebSessionManager();
        this.webPermissionManager = new WebPermissionManager(this);
        this.webServer = new WebServer(this);
        this.webServer.start();

        this.chatManager = new ChatManager(this);

        // Default Punishments laden (beim ersten Start)
        DefaultPunishmentsLoader defaultLoader = new DefaultPunishmentsLoader(this);
        defaultLoader.loadDefaults();

        // Commands registrieren
        registerCommands();

        // Listeners registrieren
        registerListeners();

        getLogger().info("DisMineBridge wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        // Cleanup
        if (muteManager != null) {
            muteManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        if (webServer != null) webServer.stop();
        if (webSessionManager != null) webSessionManager.shutdown();

        getLogger().info("DisMineBridge wurde deaktiviert!");
    }

    private void registerCommands() {
        // Ban/Unban
        getCommand("ban").setExecutor(new BanCommand(this));
        getCommand("unban").setExecutor(new UnbanCommand(this));

        // Mute/Unmute
        getCommand("mute").setExecutor(new MuteCommand(this));
        getCommand("unmute").setExecutor(new UnmuteCommand(this));

        // Warn/Kick
        getCommand("warn").setExecutor(new WarnCommand(this));
        getCommand("kick").setExecutor(new KickCommand(this));

        // Punishment System
        getCommand("punishment").setExecutor(new PunishmentCommand(this));
        getCommand("punish").setExecutor(new PunishCommand(this));

        getCommand("vanish").setExecutor(new VanishCommand(this));
        getCommand("whitelist").setExecutor(new WhitelistCommand(this));

        getCommand("invsee").setExecutor(new InvSeeCommand(this));
        getCommand("adminmode").setExecutor(new AdminModeCommand(this));

        getCommand("chatdelete").setExecutor(new ChatDeleteCommand(this));   // NEU
        getCommand("chattp").setExecutor(new ChatTpCommand(this));           // NEU
        // TabCompleter für chattp:
        getCommand("chattp").setTabCompleter(new ChatTpCommand(this));

        //getCommand("dmb").setExecutor(new DmbCommand(this));


    }

    private void registerListeners() {
        // Ban Listener
        getServer().getPluginManager().registerEvents(new BanListener(this), this);

        // Chat Listener (für Mutes)
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getServer().getPluginManager().registerEvents(new VanishJoinQuitListener(this), this);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);


    }

    // Getter
    public static DisMineBridge getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public VanishManager getVanishmanager() {
        return vanishManager;
    }

    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }

    public TicketManager getTicketManager() {
        return ticketManager;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public AdminModeManager getAdminModeManager() {return adminModeManager;}

    public InventoryStoreManager getInventoryStoreManager() { return  inventoryStoreManager;}

    public ChatManager getChatManager() { return chatManager; }

    public WebServer getWebServer() { return webServer; }

    public WebSessionManager getWebSessionManager() { return webSessionManager; }

    public WebPermissionManager getWebPermissionManager() { return webPermissionManager; }

}
