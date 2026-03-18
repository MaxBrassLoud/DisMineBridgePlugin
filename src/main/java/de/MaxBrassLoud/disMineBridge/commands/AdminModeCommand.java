package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.features.adminmode.AdminModeManager;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.List;

public class AdminModeCommand implements CommandExecutor, TabCompleter, Listener {

    private final DisMineBridge  plugin;
    private final AdminModeManager manager;
    private final LanguageManager  lang;

    public AdminModeCommand(DisMineBridge plugin) {
        this.plugin  = plugin;
        this.manager = plugin.getAdminModeManager();
        this.lang    = plugin.getLanguageManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.getMessage("minecraft.adminmode.player-only"));
            return true;
        }

        if (!player.hasPermission("dmb.adminmode") && !player.hasPermission("dmb.admin")) {
            player.sendMessage(lang.getMessage("minecraft.adminmode.no-permission"));
            return true;
        }

        boolean nowActive = manager.toggle(player);

        if (nowActive) {
            player.sendMessage("");
            player.sendMessage(lang.getMessage("minecraft.adminmode.activated-header"));
            player.sendMessage(lang.getMessage("minecraft.adminmode.activated-line1"));
            player.sendMessage(lang.getMessage("minecraft.adminmode.activated-line2"));
            player.sendMessage(lang.getMessage("minecraft.adminmode.activated-line3"));
            player.sendMessage("");
            sendBossBarHint(player, true);
        } else {
            player.sendMessage("");
            player.sendMessage(lang.getMessage("minecraft.adminmode.deactivated-header"));
            player.sendMessage(lang.getMessage("minecraft.adminmode.deactivated-line1"));
            player.sendMessage(lang.getMessage("minecraft.adminmode.deactivated-line2"));
            player.sendMessage("");
            sendBossBarHint(player, false);
        }

        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            manager.onJoin(player);

            if (manager.isActive(player.getUniqueId())) {
                player.sendMessage("");
                player.sendMessage(lang.getMessage("minecraft.adminmode.restart-notice-header"));
                player.sendMessage(lang.getMessage("minecraft.adminmode.restart-notice-line1"));
                player.sendMessage(lang.getMessage("minecraft.adminmode.restart-notice-line2"));
                player.sendMessage("");
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.isActive(player.getUniqueId())) {
            plugin.getLogger().info("[AdminMode] " + player.getName()
                    + " hat den Server im Admin-Modus verlassen. Snapshot bleibt aktiv.");
        }
    }

    private void sendBossBarHint(Player player, boolean active) {
        try {
            org.bukkit.boss.BossBar bar = Bukkit.createBossBar(
                    active
                            ? lang.getMessage("minecraft.adminmode.bossbar-active")
                            : lang.getMessage("minecraft.adminmode.bossbar-inactive"),
                    active
                            ? org.bukkit.boss.BarColor.RED
                            : org.bukkit.boss.BarColor.GREEN,
                    org.bukkit.boss.BarStyle.SOLID
            );
            bar.addPlayer(player);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                bar.removePlayer(player);
                bar.setVisible(false);
            }, 100L);
        } catch (Exception ignored) {}
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return Collections.emptyList();
    }
}