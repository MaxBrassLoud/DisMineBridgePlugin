package de.MaxBrassLoud.disMineBridgePlugin.playerdata;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verwaltet Spieler-Inventare und Daten für Online UND Offline Spieler
 */
public class PlayerDataManager {

    private static final Logger logger = Logger.getLogger("DisMineBridge");

    /**
     * Speichert alle Spielerdaten beim Logout
     */
    public static void savePlayerData(Player player) {
        try {
            Location loc = player.getLocation();
            DatabaseManager db = DatabaseManager.getInstance();

            String[] columns = {
                    "uuid", "name", "inventory", "armor", "enderchest",
                    "location_world", "location_x", "location_y", "location_z",
                    "location_yaw", "location_pitch", "gamemode", "health",
                    "food_level", "exp", "level", "last_seen", "last_updated"
            };

            String sql = db.getUpsertSQL("player_data", columns, "uuid");

            db.executeUpdate(sql,
                    player.getUniqueId().toString(),
                    player.getName(),
                    serializeInventory(player.getInventory().getContents()),
                    serializeInventory(player.getInventory().getArmorContents()),
                    serializeInventory(player.getEnderChest().getContents()),
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch(),
                    player.getGameMode().name(),
                    player.getHealth(),
                    player.getFoodLevel(),
                    player.getExp(),
                    player.getLevel(),
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            );

            logger.info("[PlayerData] Daten von " + player.getName() + " gespeichert.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern der Spielerdaten für " + player.getName(), e);
        }
    }

    /**
     * Lädt Inventar für Offline-Spieler
     */
    public static PlayerData loadOfflinePlayerData(UUID uuid, String name) {
        try {
            String sql = "SELECT * FROM player_data WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                PlayerData data = new PlayerData();
                data.uuid = uuid;
                data.name = rs.getString("name");
                data.inventory = deserializeInventory(rs.getString("inventory"));
                data.armor = deserializeInventory(rs.getString("armor"));
                data.enderchest = deserializeInventory(rs.getString("enderchest"));
                data.isOnline = false;
                data.lastSeen = rs.getLong("last_seen");

                rs.close();
                ps.close();

                return data;
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Laden der Offline-Daten für " + name, e);
        }

        return null;
    }

    /**
     * Speichert nur Inventar/Enderchest für Offline-Spieler (nach InvSee/EnderSee)
     */
    public static void saveOfflineInventory(UUID uuid, ItemStack[] inventory, ItemStack[] armor, ItemStack[] enderchest) {
        try {
            String sql = "UPDATE player_data SET inventory = ?, armor = ?, enderchest = ?, last_updated = ? WHERE uuid = ?";
            DatabaseManager.getInstance().executeUpdate(sql,
                    serializeInventory(inventory),
                    serializeInventory(armor),
                    serializeInventory(enderchest),
                    System.currentTimeMillis(),
                    uuid.toString()
            );

            logger.info("[PlayerData] Offline-Inventar für UUID " + uuid + " gespeichert.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern des Offline-Inventars", e);
        }
    }

    /**
     * Prüft ob Spielerdaten existieren
     */
    public static boolean hasPlayerData(UUID uuid) {
        try {
            String sql = "SELECT uuid FROM player_data WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid.toString());
            ResultSet rs = ps.executeQuery();

            boolean exists = rs.next();
            rs.close();
            ps.close();

            return exists;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Fehler beim Prüfen der Spielerdaten", e);
            return false;
        }
    }

    /**
     * Erstellt ersten Eintrag für neuen Spieler
     */
    public static void createFirstJoinEntry(Player player) {
        try {
            long now = System.currentTimeMillis();
            String sql = "INSERT INTO player_data (uuid, name, first_join, last_seen, last_updated) VALUES (?, ?, ?, ?, ?)";
            DatabaseManager.getInstance().executeUpdate(sql,
                    player.getUniqueId().toString(),
                    player.getName(),
                    now,
                    now,
                    now
            );

            logger.info("[PlayerData] Erster Join-Eintrag für " + player.getName() + " erstellt.");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Fehler beim Erstellen des First-Join-Eintrags", e);
        }
    }

    // ============================================================
    //  SERIALISIERUNG
    // ============================================================

    private static String serializeInventory(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Fehler beim Serialisieren des Inventars", e);
            return "";
        }
    }

    private static ItemStack[] deserializeInventory(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();

            return items;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Fehler beim Deserialisieren des Inventars", e);
            return new ItemStack[0];
        }
    }

    // ============================================================
    //  DATA CLASS
    // ============================================================

    public static class PlayerData {
        public UUID uuid;
        public String name;
        public ItemStack[] inventory;
        public ItemStack[] armor;
        public ItemStack[] enderchest;
        public boolean isOnline;
        public long lastSeen;
    }
}