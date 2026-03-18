package de.MaxBrassLoud.disMineBridge.features.adminmode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.util.AdminModeData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class AdminModeManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final DisMineBridge plugin;
    private final Logger        log;
    private final Path          playersDir;

    private final Set<UUID> activeAdmins = new HashSet<>();

    public AdminModeManager(DisMineBridge plugin) {
        this.plugin     = plugin;
        this.log        = plugin.getLogger();
        this.playersDir = plugin.getDataFolder().toPath().resolve("players").resolve("admin");

        try {
            Files.createDirectories(playersDir);
        } catch (IOException e) {
            log.severe("[AdminMode] Konnte players/-Ordner nicht erstellen: " + e.getMessage());
        }

        loadActiveCache();
    }

    public boolean isActive(UUID uuid) {
        return activeAdmins.contains(uuid);
    }

    public boolean toggle(Player player) {
        if (isActive(player.getUniqueId())) {
            deactivate(player);
            return false;
        } else {
            activate(player);
            return true;
        }
    }

    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();

        AdminModeData data = snapshot(player);
        data.active      = true;
        data.activatedAt = System.currentTimeMillis();

        if (!saveData(uuid, data)) {
            player.sendMessage(lang.getMessage("minecraft.adminmode.snapshot-error"));
            return;
        }

        clearPlayer(player);
        activeAdmins.add(uuid);

        log.info("[AdminMode] " + player.getName() + " hat den Admin-Modus aktiviert."
                + " Snapshot gespeichert in players/admin/" + uuid + ".json");
    }

    public void deactivate(Player player) {
        UUID uuid = player.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();
        AdminModeData data = loadData(uuid);

        if (data == null || !data.active) {
            player.sendMessage(lang.getMessage("minecraft.adminmode.no-snapshot"));
            activeAdmins.remove(uuid);
            return;
        }

        restore(player, data);

        data.active = false;
        saveData(uuid, data);
        activeAdmins.remove(uuid);

        log.info("[AdminMode] " + player.getName() + " hat den Admin-Modus deaktiviert. Snapshot wiederhergestellt.");
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        AdminModeData data = loadData(uuid);

        if (data != null && data.active) {
            activeAdmins.add(uuid);
            clearPlayer(player);
            log.info("[AdminMode] " + player.getName() + " ist mit aktivem Admin-Modus gejoint.");
        }
    }

    private AdminModeData snapshot(Player p) {
        AdminModeData d = new AdminModeData();

        Location loc  = p.getLocation();
        d.worldName   = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        d.x           = loc.getX();
        d.y           = loc.getY();
        d.z           = loc.getZ();
        d.yaw         = loc.getYaw();
        d.pitch       = loc.getPitch();

        d.health       = p.getHealth();
        d.maxHealth    = p.getMaxHealth();
        d.foodLevel    = p.getFoodLevel();
        d.saturation   = p.getSaturation();
        d.exhaustion   = p.getExhaustion();

        d.xpLevel         = p.getLevel();
        d.xpProgress      = p.getExp();
        d.totalExperience = p.getTotalExperience();

        d.gameMode = p.getGameMode().name();

        d.inventorySlots = serializeInventory(p.getInventory());

        return d;
    }

    private void restore(Player p, AdminModeData d) {
        p.getInventory().clear();
        deserializeInventory(p.getInventory(), d.inventorySlots);

        World world = Bukkit.getWorld(d.worldName);
        if (world == null) {
            log.warning("[AdminMode] Welt '" + d.worldName + "' nicht gefunden. Teleport übersprungen.");
        } else {
            Location target = new Location(world, d.x, d.y, d.z, d.yaw, d.pitch);
            p.teleport(target);
        }

        p.setMaxHealth(d.maxHealth);
        p.setHealth(Math.min(d.health, d.maxHealth));
        p.setFoodLevel(d.foodLevel);
        p.setSaturation(d.saturation);
        p.setExhaustion(d.exhaustion);

        p.setLevel(d.xpLevel);
        p.setExp(d.xpProgress);
        p.setTotalExperience(d.totalExperience);

        try {
            p.setGameMode(GameMode.valueOf(d.gameMode));
        } catch (IllegalArgumentException e) {
            p.setGameMode(GameMode.SURVIVAL);
        }

        p.updateInventory();
    }

    private void clearPlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(null);
        p.setGameMode(GameMode.CREATIVE);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
        p.updateInventory();
    }

    private List<AdminModeData.SlotEntry> serializeInventory(PlayerInventory inv) {
        List<AdminModeData.SlotEntry> list = new ArrayList<>();

        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) continue;
            String b64 = itemToBase64(item);
            if (b64 != null) list.add(new AdminModeData.SlotEntry(slot, b64));
        }

        ItemStack[] armor = inv.getArmorContents();
        int[] armorSlots  = {100, 101, 102, 103};
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] == null) continue;
            String b64 = itemToBase64(armor[i]);
            if (b64 != null) list.add(new AdminModeData.SlotEntry(armorSlots[i], b64));
        }

        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null) {
            String b64 = itemToBase64(offhand);
            if (b64 != null) list.add(new AdminModeData.SlotEntry(150, b64));
        }

        return list;
    }

    private void deserializeInventory(PlayerInventory inv, List<AdminModeData.SlotEntry> slots) {
        if (slots == null) return;

        ItemStack[] armor = new ItemStack[4];

        for (AdminModeData.SlotEntry entry : slots) {
            ItemStack item = itemFromBase64(entry.base64);
            if (item == null) continue;

            if (entry.slot >= 0 && entry.slot <= 35) {
                inv.setItem(entry.slot, item);
            } else if (entry.slot == 100) {
                armor[0] = item;
            } else if (entry.slot == 101) {
                armor[1] = item;
            } else if (entry.slot == 102) {
                armor[2] = item;
            } else if (entry.slot == 103) {
                armor[3] = item;
            } else if (entry.slot == 150) {
                inv.setItemInOffHand(item);
            }
        }

        inv.setArmorContents(armor);
    }

    private String itemToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            log.warning("[AdminMode] Item-Serialisierung fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }

    private ItemStack itemFromBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                    new ByteArrayInputStream(bytes))) {
                return (ItemStack) in.readObject();
            }
        } catch (Exception e) {
            log.warning("[AdminMode] Item-Deserialisierung fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }

    private boolean saveData(UUID uuid, AdminModeData data) {
        Path file = playersDir.resolve(uuid + ".json");
        try {
            String json = GSON.toJson(data);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            log.severe("[AdminMode] JSON schreiben fehlgeschlagen (" + uuid + "): " + e.getMessage());
            return false;
        }
    }

    private AdminModeData loadData(UUID uuid) {
        Path file = playersDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(json, AdminModeData.class);
        } catch (Exception e) {
            log.severe("[AdminMode] JSON lesen fehlgeschlagen (" + uuid + "): " + e.getMessage());
            return null;
        }
    }

    private void loadActiveCache() {
        try {
            if (!Files.exists(playersDir)) return;
            Files.list(playersDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            String json = Files.readString(file, StandardCharsets.UTF_8);
                            AdminModeData data = GSON.fromJson(json, AdminModeData.class);
                            if (data != null && data.active) {
                                String name = file.getFileName().toString().replace(".json", "");
                                activeAdmins.add(UUID.fromString(name));
                            }
                        } catch (Exception ignored) {}
                    });
            if (!activeAdmins.isEmpty())
                log.info("[AdminMode] " + activeAdmins.size()
                        + " Spieler mit aktivem Admin-Modus aus JSON geladen.");
        } catch (IOException e) {
            log.warning("[AdminMode] Cache-Initialisierung fehlgeschlagen: " + e.getMessage());
        }
    }
}