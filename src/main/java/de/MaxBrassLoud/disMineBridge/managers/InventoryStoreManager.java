package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.util.OfflineInventoryStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Zentrale Verwaltung der Offline-Inventar-Snapshots.
 */
public class InventoryStoreManager implements Listener {

    private final DisMineBridge plugin;
    private final Logger        log;
    private final Path          playersDir;

    public InventoryStoreManager(DisMineBridge plugin) {
        this.plugin     = plugin;
        this.log        = plugin.getLogger();
        this.playersDir = plugin.getDataFolder().toPath().resolve("players").resolve("inventory");

        try { Files.createDirectories(playersDir); } catch (Exception ignored) {}

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public OfflineInventoryStore storeFor(UUID uuid) {
        return new OfflineInventoryStore(uuid, playersDir, log);
    }

    public Path getPlayersDir() {
        return playersDir;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LanguageManager lang = plugin.getLanguageManager();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            if (plugin.getAdminModeManager().isActive(player.getUniqueId())) return;

            OfflineInventoryStore store = storeFor(player.getUniqueId());
            if (!store.hasPendingChanges()) return;

            OfflineInventoryStore.ApplyResult result = store.applyPendingChanges(player);
            if (!result.anyChanged()) return;

            player.sendMessage("");
            player.sendMessage(lang.getMessage("minecraft.invsee.pending.header"));
            player.sendMessage(lang.getMessage("minecraft.invsee.pending.title"));
            player.sendMessage(lang.getMessage("minecraft.invsee.pending.description-1"));
            player.sendMessage(lang.getMessage("minecraft.invsee.pending.description-2"));

            switch (result) {
                case INVENTORY   -> player.sendMessage(lang.getMessage("minecraft.invsee.pending.inventory-updated"));
                case ENDER_CHEST -> player.sendMessage(lang.getMessage("minecraft.invsee.pending.enderchest-updated"));
                case BOTH        -> {
                    player.sendMessage(lang.getMessage("minecraft.invsee.pending.inventory-updated"));
                    player.sendMessage(lang.getMessage("minecraft.invsee.pending.enderchest-updated"));
                }
                default -> {}
            }

            player.sendMessage(lang.getMessage("minecraft.invsee.pending.footer"));
            player.sendMessage("");

        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getAdminModeManager().isActive(player.getUniqueId())) return;

        storeFor(player.getUniqueId()).snapshotFromPlayer(player);
    }
}