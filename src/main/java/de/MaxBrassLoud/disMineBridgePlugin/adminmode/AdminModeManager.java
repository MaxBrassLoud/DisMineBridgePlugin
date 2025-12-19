package de.MaxBrassLoud.disMineBridgePlugin.adminmode;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminModeManager {

    public static final Set<UUID> adminModePlayers = new HashSet<>();
    private static final Logger logger = Logger.getLogger("DisMineBridge");

    public static void init() {
        // Tabelle wird jetzt vom DatabaseManager erstellt
    }

    public static boolean enableAdminMode(Player p) {
        try {
            savePlayerData(p);
            adminModePlayers.add(p.getUniqueId());

            // Clear everything
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getEnderChest().clear();

            // Entferne alle Potion Effects
            for (PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }

            // Setze Werte
            p.setGameMode(GameMode.CREATIVE);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExhaustion(0.0f);
            p.setExp(0.0f);
            p.setLevel(0);
            p.setAllowFlight(true);
            p.setFlying(false);

            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Aktivieren des AdminModes", e);
            return false;
        }
    }

    public static void disableAdminMode(Player p) {
        try {
            loadPlayerData(p);
            adminModePlayers.remove(p.getUniqueId());
            deletePlayerData(p.getUniqueId());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Deaktivieren des AdminModes", e);
        }
    }

    private static void savePlayerData(Player p) {
        try {
            Location loc = p.getLocation();
            DatabaseManager db = DatabaseManager.getInstance();

            String[] columns = {
                    "uuid", "gamemode", "health", "food_level", "saturation", "exhaustion",
                    "exp", "level", "fly_speed", "walk_speed", "world", "loc_x", "loc_y", "loc_z",
                    "loc_yaw", "loc_pitch", "inventory", "armor", "enderchest", "potion_effects", "timestamp"
            };

            String sql = db.getUpsertSQL("adminmode_data", columns, "uuid");

            db.executeUpdate(sql,
                    p.getUniqueId().toString(),
                    p.getGameMode().name(),
                    p.getHealth(),
                    p.getFoodLevel(),
                    p.getSaturation(),
                    p.getExhaustion(),
                    p.getExp(),
                    p.getLevel(),
                    p.getFlySpeed(),
                    p.getWalkSpeed(),
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch(),
                    serializeInventory(p.getInventory().getContents()),
                    serializeInventory(p.getInventory().getArmorContents()),
                    serializeInventory(p.getEnderChest().getContents()),
                    serializePotionEffects(p.getActivePotionEffects()),
                    System.currentTimeMillis()
            );

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern der AdminMode-Daten", e);
        }
    }

    private static void loadPlayerData(Player p) {
        try {
            String sql = "SELECT * FROM adminmode_data WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Gamemode
                GameMode gm = GameMode.valueOf(rs.getString("gamemode"));
                p.setGameMode(gm);

                // Health & Food
                p.setHealth(rs.getDouble("health"));
                p.setFoodLevel(rs.getInt("food_level"));
                p.setSaturation(rs.getFloat("saturation"));
                p.setExhaustion(rs.getFloat("exhaustion"));

                // Experience
                p.setExp(rs.getFloat("exp"));
                p.setLevel(rs.getInt("level"));

                // Speed
                p.setFlySpeed(rs.getFloat("fly_speed"));
                p.setWalkSpeed(rs.getFloat("walk_speed"));

                // Location
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world != null) {
                    Location loc = new Location(
                            world,
                            rs.getDouble("loc_x"),
                            rs.getDouble("loc_y"),
                            rs.getDouble("loc_z"),
                            rs.getFloat("loc_yaw"),
                            rs.getFloat("loc_pitch")
                    );
                    p.teleport(loc);
                }

                // Inventar
                ItemStack[] inventory = deserializeInventory(rs.getString("inventory"));
                ItemStack[] armor = deserializeInventory(rs.getString("armor"));
                ItemStack[] enderchest = deserializeInventory(rs.getString("enderchest"));

                p.getInventory().setContents(inventory);
                p.getInventory().setArmorContents(armor);
                p.getEnderChest().setContents(enderchest);

                // Potion Effects
                Collection<PotionEffect> effects = deserializePotionEffects(rs.getString("potion_effects"));
                for (PotionEffect effect : effects) {
                    p.addPotionEffect(effect);
                }

                p.updateInventory();
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Laden der AdminMode-Daten", e);
        }
    }

    private static void deletePlayerData(UUID uuid) {
        DatabaseManager.getInstance().executeUpdate("DELETE FROM adminmode_data WHERE uuid = ?", uuid.toString());
    }

    public static boolean isInAdminMode(Player p) {
        return adminModePlayers.contains(p.getUniqueId());
    }

    // ============================================================
    //  SERIALISIERUNG (Base64 für bessere Kompatibilität)
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
            return new ItemStack[41];
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
            return new ItemStack[41];
        }
    }

    private static String serializePotionEffects(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PotionEffect effect : effects) {
            sb.append(effect.getType().getName()).append(":")
                    .append(effect.getDuration()).append(":")
                    .append(effect.getAmplifier()).append(";");
        }
        return sb.toString();
    }

    private static Collection<PotionEffect> deserializePotionEffects(String data) {
        List<PotionEffect> effects = new ArrayList<>();
        if (data == null || data.isEmpty()) return effects;

        try {
            String[] parts = data.split(";");
            for (String part : parts) {
                if (part.isEmpty()) continue;
                String[] effectData = part.split(":");
                if (effectData.length >= 3) {
                    PotionEffectType type = PotionEffectType.getByName(effectData[0]);
                    if (type != null) {
                        int duration = Integer.parseInt(effectData[1]);
                        int amplifier = Integer.parseInt(effectData[2]);
                        effects.add(new PotionEffect(type, duration, amplifier));
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Fehler beim Deserialisieren der Potion-Effects", e);
        }

        return effects;
    }

    public static void onPlayerQuit(Player p) {
        adminModePlayers.remove(p.getUniqueId());
    }
}