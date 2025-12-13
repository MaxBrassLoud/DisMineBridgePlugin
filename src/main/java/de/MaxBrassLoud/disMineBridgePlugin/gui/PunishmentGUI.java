package de.MaxBrassLoud.disMineBridgePlugin.gui;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Komplettes GUI-System für Strafen mit automatischer Berechnung
 */
public class PunishmentGUI {

    private static final Logger logger = Logger.getLogger("DisMineBridge");

    // ============================================================
    //  HAUPTMENÜS
    // ============================================================

    /**
     * Öffnet das Hauptmenü für Strafen
     */
    public static void openMainMenu(Player viewer, OfflinePlayer target) {
        String title = ChatColor.DARK_GRAY + "» " + ChatColor.GOLD + "Strafen: " + ChatColor.YELLOW + target.getName();
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Spieler-Kopf in der Mitte oben
        inv.setItem(4, createPlayerHead(target));

        // Strafen-Items
        inv.setItem(10, createBanItem());
        inv.setItem(12, createMuteItem());
        inv.setItem(14, createKickItem());
        inv.setItem(16, createWarnItem());

        // Historie
        inv.setItem(22, createHistoryItem(target));

        // Zurück-Button
        inv.setItem(26, createBackItem());

        // Füllmaterial
        fillEmptySlots(inv);

        viewer.openInventory(inv);
    }

    /**
     * Öffnet Grund-Auswahl für bestimmte Strafe
     */
    public static void openReasonMenu(Player viewer, OfflinePlayer target, PunishmentType type) {
        String title = ChatColor.DARK_GRAY + "» " + ChatColor.GOLD + "Grund wählen: " + ChatColor.YELLOW + type.getDisplayName();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        List<PunishmentReason> reasons = loadReasonsFromDatabase(type);

        // Füge Gründe hinzu (max 45 Slots, letzten 9 für Navigation)
        for (int i = 0; i < reasons.size() && i < 45; i++) {
            inv.setItem(i, createReasonItem(reasons.get(i)));
        }

        // Navigation
        inv.setItem(49, createBackItem());

        // Benutzerdefinierter Grund
        inv.setItem(53, createCustomReasonItem());

        // Fülle Rest
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createFillerItem());
            }
        }

        viewer.openInventory(inv);
    }

    /**
     * Öffnet Dauer-Auswahl für Strafe
     */
    public static void openDurationMenu(Player viewer, OfflinePlayer target, PunishmentType type, String reason) {
        // Berechne empfohlene Strafe basierend auf Historie
        PunishmentCalculation calc = calculatePunishment(target, type, reason);

        String title = ChatColor.DARK_GRAY + "» " + ChatColor.GOLD + "Dauer wählen";
        Inventory inv = Bukkit.createInventory(null, 45, title);

        // Info über vorherige Vergehen
        inv.setItem(4, createPunishmentInfoItem(target, type, calc));

        // Empfohlene Strafe (in der Mitte)
        inv.setItem(13, createRecommendedDurationItem(calc));

        // Vordefinierte Dauern (abhängig vom Typ)
        List<DurationOption> options = getDurationOptions(type, calc.minimumDuration);
        int[] slots = {19, 20, 21, 22, 23, 24, 25}; // Untere Reihe

        for (int i = 0; i < options.size() && i < slots.length; i++) {
            inv.setItem(slots[i], createDurationOptionItem(options.get(i), calc.minimumDuration));
        }

        // Benutzerdefinierte Dauer
        inv.setItem(31, createCustomDurationItem(calc.minimumDuration));

        // Navigation
        inv.setItem(36, createBackItem());
        inv.setItem(44, createExecuteItem());

        // Fülle Rest
        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createFillerItem());
            }
        }

        viewer.openInventory(inv);
    }

    /**
     * Zeigt Strafhistorie eines Spielers
     */
    public static void openHistoryMenu(Player viewer, OfflinePlayer target) {
        String title = ChatColor.DARK_GRAY + "» " + ChatColor.BLUE + "Historie: " + ChatColor.YELLOW + target.getName();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        List<PunishmentRecord> history = loadPunishmentHistory(target);

        // Zeige letzte 45 Strafen
        for (int i = 0; i < history.size() && i < 45; i++) {
            inv.setItem(i, createHistoryRecordItem(history.get(i)));
        }

        // Navigation
        inv.setItem(49, createBackItem());

        // Fülle Rest
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createFillerItem());
            }
        }

        viewer.openInventory(inv);
    }

    // ============================================================
    //  ITEM CREATION
    // ============================================================

    private static ItemStack createPlayerHead(OfflinePlayer target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.YELLOW + ChatColor.BOLD.toString() + target.getName());

            // Lade Statistiken
            PunishmentStats stats = loadPlayerStats(target);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Strafen-Statistik:");
            lore.add(ChatColor.RED + "  Bans: " + ChatColor.WHITE + stats.totalBans);
            lore.add(ChatColor.GOLD + "  Mutes: " + ChatColor.WHITE + stats.totalMutes);
            lore.add(ChatColor.YELLOW + "  Warns: " + ChatColor.WHITE + stats.totalWarns);
            lore.add(ChatColor.GRAY + "  Kicks: " + ChatColor.WHITE + stats.totalKicks);
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Status: " + (target.isOnline() ?
                    ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));

            meta.setLore(lore);
            skull.setItemMeta(meta);
        }

        return skull;
    }

    private static ItemStack createBanItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + ChatColor.BOLD.toString() + "BAN");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Bannt den Spieler temporär");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Die Dauer wird automatisch");
            lore.add(ChatColor.YELLOW + "basierend auf Vorstrafen berechnet");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Fortfahren");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createMuteItem() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + "MUTE");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Muted den Spieler");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Die Dauer wird automatisch");
            lore.add(ChatColor.YELLOW + "basierend auf Vorstrafen berechnet");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Fortfahren");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createKickItem() {
        ItemStack item = new ItemStack(Material.IRON_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + ChatColor.BOLD.toString() + "KICK");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Kickt den Spieler vom Server");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Fortfahren");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createWarnItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + "WARNUNG");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Verwarnt den Spieler");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Mehrere Warnungen führen");
            lore.add(ChatColor.YELLOW + "zu automatischen Bans");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Fortfahren");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createHistoryItem(OfflinePlayer target) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + ChatColor.BOLD.toString() + "STRAFHISTORIE");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Zeigt alle Strafen des Spielers");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Anzeigen");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + ChatColor.BOLD.toString() + "« Zurück");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Zurück zum vorherigen Menü");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createReasonItem(PunishmentReason reason) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + reason.name);

            List<String> lore = new ArrayList<>();
            lore.add("");

            if (reason.description != null && !reason.description.isEmpty()) {
                lore.add(ChatColor.GRAY + reason.description);
                lore.add("");
            }

            lore.add(ChatColor.DARK_GRAY + "Standard-Dauer:");
            lore.add(ChatColor.WHITE + "  " + formatDuration(reason.defaultDuration));
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Auswählen");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createCustomReasonItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + ChatColor.BOLD.toString() + "EIGENER GRUND");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Gib einen eigenen Grund ein");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Eingeben");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createPunishmentInfoItem(OfflinePlayer target, PunishmentType type, PunishmentCalculation calc) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + "VORHERIGE VERGEHEN");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "Spieler: " + ChatColor.WHITE + target.getName());
            lore.add(ChatColor.YELLOW + "Strafart: " + ChatColor.WHITE + type.getDisplayName());
            lore.add("");
            lore.add(ChatColor.GRAY + "Anzahl vorheriger Vergehen:");
            lore.add(ChatColor.WHITE + "  " + calc.previousOffenses + "x " + type.getDisplayName());
            lore.add("");

            if (calc.previousOffenses > 0) {
                lore.add(ChatColor.RED + "⚠ Wiederholungstäter!");
                lore.add(ChatColor.GRAY + "Mindestdauer: " + ChatColor.YELLOW + formatDuration(calc.minimumDuration));
            } else {
                lore.add(ChatColor.GREEN + "✔ Erster Verstoß");
                lore.add(ChatColor.GRAY + "Mindestdauer: " + ChatColor.YELLOW + formatDuration(calc.minimumDuration));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createRecommendedDurationItem(PunishmentCalculation calc) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + ChatColor.BOLD.toString() + "EMPFOHLENE DAUER");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + formatDuration(calc.recommendedDuration));
            lore.add("");
            lore.add(ChatColor.GRAY + "Basierend auf:");
            lore.add(ChatColor.WHITE + "  • Anzahl Vorstrafen");
            lore.add(ChatColor.WHITE + "  • Schwere des Vergehens");
            lore.add(ChatColor.WHITE + "  • Zeitlicher Abstand");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Verwenden");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createDurationOptionItem(DurationOption option, long minimumDuration) {
        boolean canUse = option.durationMillis >= minimumDuration;

        ItemStack item = new ItemStack(canUse ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (canUse) {
                meta.setDisplayName(ChatColor.GREEN + option.displayName);
            } else {
                meta.setDisplayName(ChatColor.DARK_GRAY + ChatColor.STRIKETHROUGH.toString() + option.displayName);
            }

            List<String> lore = new ArrayList<>();
            lore.add("");

            if (canUse) {
                lore.add(ChatColor.GRAY + "Dauer: " + ChatColor.YELLOW + formatDuration(option.durationMillis));
                lore.add("");
                lore.add(ChatColor.GREEN + "➜ Klicken zum Auswählen");
            } else {
                lore.add(ChatColor.RED + "✖ Zu kurz!");
                lore.add(ChatColor.GRAY + "Mindestens: " + ChatColor.YELLOW + formatDuration(minimumDuration));
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "Aufgrund vorheriger Vergehen");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createCustomDurationItem(long minimumDuration) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + ChatColor.BOLD.toString() + "EIGENE DAUER");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Gib eine eigene Dauer ein");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Mindestdauer:");
            lore.add(ChatColor.WHITE + "  " + formatDuration(minimumDuration));
            lore.add("");
            lore.add(ChatColor.GRAY + "Format: " + ChatColor.WHITE + "7d, 12h, 30m");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Eingeben");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createExecuteItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + ChatColor.BOLD.toString() + "✔ STRAFE VOLLZIEHEN");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Führt die Strafe mit den");
            lore.add(ChatColor.GRAY + "gewählten Einstellungen aus");
            lore.add("");
            lore.add(ChatColor.GREEN + "➜ Klicken zum Bestätigen");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createHistoryRecordItem(PunishmentRecord record) {
        Material material = switch (record.type) {
            case "BAN" -> Material.BARRIER;
            case "MUTE" -> Material.REDSTONE_BLOCK;
            case "WARN" -> Material.PAPER;
            case "KICK" -> Material.IRON_BOOTS;
            default -> Material.STONE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            ChatColor color = switch (record.type) {
                case "BAN" -> ChatColor.RED;
                case "MUTE" -> ChatColor.GOLD;
                case "WARN" -> ChatColor.YELLOW;
                case "KICK" -> ChatColor.GRAY;
                default -> ChatColor.WHITE;
            };

            meta.setDisplayName(color + ChatColor.BOLD.toString() + record.type);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "Grund: " + ChatColor.WHITE + record.reason);
            lore.add(ChatColor.YELLOW + "Von: " + ChatColor.WHITE + record.punisher);
            lore.add(ChatColor.YELLOW + "Datum: " + ChatColor.WHITE + record.date);

            if (record.duration > 0) {
                lore.add(ChatColor.YELLOW + "Dauer: " + ChatColor.WHITE + formatDuration(record.duration));
            }

            if (record.active) {
                lore.add("");
                lore.add(ChatColor.GREEN + "✔ Aktiv");
            } else if (record.pardoned) {
                lore.add("");
                lore.add(ChatColor.AQUA + "⚡ Begnadigt");
            } else {
                lore.add("");
                lore.add(ChatColor.GRAY + "○ Abgelaufen");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fillEmptySlots(Inventory inv) {
        ItemStack filler = createFillerItem();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    /**
     * Prüft ob ein Item ein GUI-Item ist (keine Interaktion)
     */
    public static boolean isGUIItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Material type = item.getType();
        return type == Material.GRAY_STAINED_GLASS_PANE || type == Material.PLAYER_HEAD;
    }

    // ============================================================
    //  DATABASE METHODS
    // ============================================================

    /**
     * Lädt Gründe aus der Datenbank
     */
    private static List<PunishmentReason> loadReasonsFromDatabase(PunishmentType type) {
        List<PunishmentReason> reasons = new ArrayList<>();

        try {
            String sql = "SELECT * FROM punishment_reasons WHERE type = ? AND enabled = 1 ORDER BY sort_order";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, type.name());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                PunishmentReason reason = new PunishmentReason();
                reason.id = rs.getInt("id");
                reason.name = rs.getString("name");
                reason.description = rs.getString("description");
                reason.type = type;
                reason.defaultDuration = rs.getLong("default_duration");
                reason.severity = rs.getInt("severity");

                reasons.add(reason);
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            logger.warning("Fehler beim Laden der Gründe: " + e.getMessage());
            reasons = getDefaultReasons(type);
        }

        return reasons;
    }

    /**
     * Berechnet empfohlene Strafe basierend auf Historie
     */
    private static PunishmentCalculation calculatePunishment(OfflinePlayer target, PunishmentType type, String reason) {
        PunishmentCalculation calc = new PunishmentCalculation();
        calc.type = type;
        calc.reason = reason;

        try {
            // Zähle vorherige Vergehen des gleichen Typs
            String countSql = "SELECT COUNT(*) as count FROM " + type.getTableName() + " WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(countSql, target.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                calc.previousOffenses = rs.getInt("count");
            }

            rs.close();
            ps.close();

            // Berechne Dauer basierend auf Vorstrafen
            long baseDuration = type.getBaseDuration();

            // Erhöhe Dauer pro vorherigem Vergehen
            double multiplier = 1.0 + (calc.previousOffenses * 0.5); // +50% pro Vergehen
            calc.recommendedDuration = (long) (baseDuration * multiplier);
            calc.minimumDuration = baseDuration * (calc.previousOffenses + 1); // Mindestens 1x, 2x, 3x etc.

            // Maximal-Dauer begrenzen
            long maxDuration = type.getMaxDuration();
            if (calc.recommendedDuration > maxDuration) {
                calc.recommendedDuration = maxDuration;
            }

        } catch (Exception e) {
            logger.warning("Fehler bei Strafberechnung: " + e.getMessage());
            calc.previousOffenses = 0;
            calc.recommendedDuration = type.getBaseDuration();
            calc.minimumDuration = type.getBaseDuration();
        }

        return calc;
    }

    /**
     * Lädt Spieler-Statistiken
     */
    private static PunishmentStats loadPlayerStats(OfflinePlayer target) {
        PunishmentStats stats = new PunishmentStats();
        String uuid = target.getUniqueId().toString();

        try {
            String banSql = "SELECT COUNT(*) as count FROM bans WHERE uuid = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(banSql, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) stats.totalBans = rs.getInt("count");
            rs.close();
            ps.close();

            String muteSql = "SELECT COUNT(*) as count FROM mutes WHERE uuid = ?";
            ps = DatabaseManager.getInstance().prepareStatement(muteSql, uuid);
            rs = ps.executeQuery();
            if (rs.next()) stats.totalMutes = rs.getInt("count");
            rs.close();
            ps.close();

            String warnSql = "SELECT COUNT(*) as count FROM warns WHERE uuid = ?";
            ps = DatabaseManager.getInstance().prepareStatement(warnSql, uuid);
            rs = ps.executeQuery();
            if (rs.next()) stats.totalWarns = rs.getInt("count");
            rs.close();
            ps.close();

            String kickSql = "SELECT COUNT(*) as count FROM kicks WHERE uuid = ?";
            ps = DatabaseManager.getInstance().prepareStatement(kickSql, uuid);
            rs = ps.executeQuery();
            if (rs.next()) stats.totalKicks = rs.getInt("count");
            rs.close();
            ps.close();

        } catch (Exception e) {
            logger.warning("Fehler beim Laden der Statistiken: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Lädt Strafhistorie
     */
    private static List<PunishmentRecord> loadPunishmentHistory(OfflinePlayer target) {
        List<PunishmentRecord> history = new ArrayList<>();
        String uuid = target.getUniqueId().toString();

        try {
            loadBanHistory(uuid, history);
            loadMuteHistory(uuid, history);
            loadWarnHistory(uuid, history);
            loadKickHistory(uuid, history);

            history.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        } catch (Exception e) {
            logger.warning("Fehler beim Laden der Historie: " + e.getMessage());
        }

        return history;
    }

    private static void loadBanHistory(String uuid, List<PunishmentRecord> history) throws Exception {
        String sql = "SELECT * FROM bans WHERE uuid = ? ORDER BY created_at DESC LIMIT 20";
        PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            PunishmentRecord record = new PunishmentRecord();
            record.type = "BAN";
            record.reason = rs.getString("reason");
            record.punisher = rs.getString("banner");
            record.duration = rs.getLong("expire") - System.currentTimeMillis();
            record.pardoned = rs.getBoolean("pardon");

            try {
                record.timestamp = rs.getTimestamp("created_at").getTime();
            } catch (Exception e) {
                record.timestamp = System.currentTimeMillis();
            }

            record.date = formatDate(record.timestamp);
            record.active = !record.pardoned && rs.getLong("expire") > System.currentTimeMillis();

            history.add(record);
        }

        rs.close();
        ps.close();
    }

    private static void loadMuteHistory(String uuid, List<PunishmentRecord> history) throws Exception {
        String sql = "SELECT * FROM mutes WHERE uuid = ? ORDER BY created_at DESC LIMIT 20";
        PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            PunishmentRecord record = new PunishmentRecord();
            record.type = "MUTE";
            record.reason = rs.getString("reason");
            record.punisher = "System";
            record.duration = rs.getLong("expire") - System.currentTimeMillis();
            record.pardoned = false;

            try {
                record.timestamp = rs.getTimestamp("created_at").getTime();
            } catch (Exception e) {
                record.timestamp = System.currentTimeMillis();
            }

            record.date = formatDate(record.timestamp);
            record.active = rs.getLong("expire") > System.currentTimeMillis();

            history.add(record);
        }

        rs.close();
        ps.close();
    }

    private static void loadWarnHistory(String uuid, List<PunishmentRecord> history) throws Exception {
        String sql = "SELECT * FROM warns WHERE uuid = ? ORDER BY created_at DESC LIMIT 20";
        PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            PunishmentRecord record = new PunishmentRecord();
            record.type = "WARN";
            record.reason = rs.getString("reason");
            record.punisher = rs.getString("warner");
            record.duration = 0;
            record.pardoned = false;

            try {
                record.timestamp = rs.getTimestamp("created_at").getTime();
            } catch (Exception e) {
                record.timestamp = System.currentTimeMillis();
            }

            record.date = formatDate(record.timestamp);
            record.active = false;

            history.add(record);
        }

        rs.close();
        ps.close();
    }

    private static void loadKickHistory(String uuid, List<PunishmentRecord> history) throws Exception {
        String sql = "SELECT * FROM kicks WHERE uuid = ? ORDER BY created_at DESC LIMIT 20";
        PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, uuid);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            PunishmentRecord record = new PunishmentRecord();
            record.type = "KICK";
            record.reason = rs.getString("reason");
            record.punisher = rs.getString("kicker");
            record.duration = 0;
            record.pardoned = false;

            try {
                record.timestamp = rs.getTimestamp("created_at").getTime();
            } catch (Exception e) {
                record.timestamp = System.currentTimeMillis();
            }

            record.date = formatDate(record.timestamp);
            record.active = false;

            history.add(record);
        }

        rs.close();
        ps.close();
    }

    // ============================================================
    //  UTILITY METHODS
    // ============================================================

    /**
     * Formatiert Dauer in lesbares Format
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "Permanent";

        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.length() == 0 && secs > 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    /**
     * Formatiert Datum
     */
    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }

    /**
     * Parst Dauer-String (z.B. "7d", "12h", "30m", "7d1h30m")
     * Unterstützt kombinierte Formate wie: 7d12h, 1h30m, 2d3h15m
     */
    public static long parseDuration(String str) {
        if (str == null || str.isEmpty()) return 0;

        str = str.toLowerCase().trim();
        long total = 0;
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (number.length() > 0) {
                    try {
                        int value = Integer.parseInt(number.toString());
                        switch (c) {
                            case 'd' -> total += value * 86400000L;  // Tage
                            case 'h' -> total += value * 3600000L;   // Stunden
                            case 'm' -> total += value * 60000L;     // Minuten
                            case 's' -> total += value * 1000L;      // Sekunden
                        }
                        number = new StringBuilder(); // Reset für nächste Zahl
                    } catch (NumberFormatException e) {
                        logger.warning("Ungültige Zahl beim Parsen der Dauer: " + number);
                        number = new StringBuilder();
                    }
                }
            } else if (c != ' ' && c != ',') {
                // Ignoriere Leerzeichen und Kommas, warnen bei anderen Zeichen
                logger.warning("Ungültiges Zeichen in Dauer-String: " + c);
            }
        }

        return total;
    }

    /**
     * Validiert einen Dauer-String
     * @return true wenn Format gültig ist
     */
    public static boolean isValidDurationFormat(String str) {
        if (str == null || str.isEmpty()) return false;

        // Erlaubte Pattern: Zahl + Buchstabe (d/h/m/s)
        // Beispiele: 7d, 12h, 7d12h, 1h30m15s
        String pattern = "^(\\d+[dhms]\\s*)+$";
        return str.toLowerCase().trim().matches(pattern);
    }

    /**
     * Gibt Standard-Gründe zurück (Fallback)
     */
    private static List<PunishmentReason> getDefaultReasons(PunishmentType type) {
        List<PunishmentReason> reasons = new ArrayList<>();

        switch (type) {
            case BAN -> {
                reasons.add(new PunishmentReason("Hacking", type, 604800000L));
                reasons.add(new PunishmentReason("Griefing", type, 259200000L));
                reasons.add(new PunishmentReason("Beleidigung", type, 432000000L));
                reasons.add(new PunishmentReason("Spam", type, 86400000L));
                reasons.add(new PunishmentReason("Werbung", type, 172800000L));
            }
            case MUTE -> {
                reasons.add(new PunishmentReason("Spam", type, 1800000L));
                reasons.add(new PunishmentReason("Beleidigung", type, 3600000L));
                reasons.add(new PunishmentReason("Caps", type, 900000L));
                reasons.add(new PunishmentReason("Werbung", type, 7200000L));
                reasons.add(new PunishmentReason("Provokation", type, 1800000L));
            }
            case KICK -> {
                reasons.add(new PunishmentReason("AFK", type, 0L));
                reasons.add(new PunishmentReason("Verhalten", type, 0L));
                reasons.add(new PunishmentReason("Namensänderung", type, 0L));
            }
            case WARN -> {
                reasons.add(new PunishmentReason("Regelverstoß", type, 0L));
                reasons.add(new PunishmentReason("Fehlverhalten", type, 0L));
                reasons.add(new PunishmentReason("Spamming", type, 0L));
            }
        }

        return reasons;
    }

    /**
     * Gibt Dauer-Optionen zurück
     */
    private static List<DurationOption> getDurationOptions(PunishmentType type, long minimumDuration) {
        List<DurationOption> options = new ArrayList<>();

        switch (type) {
            case BAN -> {
                options.add(new DurationOption("1 Tag", 86400000L));
                options.add(new DurationOption("3 Tage", 259200000L));
                options.add(new DurationOption("7 Tage", 604800000L));
                options.add(new DurationOption("14 Tage", 1209600000L));
                options.add(new DurationOption("30 Tage", 2592000000L));
                options.add(new DurationOption("Permanent", Long.MAX_VALUE));
            }
            case MUTE -> {
                options.add(new DurationOption("15 Minuten", 900000L));
                options.add(new DurationOption("30 Minuten", 1800000L));
                options.add(new DurationOption("1 Stunde", 3600000L));
                options.add(new DurationOption("3 Stunden", 10800000L));
                options.add(new DurationOption("12 Stunden", 43200000L));
                options.add(new DurationOption("24 Stunden", 86400000L));
                options.add(new DurationOption("3 Tage", 259200000L));
            }
        }

        return options;
    }

    // ============================================================
    //  INNER CLASSES
    // ============================================================

    public static class GUISession {
        public UUID viewerUUID;
        public UUID targetUUID;
        public String targetName;
        public PunishmentType currentType;
        public String selectedReason;
        public long selectedDuration = 0;
        public long minimumDuration = 0;
        public int previousOffenses = 0;
    }

    public enum PunishmentType {
        BAN("Ban", "bans", 604800000L, 2592000000L),
        MUTE("Mute", "mutes", 1800000L, 86400000L),
        KICK("Kick", "kicks", 0L, 0L),
        WARN("Warnung", "warns", 0L, 0L);

        private final String displayName;
        private final String tableName;
        private final long baseDuration;
        private final long maxDuration;

        PunishmentType(String displayName, String tableName, long baseDuration, long maxDuration) {
            this.displayName = displayName;
            this.tableName = tableName;
            this.baseDuration = baseDuration;
            this.maxDuration = maxDuration;
        }

        public String getDisplayName() { return displayName; }
        public String getTableName() { return tableName; }
        public long getBaseDuration() { return baseDuration; }
        public long getMaxDuration() { return maxDuration; }
    }

    public static class PunishmentReason {
        public int id;
        public String name;
        public String description;
        public PunishmentType type;
        public long defaultDuration;
        public int severity;
        public boolean enabled = true;
        public int sortOrder;

        public PunishmentReason() {}

        public PunishmentReason(String name, PunishmentType type, long defaultDuration) {
            this.name = name;
            this.type = type;
            this.defaultDuration = defaultDuration;
            this.severity = 2;
            this.enabled = true;
        }
    }

    public static class PunishmentCalculation {
        public PunishmentType type;
        public String reason;
        public int previousOffenses = 0;
        public long minimumDuration = 0;
        public long recommendedDuration = 0;
        public long selectedDuration = 0;

        public boolean isValidDuration(long duration) {
            return duration >= minimumDuration;
        }
    }

    public static class PunishmentStats {
        public int totalBans = 0;
        public int totalMutes = 0;
        public int totalWarns = 0;
        public int totalKicks = 0;

        public int getTotalPunishments() {
            return totalBans + totalMutes + totalWarns + totalKicks;
        }
    }

    public static class PunishmentRecord {
        public String type;
        public String reason;
        public String punisher;
        public long duration;
        public long timestamp;
        public String date;
        public boolean active = false;
        public boolean pardoned = false;
    }

    public static class DurationOption {
        public String displayName;
        public long durationMillis;

        public DurationOption(String displayName, long durationMillis) {
            this.displayName = displayName;
            this.durationMillis = durationMillis;
        }
    }
}