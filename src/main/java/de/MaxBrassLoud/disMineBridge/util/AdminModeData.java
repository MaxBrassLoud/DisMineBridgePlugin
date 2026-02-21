package de.MaxBrassLoud.disMineBridge.util;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Speichert einen vollständigen Spieler-Snapshot für den Admin-Modus.
 * Wird als <UUID>.json in plugins/DisMineBridge/players/ persistiert.
 *
 * Felder:
 *   active          – ob der Spieler gerade im Admin-Modus ist
 *   worldName       – Welt zum Zeitpunkt der Aktivierung
 *   x, y, z         – Koordinaten
 *   yaw, pitch      – Blickrichtung
 *   health          – Herzen (double, 0–maxHealth)
 *   maxHealth       – maximale Herzen
 *   foodLevel       – Hunger (0–20)
 *   saturation      – Sättigung
 *   exhaustion      – Erschöpfung
 *   xpLevel         – XP-Level
 *   xpProgress      – XP-Fortschritt (0.0–1.0)
 *   totalExperience – Gesamt-XP-Punkte
 *   gameMode        – Spielmodus (SURVIVAL/CREATIVE/etc.)
 *   activatedAt     – Unix-Timestamp der Aktivierung (ms)
 *   inventory       – Base64-serialisierte Items (Slots 0–40)
 */
public class AdminModeData {

    /** true = Admin-Modus aktiv, Snapshot gültig */
    public boolean active;

    // ── Position ──────────────────────────────────────────────────────────
    public String worldName;
    public double x, y, z;
    public float  yaw, pitch;

    // ── Vitalwerte ────────────────────────────────────────────────────────
    public double health;
    public double maxHealth;
    public int    foodLevel;
    public float  saturation;
    public float  exhaustion;

    // ── Erfahrung ────────────────────────────────────────────────────────
    public int   xpLevel;
    public float xpProgress;
    public int   totalExperience;

    // ── Spielmodus ───────────────────────────────────────────────────────
    public String gameMode; // GameMode.name()

    // ── Metadaten ────────────────────────────────────────────────────────
    public long activatedAt;

    // ── Inventar (als Base64-Strings, eine Entry pro belegtem Slot) ──────
    // Serialisiertes Format: slot→base64(BukkitObjectOutputStream)
    public List<SlotEntry> inventorySlots;

    /** Einzelner Slot-Eintrag im gespeicherten Inventar. */
    public static class SlotEntry {
        public int    slot;
        public String base64; // BukkitObjectOutputStream-serialisiertes ItemStack

        public SlotEntry() {}
        public SlotEntry(int slot, String base64) {
            this.slot   = slot;
            this.base64 = base64;
        }
    }
}