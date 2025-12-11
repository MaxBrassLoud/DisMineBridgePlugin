package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.vanish.VanishManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VanishJoinQuitListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        // IMMER die Standard-Join-Nachricht blockieren
        e.setJoinMessage(null);

        // VanishManager lädt Spieler und entscheidet über Join-Nachricht
        VanishManager.loadPlayer(e.getPlayer());

        // Zeige vanished Spieler für Staff-Mitglieder
        VanishManager.showVanishedToStaff(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        // IMMER die Standard-Quit-Nachricht blockieren
        e.setQuitMessage(null);

        // Nur wenn der Spieler NICHT im Vanish ist, soll eine Leave-Nachricht erscheinen
        if (!VanishManager.isVanished(e.getPlayer())) {
            VanishManager.sendFakeQuit(e.getPlayer());
        }

        // Cleanup
        VanishManager.cleanup(e.getPlayer());
    }
}