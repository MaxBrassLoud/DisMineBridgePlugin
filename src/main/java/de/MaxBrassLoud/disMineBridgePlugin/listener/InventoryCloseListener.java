package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.command.endersee;
import de.MaxBrassLoud.disMineBridgePlugin.command.invsee;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class InventoryCloseListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player viewer)) return;

        String title = e.getView().getTitle();

        // Prüfe InvSee
        if (title.contains("Inventar:")) {
            invsee.OfflinePlayerData data = invsee.getOpenInventory(viewer.getUniqueId());
            if (data != null) {
                if (data.isOnline) {
                    Player target = Bukkit.getPlayer(data.targetUUID);
                    if (target != null && target.isOnline()) {
                        invsee.saveOnlineInventory(target, e.getInventory());
                        viewer.sendMessage(ChatColor.GREEN + "✔ Änderungen am Inventar von " +
                                ChatColor.YELLOW + data.playerName +
                                ChatColor.GREEN + " wurden gespeichert.");
                    }
                }
                invsee.removeOpenInventory(viewer.getUniqueId());
            }
        }

        // Prüfe EnderSee
        if (title.toLowerCase().contains("enderchest:")) {
            endersee.OfflineEnderData data = endersee.getOpenEnderchest(viewer.getUniqueId());
            if (data != null) {
                if (data.isOnline) {
                    Player target = Bukkit.getPlayer(data.targetUUID);
                    if (target != null && target.isOnline()) {
                        endersee.notifyChange(viewer, target);
                    }
                }
                endersee.removeOpenEnderchest(viewer.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player viewer)) return;

        String title = e.getView().getTitle();

        // Prüfe ob es ein InvSee-Inventar ist
        if (!title.contains("Inventar:")) return;

        int slot = e.getRawSlot();

        // Slot außerhalb des Inventars (unteres Inventar des Viewers)
        if (slot >= 54) return;

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null) return;

        // Blockiere Info-Items (Slots 45, 47, 49, 51)
        if (slot == 45 || slot == 47 || slot == 49 || slot == 51) {
            if (isInfoItem(clickedItem)) {
                e.setCancelled(true);
                viewer.sendMessage(ChatColor.YELLOW + "⚠ Dies ist ein Info-Item und kann nicht verschoben werden.");
                return;
            }
        }

        // Blockiere Glasscheiben (Slots 36-44)
        if (slot >= 36 && slot <= 44) {
            if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                e.setCancelled(true);
                return;
            }
        }

        // Erlaube alle anderen Slots
    }

    private boolean isInfoItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        Material type = item.getType();
        return type == Material.IRON_HELMET ||
                type == Material.IRON_CHESTPLATE ||
                type == Material.IRON_LEGGINGS ||
                type == Material.IRON_BOOTS;
    }
}