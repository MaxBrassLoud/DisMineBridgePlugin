package de.MaxBrassLoud.disMineBridge.features.vanish;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class VanishManager {

    private final DisMineBridge plugin;
    private final DatabaseManager database;
    private final LanguageManager language;
    private final Map<UUID, VanishData> vanishedPlayers;

    public VanishManager(DisMineBridge plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
        this.language = plugin.getLanguageManager();
        this.vanishedPlayers = new HashMap<>();
    }

    private static class VanishData {
        int userId;
        long startTime;

        VanishData(int userId, long startTime) {
            this.userId = userId;
            this.startTime = startTime;
        }
    }

    public void loadPlayer(Player p) {
        try {
            int userId = database.createOrGetUser(getDiscordIdFromPlayer(p));
            boolean isVanished = database.getVanishStatus(userId);

            if (isVanished) {
                long startTime = database.getVanishStartTime(userId);
                vanishedPlayers.put(p.getUniqueId(), new VanishData(userId, startTime));
                hide(p);
                // Keine Join-Nachricht, da Spieler im Vanish ist
            } else {
                sendFakeJoin(p);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden des Vanish-Status für " + p.getName(), e);
            sendFakeJoin(p); // Fallback
        }
    }

    public void setVanish(Player p, boolean vanish) {
        try {
            int userId = database.createOrGetUser(getDiscordIdFromPlayer(p));

            if (vanish) {
                long startTime = System.currentTimeMillis();
                vanishedPlayers.put(p.getUniqueId(), new VanishData(userId, startTime));
                hide(p);
                sendFakeQuit(p);
            } else {
                vanishedPlayers.remove(p.getUniqueId());
                show(p);
                sendFakeJoin(p);
            }

            database.setVanishStatus(userId, vanish);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Setzen des Vanish-Status für " + p.getName(), e);
        }
    }

    public boolean isVanished(Player p) {
        return vanishedPlayers.containsKey(p.getUniqueId());
    }

    public List<Player> getVanishedPlayers() {
        return vanishedPlayers.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public long getVanishDuration(Player p) {
        VanishData data = vanishedPlayers.get(p.getUniqueId());
        if (data == null) return 0;
        return System.currentTimeMillis() - data.startTime;
    }

    public int getPlayersWhoCanSee(Player vanishedPlayer) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(vanishedPlayer) && online.hasPermission("dmb.vanish.see")) {
                count++;
            }
        }
        return count;
    }

    private void hide(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p) && !other.hasPermission("dmb.vanish.see")) {
                other.hidePlayer(plugin, p);
            }
        }
    }

    private void show(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, p);
        }
    }

    public void sendFakeQuit(Player p) {
        String message = language.getMessage("minecraft.vanish.fake-quit")
                .replace("{player}", p.getName());
        Bukkit.broadcastMessage(message);
    }

    private void sendFakeJoin(Player p) {
        String message = language.getMessage("minecraft.vanish.fake-join")
                .replace("{player}", p.getName());
        Bukkit.broadcastMessage(message);
    }

    public void cleanup(Player p) {
        vanishedPlayers.remove(p.getUniqueId());
    }

    /**
     * Zeigt einen Vanish-Spieler für ein anderes Team-Mitglied
     * Wird beim Join aufgerufen
     */
    public void showVanishedToStaff(Player staff) {
        if (!staff.hasPermission("dmb.vanish.see")) return;

        for (Player vanishedPlayer : getVanishedPlayers()) {
            staff.showPlayer(plugin, vanishedPlayer);
        }
    }

    /**
     * NEU: Versteckt alle vanished Spieler vor einem bestimmten Spieler
     * Wird beim Join aufgerufen für normale Spieler
     */
    public void hideAllVanishedFrom(Player player) {
        for (Player vanishedPlayer : getVanishedPlayers()) {
            player.hidePlayer(plugin, vanishedPlayer);
        }
    }

    /**
     * Hilfsmethode um Discord ID vom Spieler zu erhalten
     * Diese muss entsprechend deiner Implementation angepasst werden
     */
    private String getDiscordIdFromPlayer(Player p) {
        // TODO: Implementiere die Logik um die Discord ID vom Spieler zu erhalten
        // Dies könnte über eine separate Tabelle oder ein Mapping erfolgen
        return p.getUniqueId().toString(); // Placeholder
    }
}