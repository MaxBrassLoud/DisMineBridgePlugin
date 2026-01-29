package de.MaxBrassLoud.disMineBridge.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.features.vanish.VanishManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VanishJoinQuitListener implements Listener {

    private final DisMineBridge plugin;
    private final VanishManager vanishManager;

    public VanishJoinQuitListener(DisMineBridge plugin) {
        this.plugin = plugin;
        this.vanishManager = plugin.getVanishmanager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        // IMMER die Standard-Join-Nachricht blockieren
        event.setJoinMessage(null);

        // VanishManager lädt Spieler und entscheidet über Join-Nachricht
        vanishManager.loadPlayer(event.getPlayer());

        // Zeige vanished Spieler für Staff-Mitglieder
        vanishManager.showVanishedToStaff(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        // IMMER die Standard-Quit-Nachricht blockieren
        event.setQuitMessage(null);

        // Nur wenn der Spieler NICHT im Vanish ist, soll eine Leave-Nachricht erscheinen
        if (!vanishManager.isVanished(event.getPlayer())) {
            vanishManager.sendFakeQuit(event.getPlayer());
        }

        // Cleanup
        vanishManager.cleanup(event.getPlayer());
    }
}