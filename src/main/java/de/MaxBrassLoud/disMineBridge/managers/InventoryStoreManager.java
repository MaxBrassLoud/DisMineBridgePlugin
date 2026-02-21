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
 *
 * Aufgaben:
 *  1. PlayerQuitEvent  → Snapshot des Spielers in JSON schreiben
 *     (damit InvSee immer eine aktuelle Basis hat)
 *
 *  2. PlayerJoinEvent  → Prüfen ob pendingChanges=true in der JSON
 *     → falls ja: Inventar/Enderchest des Spielers überschreiben
 *     → Spieler informieren was geändert wurde
 *
 * Ordner: plugins/DisMineBridge/players/inventory/<UUID>.json
 */
public class InventoryStoreManager implements Listener {

    private final DisMineBridge plugin;
    private final Logger        log;
    private final Path          playersDir;

    // ─────────────────────────────────────────────────────────────────────

    public InventoryStoreManager(DisMineBridge plugin) {
        this.plugin     = plugin;
        this.log        = plugin.getLogger();
        this.playersDir = plugin.getDataFolder().toPath().resolve("players").resolve("inventory");

        try { Files.createDirectories(playersDir); } catch (Exception ignored) {}

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Öffentliche API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Gibt den OfflineInventoryStore für eine bestimmte UUID zurück.
     * Wird von InvSeeCommand benutzt um Offline-Inventare zu lesen/schreiben.
     */
    public OfflineInventoryStore storeFor(UUID uuid) {
        return new OfflineInventoryStore(uuid, playersDir, log);
    }

    /**
     * Gibt den players/inventory/-Pfad zurück.
     */
    public Path getPlayersDir() {
        return playersDir;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Events
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Beim Join: Ausstehende Inventar-Änderungen anwenden.
     * 1 Tick Verzögerung damit das Spieler-Inventar vollständig geladen ist.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Admin-Modus hat Vorrang: falls aktiv, kein Inventar-Restore
            if (plugin.getAdminModeManager().isActive(player.getUniqueId())) return;

            OfflineInventoryStore store = storeFor(player.getUniqueId());
            if (!store.hasPendingChanges()) return;

            OfflineInventoryStore.ApplyResult result = store.applyPendingChanges(player);
            if (!result.anyChanged()) return;

            // Spieler informieren
            player.sendMessage("");
            player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("  §6§l⟳ Inventar-Aktualisierung");
            player.sendMessage("  §7Ein Administrator hat dein Inventar");
            player.sendMessage("  §7während deiner Abwesenheit bearbeitet:");

            switch (result) {
                case INVENTORY   -> player.sendMessage("  §a✔ §fInventar aktualisiert");
                case ENDER_CHEST -> player.sendMessage("  §a✔ §fEnderchest aktualisiert");
                case BOTH        -> {
                    player.sendMessage("  §a✔ §fInventar aktualisiert");
                    player.sendMessage("  §a✔ §fEnderchest aktualisiert");
                }
                default -> {} // NONE → wird oben abgefangen
            }

            player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");

        }, 2L); // 2 Ticks (~100ms) – sicher nach vollständigem Load
    }

    /**
     * Beim Quit: Aktuelles Inventar in JSON sichern damit InvSee
     * beim nächsten Öffnen den richtigen Stand sieht.
     *
     * Achtung: Spieler im Admin-Modus haben ein leeres Inventar →
     * keinen Snapshot machen (würde Admin-Snapshot überschreiben).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Im Admin-Modus kein Snapshot (Inventar ist gecleart)
        if (plugin.getAdminModeManager().isActive(player.getUniqueId())) return;

        storeFor(player.getUniqueId()).snapshotFromPlayer(player);
    }
}