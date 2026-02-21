package de.MaxBrassLoud.disMineBridge.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Speichert und lädt das Inventar + Enderchest von Offline-Spielern
 * als JSON-Datei in plugins/DisMineBridge/players/inventory/<UUID>.json
 *
 * Kein NMS, keine Reflection – nutzt ausschließlich
 * Bukkit's BukkitObjectOutputStream (Base64-Serialisierung).
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │ JSON-Aufbau                                                 │
 * │  pendingChanges  – true wenn InvSee Änderungen vornimmt,   │
 * │                    wird beim nächsten Join angewendet       │
 * │  inventorySlots  – Hauptinv + Rüstung + Offhand            │
 * │  enderChestSlots – Enderchest                              │
 * │  inventoryAt     – Timestamp letzter Inv-Snapshot          │
 * │  enderChestAt    – Timestamp letzter EC-Snapshot           │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Slot-Nummern im JSON:
 *   0–35  → Hauptinventar (0–8 Hotbar, 9–35 Haupt)
 *   100   → Boots
 *   101   → Leggings
 *   102   → Chestplate
 *   103   → Helmet
 *   150   → Offhand
 *   0–26  → Enderchest (in enderChestSlots)
 *
 * Viewer-Slot-Mapping (aus InvSeeCommand, 54-Slot-Layout):
 *   Viewer 36 = Boots, 37 = Leggings, 38 = Chestplate,
 *   39 = Helmet, 40 = Offhand
 */
public class OfflineInventoryStore {

    // ── Viewer-Slot-Positionen (InvSeeCommand-Layout) ─────────────────────
    public static final int VIEW_BOOTS      = 36;
    public static final int VIEW_LEGGINGS   = 37;
    public static final int VIEW_CHESTPLATE = 38;
    public static final int VIEW_HELMET     = 39;
    public static final int VIEW_OFFHAND    = 40;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final UUID   uuid;
    private final Path   jsonFile;
    private final Logger log;

    // ─────────────────────────────────────────────────────────────────────

    public OfflineInventoryStore(UUID uuid, Path playersDir, Logger log) {
        this.uuid     = uuid;
        this.log      = log;
        this.jsonFile = playersDir.resolve(uuid + ".json");
    }

    /** Gibt true zurück wenn bereits eine JSON-Datei für diesen Spieler existiert. */
    public boolean exists() {
        return Files.exists(jsonFile);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Öffentliche API – Lesen
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Liest das Hauptinventar aus der JSON → 54-Slot-Array (InvSee-Viewer-Layout).
     * Gibt ein leeres Array zurück wenn keine Datei vorhanden.
     */
    public ItemStack[] readInventory() {
        OfflineJson data = load();
        ItemStack[] result = new ItemStack[54];
        if (data == null || data.inventorySlots == null) return result;

        for (SlotEntry e : data.inventorySlots) {
            ItemStack item = fromBase64(e.base64);
            if (item == null) continue;
            // JSON-Slot → Viewer-Slot
            if (e.slot >= 0 && e.slot <= 35)  result[e.slot]             = item;
            else if (e.slot == 100)            result[VIEW_BOOTS]         = item;
            else if (e.slot == 101)            result[VIEW_LEGGINGS]      = item;
            else if (e.slot == 102)            result[VIEW_CHESTPLATE]    = item;
            else if (e.slot == 103)            result[VIEW_HELMET]        = item;
            else if (e.slot == 150)            result[VIEW_OFFHAND]       = item;
        }
        return result;
    }

    /**
     * Liest den Enderchest aus der JSON → 27-Slot-Array.
     * Gibt ein leeres Array zurück wenn keine Datei vorhanden.
     */
    public ItemStack[] readEnderChest() {
        OfflineJson data = load();
        ItemStack[] result = new ItemStack[27];
        if (data == null || data.enderChestSlots == null) return result;

        for (SlotEntry e : data.enderChestSlots) {
            ItemStack item = fromBase64(e.base64);
            if (item != null && e.slot >= 0 && e.slot < 27) result[e.slot] = item;
        }
        return result;
    }

    /**
     * Gibt true zurück wenn ausstehende (unbestätigte) Änderungen vorliegen.
     * → Wird beim PlayerJoinEvent geprüft.
     */
    public boolean hasPendingChanges() {
        OfflineJson data = load();
        return data != null && data.pendingChanges;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Öffentliche API – Schreiben (aus InvSee)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Schreibt das Inventar (54-Slot-Viewer-Array) in die JSON.
     * Setzt pendingChanges = true damit der Spieler beim nächsten
     * Login die Änderungen erhält.
     */
    public boolean writeInventory(ItemStack[] viewerContents) {
        OfflineJson data = loadOrCreate();

        data.inventorySlots = new ArrayList<>();
        // Slots 0–35: Hauptinventar + Hotbar
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = get(viewerContents, slot);
            if (item == null || item.getType() == Material.AIR) continue;
            String b64 = toBase64(item);
            if (b64 != null) data.inventorySlots.add(new SlotEntry(slot, b64));
        }
        // Rüstung → JSON-Slots 100–103
        addIfPresent(data.inventorySlots, viewerContents, VIEW_BOOTS,      100);
        addIfPresent(data.inventorySlots, viewerContents, VIEW_LEGGINGS,   101);
        addIfPresent(data.inventorySlots, viewerContents, VIEW_CHESTPLATE, 102);
        addIfPresent(data.inventorySlots, viewerContents, VIEW_HELMET,     103);
        // Offhand → JSON-Slot 150
        addIfPresent(data.inventorySlots, viewerContents, VIEW_OFFHAND,    150);

        data.inventoryAt    = System.currentTimeMillis();
        data.pendingChanges = true;
        return save(data);
    }

    /**
     * Schreibt den Enderchest (27-Slot-Array) in die JSON.
     * Setzt pendingChanges = true.
     */
    public boolean writeEnderChest(ItemStack[] contents) {
        OfflineJson data = loadOrCreate();

        data.enderChestSlots = new ArrayList<>();
        for (int slot = 0; slot < Math.min(27, contents.length); slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) continue;
            String b64 = toBase64(item);
            if (b64 != null) data.enderChestSlots.add(new SlotEntry(slot, b64));
        }

        data.enderChestAt   = System.currentTimeMillis();
        data.pendingChanges = true;
        return save(data);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Öffentliche API – Anwenden beim Join
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Wendet alle ausstehenden Änderungen auf den Spieler an.
     * Gibt eine lesbare Zusammenfassung zurück (für Chat-Nachricht).
     * Setzt pendingChanges = false nach erfolgreichem Anwenden.
     */
    public ApplyResult applyPendingChanges(org.bukkit.entity.Player player) {
        OfflineJson data = load();
        if (data == null || !data.pendingChanges) return ApplyResult.NONE;

        boolean invChanged = false;
        boolean ecChanged  = false;

        // ── Hauptinventar anwenden ────────────────────────────────────────
        if (data.inventorySlots != null) {
            player.getInventory().clear();
            ItemStack[] armor = new ItemStack[4];

            for (SlotEntry e : data.inventorySlots) {
                ItemStack item = fromBase64(e.base64);
                if (item == null) continue;
                if      (e.slot >= 0 && e.slot <= 35) player.getInventory().setItem(e.slot, item);
                else if (e.slot == 100) armor[0] = item;
                else if (e.slot == 101) armor[1] = item;
                else if (e.slot == 102) armor[2] = item;
                else if (e.slot == 103) armor[3] = item;
                else if (e.slot == 150) player.getInventory().setItemInOffHand(item);
            }
            player.getInventory().setArmorContents(armor);
            player.updateInventory();
            invChanged = true;
        }

        // ── Enderchest anwenden ───────────────────────────────────────────
        if (data.enderChestSlots != null) {
            player.getEnderChest().clear();
            for (SlotEntry e : data.enderChestSlots) {
                ItemStack item = fromBase64(e.base64);
                if (item != null && e.slot >= 0 && e.slot < 27)
                    player.getEnderChest().setItem(e.slot, item);
            }
            ecChanged = true;
        }

        // ── Ausstehend löschen ────────────────────────────────────────────
        data.pendingChanges = false;
        save(data);

        log.info("[InvSee] Ausstehende Offline-Änderungen auf "
                + player.getName() + " angewendet."
                + (invChanged ? " [Inventar]" : "")
                + (ecChanged  ? " [Enderchest]" : ""));

        if (invChanged && ecChanged) return ApplyResult.BOTH;
        if (invChanged)              return ApplyResult.INVENTORY;
        if (ecChanged)               return ApplyResult.ENDER_CHEST;
        return ApplyResult.NONE;
    }

    /** Ergebnis von applyPendingChanges(). */
    public enum ApplyResult {
        NONE, INVENTORY, ENDER_CHEST, BOTH;

        public boolean anyChanged() { return this != NONE; }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Synchronisation vom Live-Spieler → JSON
    // (wird aufgerufen wenn ein Online-Spieler sein Inventar ändert,
    //  damit die JSON immer aktuell ist)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Erstellt einen Snapshot des aktuellen Live-Inventars eines Spielers.
     * Überschreibt vorhandene JSON-Daten, pendingChanges bleibt unberührt.
     * Wird beim PlayerQuitEvent aufgerufen.
     */
    public void snapshotFromPlayer(org.bukkit.entity.Player player) {
        OfflineJson data = loadOrCreate();

        // ── Position & Welt ───────────────────────────────────────────────
        org.bukkit.Location loc = player.getLocation();
        data.worldName   = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        data.x           = loc.getX();
        data.y           = loc.getY();
        data.z           = loc.getZ();
        data.yaw         = loc.getYaw();
        data.pitch       = loc.getPitch();
        data.environment = loc.getWorld() != null
                ? loc.getWorld().getEnvironment().name() : "NORMAL";

        // ── Vitalwerte ────────────────────────────────────────────────────
        data.health      = player.getHealth();
        data.maxHealth   = player.getMaxHealth();
        data.foodLevel   = player.getFoodLevel();
        data.saturation  = player.getSaturation();

        // ── Spielmodus & XP ───────────────────────────────────────────────
        data.gameMode    = player.getGameMode().name();
        data.xpLevel     = player.getLevel();
        data.xpProgress  = player.getExp();

        // ── Inventar ─────────────────────────────────────────────────────
        data.inventorySlots = new ArrayList<>();
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            String b64 = toBase64(item);
            if (b64 != null) data.inventorySlots.add(new SlotEntry(slot, b64));
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        int[] armorSlots = {100, 101, 102, 103};
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] == null || armor[i].getType() == Material.AIR) continue;
            String b64 = toBase64(armor[i]);
            if (b64 != null) data.inventorySlots.add(new SlotEntry(armorSlots[i], b64));
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            String b64 = toBase64(offhand);
            if (b64 != null) data.inventorySlots.add(new SlotEntry(150, b64));
        }
        data.inventoryAt = System.currentTimeMillis();

        // ── Enderchest ───────────────────────────────────────────────────
        data.enderChestSlots = new ArrayList<>();
        for (int slot = 0; slot < 27; slot++) {
            ItemStack item = player.getEnderChest().getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            String b64 = toBase64(item);
            if (b64 != null) data.enderChestSlots.add(new SlotEntry(slot, b64));
        }
        data.enderChestAt = System.currentTimeMillis();

        // pendingChanges NICHT anfassen (InvSee könnte bereits was ausstehend haben)
        save(data);
    }

    /**
     * Öffentlicher Snapshot der gespeicherten Spieler-Metadaten.
     * Wird von InvSeeCommand für den Spielerkopf genutzt.
     */
    public static class PlayerSnapshot {
        public final String worldName;
        public final double x, y, z;
        public final float  yaw, pitch;
        public final double health, maxHealth;
        public final int    foodLevel;
        public final float  saturation;
        public final String gameMode;
        public final int    xpLevel;
        public final float  xpProgress;
        public final String environment;
        public final long   snapshotAt; // Zeitpunkt des Snapshots (inventoryAt)

        private PlayerSnapshot(OfflineJson d) {
            this.worldName   = d.worldName   != null ? d.worldName   : "world";
            this.x           = d.x;
            this.y           = d.y;
            this.z           = d.z;
            this.yaw         = d.yaw;
            this.pitch       = d.pitch;
            this.health      = d.health;
            this.maxHealth   = d.maxHealth > 0 ? d.maxHealth : 20.0;
            this.foodLevel   = d.foodLevel;
            this.saturation  = d.saturation;
            this.gameMode    = d.gameMode    != null ? d.gameMode    : "SURVIVAL";
            this.xpLevel     = d.xpLevel;
            this.xpProgress  = d.xpProgress;
            this.environment = d.environment != null ? d.environment : "NORMAL";
            this.snapshotAt  = d.inventoryAt;
        }
    }

    /**
     * Gibt den zuletzt gespeicherten Spieler-Snapshot zurück.
     * Gibt null zurück wenn noch keine Datei existiert.
     */
    public PlayerSnapshot getSnapshot() {
        OfflineJson data = load();
        if (data == null || data.worldName == null) return null;
        return new PlayerSnapshot(data);
    }

    // ══════════════════════════════════════════════════════════════════════
    // JSON I/O
    // ══════════════════════════════════════════════════════════════════════

    private OfflineJson load() {
        if (!Files.exists(jsonFile)) return null;
        try {
            String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
            return GSON.fromJson(json, OfflineJson.class);
        } catch (Exception e) {
            log.warning("[InvSee] JSON lesen fehlgeschlagen (" + uuid + "): " + e.getMessage());
            return null;
        }
    }

    private OfflineJson loadOrCreate() {
        OfflineJson data = load();
        return data != null ? data : new OfflineJson();
    }

    private boolean save(OfflineJson data) {
        try {
            Files.createDirectories(jsonFile.getParent());
            Files.writeString(jsonFile, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            log.severe("[InvSee] JSON schreiben fehlgeschlagen (" + uuid + "): " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Base64-Serialisierung
    // ══════════════════════════════════════════════════════════════════════

    private String toBase64(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            log.warning("[InvSee] Item serialisieren fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }

    private ItemStack fromBase64(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                    new ByteArrayInputStream(bytes))) {
                return (ItemStack) in.readObject();
            }
        } catch (Exception e) {
            log.warning("[InvSee] Item deserialisieren fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════════

    private static ItemStack get(ItemStack[] arr, int i) {
        return (arr != null && i < arr.length) ? arr[i] : null;
    }

    private void addIfPresent(List<SlotEntry> list, ItemStack[] arr,
                              int viewerSlot, int jsonSlot) {
        ItemStack item = get(arr, viewerSlot);
        if (item == null || item.getType() == Material.AIR) return;
        String b64 = toBase64(item);
        if (b64 != null) list.add(new SlotEntry(jsonSlot, b64));
    }

    // ══════════════════════════════════════════════════════════════════════
    // JSON-Datenmodell
    // ══════════════════════════════════════════════════════════════════════

    /** Interne JSON-Struktur (Gson serialisiert/deserialisiert direkt). */
    private static class OfflineJson {
        /** true = InvSee hat Änderungen vorgenommen, die noch nicht angewendet wurden. */
        boolean pendingChanges = false;

        /** Unix-Timestamps der letzten Snapshots. */
        long inventoryAt  = 0;
        long enderChestAt = 0;

        // ── Spieler-Metadaten (beim Logout gespeichert) ───────────────────
        /** Position beim Logout */
        String worldName;
        double x, y, z;
        float  yaw, pitch;

        /** Vitalwerte beim Logout */
        double health;
        double maxHealth;
        int    foodLevel;
        float  saturation;

        /** Spielmodus beim Logout */
        String gameMode;

        /** Erfahrung beim Logout */
        int   xpLevel;
        float xpProgress;

        /** Dimension beim Logout (NORMAL / NETHER / THE_END) */
        String environment;

        /** Serialisierte Inventar-Slots (Hauptinv + Rüstung + Offhand). */
        List<SlotEntry> inventorySlots;

        /** Serialisierte Enderchest-Slots. */
        List<SlotEntry> enderChestSlots;
    }

    /** Einzelner Slot mit Base64-serialisiertem ItemStack. */
    private static class SlotEntry {
        int    slot;
        String base64;
        SlotEntry() {}
        SlotEntry(int slot, String base64) { this.slot = slot; this.base64 = base64; }
    }
}