package de.MaxBrassLoud.disMineBridgePlugin.vanish;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VanishManager {

    private static final Map<UUID, Long> vanished = new HashMap<>(); // UUID -> Vanish-Start-Zeit
    private static final Logger logger = Logger.getLogger("DisMineBridge");

    public static void loadPlayer(Player p) {
        try {
            String sql = "SELECT vanish FROM users WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                boolean v = rs.getBoolean("vanish");
                if (v) {
                    vanished.put(p.getUniqueId(), System.currentTimeMillis());
                    hide(p);
                    // Keine Join-Nachricht, da Spieler im Vanish ist
                } else {
                    sendFakeJoin(p);
                }
            } else {
                // Neuer Spieler
                DatabaseManager.getInstance().executeUpdate(
                        "INSERT INTO users (uuid, name, vanish) VALUES (?, ?, ?)",
                        p.getUniqueId().toString(),
                        p.getName(),
                        false
                );
                sendFakeJoin(p);
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Laden des Vanish-Status", e);
        }
    }

    public static void setVanish(Player p, boolean v) {
        if (v) {
            vanished.put(p.getUniqueId(), System.currentTimeMillis());
            hide(p);
            sendFakeQuit(p);
        } else {
            vanished.remove(p.getUniqueId());
            show(p);
            sendFakeJoin(p);
        }

        saveDB(p, v);
    }

    private static void saveDB(Player p, boolean v) {
        DatabaseManager.getInstance().executeUpdate(
                "UPDATE users SET vanish = ? WHERE uuid = ?",
                v ? 1 : 0,
                p.getUniqueId().toString()
        );
    }

    public static boolean isVanished(Player p) {
        return vanished.containsKey(p.getUniqueId());
    }

    public static List<Player> getVanishedPlayers() {
        return vanished.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static long getVanishDuration(Player p) {
        Long startTime = vanished.get(p.getUniqueId());
        if (startTime == null) return 0;
        return System.currentTimeMillis() - startTime;
    }

    public static int getPlayersWhoCanSee(Player vanishedPlayer) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(vanishedPlayer) && online.hasPermission("dmb.vanish.see")) {
                count++;
            }
        }
        return count;
    }

    private static void hide(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p) && !other.hasPermission("dmb.vanish.see")) {
                other.hidePlayer(p);
            }
        }
    }

    private static void show(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(p);
        }
    }

    public static void sendFakeQuit(Player p) {
        Bukkit.broadcastMessage("§e" + p.getName() + " left the game");
    }

    private static void sendFakeJoin(Player p) {
        Bukkit.broadcastMessage("§e" + p.getName() + " joined the game");
    }

    public static void cleanup(Player p) {
        vanished.remove(p.getUniqueId());
    }

    /**
     * Zeigt einen Vanish-Spieler für ein anderes Team-Mitglied
     * Wird beim Join aufgerufen
     */
    public static void showVanishedToStaff(Player staff) {
        if (!staff.hasPermission("dmb.vanish.see")) return;

        for (Player vanishedPlayer : getVanishedPlayers()) {
            staff.showPlayer(vanishedPlayer);
        }
    }
}