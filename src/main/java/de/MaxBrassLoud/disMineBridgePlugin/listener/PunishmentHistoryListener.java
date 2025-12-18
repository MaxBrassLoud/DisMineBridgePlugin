package de.MaxBrassLoud.disMineBridgePlugin.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Verhindert Manipulation der Historie-GUI
 */
public class PunishmentHistoryListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Prüfe ob es ein Historie-GUI ist
        if (title.contains("Historie:") || title.contains("Strafhistorie")) {
            event.setCancelled(true);

            if (event.getWhoClicked() instanceof Player player) {
                // Erlaube nur Zurück-Button und Schließen
                if (event.getCurrentItem() != null) {
                    String displayName = ChatColor.stripColor(
                            event.getCurrentItem().getItemMeta() != null ?
                                    event.getCurrentItem().getItemMeta().getDisplayName() : ""
                    );

                    // Zurück-Button darf geklickt werden
                    if (displayName.contains("Zurück") || displayName.equals("« Zurück")) {
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();

        if (title.contains("Historie:") || title.contains("Strafhistorie")) {
            event.setCancelled(true);
        }
    }
}