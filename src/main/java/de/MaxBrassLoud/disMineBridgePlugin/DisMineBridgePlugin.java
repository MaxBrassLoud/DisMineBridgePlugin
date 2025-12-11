package de.MaxBrassLoud.disMineBridgePlugin;

import de.MaxBrassLoud.disMineBridgePlugin.adminmode.AdminModeManager;
import de.MaxBrassLoud.disMineBridgePlugin.command.*;
import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridgePlugin.listener.*;
import de.MaxBrassLoud.disMineBridgePlugin.maintenance.MaintenanceManager;
import de.MaxBrassLoud.disMineBridgePlugin.voicechat.VoiceChatMutePlugin;
import de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import de.MaxBrassLoud.disMineBridgePlugin.serverlist.ServerlistManager;

public final class DisMineBridgePlugin extends JavaPlugin {

    private static DisMineBridgePlugin instance;

    @Override
    public void onEnable() {

        instance = this;
        getLogger().info("Plugin startet...");

        // Config erstellen falls nicht vorhanden
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        ServerlistManager.setMOTD(config.getString("maintenance.motd"));
        // ============================================================
        //  DATENBANK INITIALISIEREN (ZENTRAL)
        // ============================================================
        DatabaseManager.init(this);

        // ============================================================
        //  MANAGER INITIALISIEREN
        // ============================================================
        AdminModeManager.init();
        WhitelistManager.init(config, this);
        MaintenanceManager.init(config, this);

        // ============================================================
        //  COMMANDS REGISTRIEREN
        // ============================================================

        // Main Command
        getCommand("dmb").setExecutor(new DMBCommand(this));

        // Ban Commands
        ban banCmd = new ban();
        getCommand("ban").setExecutor(banCmd);
        getCommand("ban").setTabCompleter(banCmd);
        getCommand("unban").setExecutor(new unban());

        // Warn & Kick Commands
        getCommand("warn").setExecutor(new warn());
        getCommand("kick").setExecutor(new kick());

        // Mute Commands
        getCommand("mute").setExecutor(new mute());
        getCommand("unmute").setExecutor(new unmute());

        // Inventory Commands
        invsee invseeCmd = new invsee();
        getCommand("invsee").setExecutor(invseeCmd);
        getCommand("invsee").setTabCompleter(invseeCmd);

        endersee enderseeCmd = new endersee();
        getCommand("endersee").setExecutor(enderseeCmd);
        getCommand("endersee").setTabCompleter(enderseeCmd);

        // AdminMode Command
        getCommand("adminmode").setExecutor(new AdminModeCommand());

        // Whitelist & Maintenance Commands
        WhitelistCommand whitelistCmd = new WhitelistCommand();
        getCommand("whitelist").setExecutor(whitelistCmd);
        getCommand("whitelist").setTabCompleter(whitelistCmd);

        MaintenanceCommand maintenanceCmd = new MaintenanceCommand();
        getCommand("maintenance").setExecutor(maintenanceCmd);
        getCommand("maintenance").setTabCompleter(maintenanceCmd);

        //Vanish Command
        vanish vanishCmd = new vanish();
        getCommand("vanish").setExecutor(vanishCmd);
        getCommand("vanish").setTabCompleter(vanishCmd);

        // ============================================================
        //  LISTENER REGISTRIEREN
        // ============================================================

        MuteListener muteListener = new MuteListener();
        PlayerQuitListener quitListener = new PlayerQuitListener(muteListener);

        getServer().getPluginManager().registerEvents(new InventoryCloseListener(), this);
        getServer().getPluginManager().registerEvents(new BanListener(), this);
        getServer().getPluginManager().registerEvents(muteListener, this);
        //getServer().getPluginManager().registerEvents(new AdminModeListener(), this);
        getServer().getPluginManager().registerEvents(new LoginListener(), this);
        getServer().getPluginManager().registerEvents(quitListener, this);
        getServer().getPluginManager().registerEvents(new VanishJoinQuitListener(), this);

        // ============================================================
        //  VOICECHAT INTEGRATION
        // ============================================================
        setupVoiceChat(muteListener, quitListener);

        getLogger().info("✔ Plugin erfolgreich gestartet!");
    }

    private void setupVoiceChat(MuteListener muteListener, PlayerQuitListener quitListener) {
        Plugin voicePlugin = getServer().getPluginManager().getPlugin("voicechat");
        if (voicePlugin != null) {
            try {
                de.maxhenkel.voicechat.api.BukkitVoicechatService service =
                        getServer().getServicesManager().load(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);

                if (service != null) {
                    VoiceChatMutePlugin vcPlugin = new VoiceChatMutePlugin(this, muteListener);
                    quitListener.setVoiceChatPlugin(vcPlugin);
                    service.registerPlugin(vcPlugin);
                    getLogger().info("✔ VoiceChat-Integration aktiviert!");
                }
            } catch (Exception e) {
                getLogger().warning("VoiceChat-Integration fehlgeschlagen: " + e.getMessage());
            }
        } else {
            getLogger().info("Simple Voice Chat nicht gefunden - nur Text-Mute aktiv.");
        }
    }

    @Override
    public void onDisable() {
        DatabaseManager.getInstance().close();
        getLogger().info("Plugin gestoppt.");
    }

    public static DisMineBridgePlugin getInstance() {
        return instance;
    }
}