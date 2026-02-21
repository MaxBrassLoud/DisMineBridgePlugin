package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.util.OfflineInventoryStore;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * /invsee <inv|end> <Spieler>
 *
 * ╔══════════════════════════════════╗
 * ║  VIEWER-LAYOUT (54 Slots / 6x9) ║
 * ╠══════════════════════════════════╣
 * ║  0  1  2  3  4  5  6  7  8     ║  <- Hotbar
 * ║  9 10 11 12 13 14 15 16 17     ║  <- Hauptinventar 1
 * ║ 18 19 20 21 22 23 24 25 26     ║  <- Hauptinventar 2
 * ║ 27 28 29 30 31 32 33 34 35     ║  <- Hauptinventar 3
 * ║ 36 37 38 39 40 [41...44]       ║  <- Rüstung + Offhand + Deko-Separator
 * ║ [45 ... 53]                    ║  <- Info-Zeile (Slot-Labels)
 * ╚══════════════════════════════════╝
 *
 * Slot 36 = Boots       (Label: Stiefel)
 * Slot 37 = Leggings    (Label: Hose)
 * Slot 38 = Chestplate  (Label: Brustpanzer)
 * Slot 39 = Helmet      (Label: Helm)
 * Slot 40 = Offhand     (Label: Nebenhand)
 * Slots 41-44 = grau (Trenner)
 * Slots 45-49 = Label-Slots (Rüstungsslot-Namen)
 * Slots 50-52 = schwarz (Füller)
 * Slot  53    = Spielerkopf-Info (Name, HP, Hunger, Coords, Dimension – live)
 */
public class InvSeeCommand implements CommandExecutor, TabCompleter, Listener {

    // ─── Größen ──────────────────────────────────────────────────────────
    private static final int INV_VIEWER_SIZE = 54;
    private static final int END_VIEWER_SIZE = 27;

    // ─── Slot-Positionen ─────────────────────────────────────────────────
    private static final int SLOT_BOOTS      = 36;
    private static final int SLOT_LEGGINGS   = 37;
    private static final int SLOT_CHESTPLATE = 38;
    private static final int SLOT_HELMET     = 39;
    private static final int SLOT_OFFHAND    = 40;

    // Slots die NICHT bearbeitbar sind (Deko + Label + Spielerkopf)
    private static final Set<Integer> LOCKED_SLOTS = new HashSet<>(
            Arrays.asList(41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53)
    );

    // Label-Slot-Positionen (Reihe 6) für Rüstungs-Slots
    private static final int LABEL_BOOTS      = 45;
    private static final int LABEL_LEGGINGS   = 46;
    private static final int LABEL_CHESTPLATE = 47;
    private static final int LABEL_HELMET     = 48;
    private static final int LABEL_OFFHAND    = 49;
    // Spielerkopf-Info (Reihe 6, letzter Slot)
    private static final int SLOT_HEAD_INFO   = 53;

    // ─── Dekorations-Items ───────────────────────────────────────────────
    // Grauer Trenner / stummer Füller – werden im Konstruktor initialisiert
    private ItemStack SEPARATOR;
    private ItemStack FILLER;

    // ─── Felder ───────────────────────────────────────────────────────────
    private final DisMineBridge plugin;
    private final LanguageManager lang;
    private final Map<UUID, InvSession> sessions        = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>>  targetToViewers = new ConcurrentHashMap<>();
    private BukkitTask syncTask;

    // ─────────────────────────────────────────────────────────────────────

    public InvSeeCommand(DisMineBridge plugin) {
        this.plugin    = plugin;
        this.lang      = plugin.getLanguageManager();
        this.SEPARATOR = makeGlass(Material.GRAY_STAINED_GLASS_PANE,  ChatColor.DARK_GRAY + "▪");
        this.FILLER    = makeGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSync, 4L, 4L);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Command
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(lang.getMessage("minecraft.invsee.player-only"));
            return true;
        }
        if (!hasPerm(viewer, "dmb.invsee")) {
            viewer.sendMessage(lang.getMessage("minecraft.invsee.no-permission"));
            return true;
        }
        if (args.length < 2) { sendUsage(viewer); return true; }

        String mode   = args[0].toLowerCase();
        String target = args[1];

        if (!mode.equals("inv") && !mode.equals("end")) { sendUsage(viewer); return true; }

        boolean isEnd = mode.equals("end");
        if (isEnd && !hasPerm(viewer, "dmb.invsee.end")) {
            viewer.sendMessage(lang.getMessage("minecraft.invsee.no-permission-end"));
            return true;
        }

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            openSession(viewer, online.getUniqueId(), online.getName(), isEnd, true);
        } else {
            if (!hasPerm(viewer, "dmb.invsee.offline")) {
                viewer.sendMessage(lang.getMessage("minecraft.invsee.no-permission-offline"));
                return true;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(target);
            if (!op.hasPlayedBefore()) {
                viewer.sendMessage(lang.getMessage("minecraft.invsee.player-not-found")
                        .replace("{player}", target));
                return true;
            }
            String name = op.getName() != null ? op.getName() : target;
            openSession(viewer, op.getUniqueId(), name, isEnd, false);
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Session öffnen
    // ══════════════════════════════════════════════════════════════════════

    private void openSession(Player viewer, UUID targetUuid, String targetName,
                             boolean isEnd, boolean isOnline) {
        terminateSession(viewer.getUniqueId(), false);

        ItemStack[] contents = isOnline
                ? fetchOnline(targetUuid, isEnd)
                : fetchOffline(targetUuid, isEnd);

        String titleKey = isOnline
                ? (isEnd ? "minecraft.invsee.title-end"         : "minecraft.invsee.title-inv")
                : (isEnd ? "minecraft.invsee.title-end-offline" : "minecraft.invsee.title-inv-offline");
        String title = ChatColor.translateAlternateColorCodes('&',
                lang.getMessage(titleKey).replace("{player}", targetName));

        int size = isEnd ? END_VIEWER_SIZE : INV_VIEWER_SIZE;
        Inventory inv = Bukkit.createInventory(null, size, title);

        if (contents != null) {
            for (int i = 0; i < Math.min(contents.length, size); i++) inv.setItem(i, contents[i]);
        }

        if (!isEnd) buildDecoRow(inv, targetUuid, targetName, isOnline);

        InvSession session = new InvSession(viewer.getUniqueId(), targetUuid,
                targetName, inv, isEnd, isOnline);
        sessions.put(viewer.getUniqueId(), session);

        if (isOnline) targetToViewers.computeIfAbsent(targetUuid,
                k -> ConcurrentHashMap.newKeySet()).add(viewer.getUniqueId());

        viewer.openInventory(inv);

        String msgKey = isOnline ? "minecraft.invsee.opened" : "minecraft.invsee.opened-offline";
        viewer.sendMessage(lang.getMessage(msgKey)
                .replace("{player}", targetName)
                .replace("{mode}", isEnd ? "Enderchest" : "Inventar"));

        plugin.getLogger().info("[InvSee] " + viewer.getName() + " oeffnet " +
                (isOnline ? "" : "OFFLINE ") + targetName + "'s " +
                (isEnd ? "Enderchest" : "Inventar"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Deko-Zeile aufbauen (Reihe 5 & 6)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Baut die untere UI-Zeile des Viewer-Inventars:
     *
     *  Reihe 5:  [Boots][Legs][Chest][Helm][Offhand] [TRENN×4]
     *  Reihe 6:  [LBoot][LLeg][LChe][LHel][LOffi] [FILL×3] [KOPF]
     *
     * Leere Rüstungsslots bekommen einen Platzhalter – wird beim Ablegen
     * eines echten Items automatisch gelöscht.
     */
    private void buildDecoRow(Inventory inv, UUID targetUuid, String targetName, boolean isOnline) {
        // Reihe 5, Slots 41–44: grauer Trenner
        for (int s = 41; s <= 44; s++) inv.setItem(s, SEPARATOR);

        // Reihe 6, Slots 45–49: Label-Items
        inv.setItem(LABEL_BOOTS,      makeLabelItem(Material.LEATHER_BOOTS,
                c("invsee.label-boots-name"),      c("invsee.label-boots-lore")));
        inv.setItem(LABEL_LEGGINGS,   makeLabelItem(Material.LEATHER_LEGGINGS,
                c("invsee.label-leggings-name"),   c("invsee.label-leggings-lore")));
        inv.setItem(LABEL_CHESTPLATE, makeLabelItem(Material.LEATHER_CHESTPLATE,
                c("invsee.label-chestplate-name"), c("invsee.label-chestplate-lore")));
        inv.setItem(LABEL_HELMET,     makeLabelItem(Material.LEATHER_HELMET,
                c("invsee.label-helmet-name"),     c("invsee.label-helmet-lore")));
        inv.setItem(LABEL_OFFHAND,    makeLabelItem(Material.SHIELD,
                c("invsee.label-offhand-name"),    c("invsee.label-offhand-lore")));

        // Reihe 6, Slots 50–52: schwarzer Füller
        for (int s = 50; s <= 52; s++) inv.setItem(s, FILLER);

        // Reihe 6, Slot 53: Spielerkopf
        inv.setItem(SLOT_HEAD_INFO, buildHeadItem(targetUuid, targetName, isOnline));

        // Leere Rüstungsslots mit Platzhalter füllen
        fillArmorPlaceholder(inv, SLOT_BOOTS,      Material.LEATHER_BOOTS,
                c("invsee.placeholder-boots"));
        fillArmorPlaceholder(inv, SLOT_LEGGINGS,   Material.LEATHER_LEGGINGS,
                c("invsee.placeholder-leggings"));
        fillArmorPlaceholder(inv, SLOT_CHESTPLATE, Material.LEATHER_CHESTPLATE,
                c("invsee.placeholder-chestplate"));
        fillArmorPlaceholder(inv, SLOT_HELMET,     Material.LEATHER_HELMET,
                c("invsee.placeholder-helmet"));
        fillArmorPlaceholder(inv, SLOT_OFFHAND,    Material.SHIELD,
                c("invsee.placeholder-offhand"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Spielerkopf-Info-Item
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Erstellt das Spielerkopf-Item mit Echtzeit-Infos.
     *
     *  Online  → ❤ HP-Balken, 🍗 Hunger-Balken, 📍 X/Y/Z, 🌍 Dimension, Spielmodus
     *  Offline → Name + Offline-Hinweis
     */
    private ItemStack buildHeadItem(UUID targetUuid, String targetName, boolean isOnline) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));

        List<String> lore = new ArrayList<>();
        Player t = Bukkit.getPlayer(targetUuid);

        if (isOnline && t != null) {
            meta.setDisplayName(c("invsee.head-online-status", "{player}", targetName));

            // ── Herzen ────────────────────────────────────────────────────
            double hp    = t.getHealth();
            double maxHp = t.getMaxHealth();
            int filled   = (int) Math.ceil(hp / 2.0);
            int total    = (int) (maxHp / 2.0);
            StringBuilder hearts = new StringBuilder();
            for (int i = 0; i < total; i++)
                hearts.append(i < filled ? ChatColor.RED + "❤" : ChatColor.DARK_GRAY + "❤");
            lore.add(c("invsee.head-hearts-label",
                    "{hp}", String.valueOf((int) hp), "{maxhp}", String.valueOf((int) maxHp)));
            lore.add("  " + hearts);

            // ── Hunger ────────────────────────────────────────────────────
            int food = t.getFoodLevel();
            StringBuilder hunger = new StringBuilder();
            for (int i = 0; i < 10; i++)
                hunger.append(i < food / 2 ? ChatColor.GOLD + "🍗" : ChatColor.DARK_GRAY + "🍗");
            lore.add(c("invsee.head-hunger-label", "{food}", String.valueOf(food)));
            lore.add("  " + hunger);

            // ── Position ──────────────────────────────────────────────────
            lore.add("");
            Location loc = t.getLocation();
            lore.add(c("invsee.head-pos-title"));
            lore.add(c("invsee.head-pos-coords",
                    "{x}", String.valueOf((int) loc.getX()),
                    "{y}", String.valueOf((int) loc.getY()),
                    "{z}", String.valueOf((int) loc.getZ())));

            // ── Dimension ─────────────────────────────────────────────────
            String dim = switch (t.getWorld().getEnvironment()) {
                case NETHER  -> c("invsee.dim-nether");
                case THE_END -> c("invsee.dim-end");
                default      -> c("invsee.dim-overworld");
            };
            lore.add(c("invsee.head-dim-label", "{dim}", dim));

            // ── Spielmodus ────────────────────────────────────────────────
            String gm = switch (t.getGameMode()) {
                case CREATIVE   -> c("invsee.gm-creative");
                case ADVENTURE  -> c("invsee.gm-adventure");
                case SPECTATOR  -> c("invsee.gm-spectator");
                default         -> c("invsee.gm-survival");
            };
            lore.add(c("invsee.head-gm-label", "{gm}", gm));
            lore.add("");
            lore.add(c("invsee.head-live-hint"));

        } else {
            // ── Offline: gespeicherte Daten aus JSON laden ────────────────
            OfflineInventoryStore store = plugin.getInventoryStoreManager().storeFor(targetUuid);
            OfflineInventoryStore.PlayerSnapshot snap = store.getSnapshot();

            meta.setDisplayName(c("invsee.head-offline-status", "{player}", targetName));

            if (snap != null) {
                // ── Herzen ────────────────────────────────────────────────
                int curHp  = (int) snap.health;
                int maxHp  = (int) snap.maxHealth;
                int filled = (int) Math.ceil(snap.health / 2.0);
                int total  = (int) (snap.maxHealth / 2.0);
                StringBuilder hearts = new StringBuilder();
                for (int i = 0; i < total; i++)
                    hearts.append(i < filled ? ChatColor.RED + "❤" : ChatColor.DARK_GRAY + "❤");
                lore.add(c("invsee.head-hearts-label",
                        "{hp}", String.valueOf(curHp), "{maxhp}", String.valueOf(maxHp)));
                lore.add("  " + hearts);

                // ── Hunger ────────────────────────────────────────────────
                StringBuilder hunger = new StringBuilder();
                for (int i = 0; i < 10; i++)
                    hunger.append(i < snap.foodLevel / 2
                            ? ChatColor.GOLD + "🍗" : ChatColor.DARK_GRAY + "🍗");
                lore.add(c("invsee.head-hunger-label",
                        "{food}", String.valueOf(snap.foodLevel)));
                lore.add("  " + hunger);

                // ── Position ──────────────────────────────────────────────
                lore.add("");
                lore.add(c("invsee.head-offline-pos"));
                lore.add(c("invsee.head-pos-coords",
                        "{x}", String.valueOf((int) snap.x),
                        "{y}", String.valueOf((int) snap.y),
                        "{z}", String.valueOf((int) snap.z)));

                // ── Dimension ─────────────────────────────────────────────
                String dim = switch (snap.environment) {
                    case "NETHER"  -> c("invsee.dim-nether");
                    case "THE_END" -> c("invsee.dim-end");
                    default        -> c("invsee.dim-overworld");
                };
                lore.add(c("invsee.head-dim-label", "{dim}", dim));

                // ── Spielmodus ────────────────────────────────────────────
                String gm = switch (snap.gameMode) {
                    case "CREATIVE"  -> c("invsee.gm-creative");
                    case "ADVENTURE" -> c("invsee.gm-adventure");
                    case "SPECTATOR" -> c("invsee.gm-spectator");
                    default          -> c("invsee.gm-survival");
                };
                lore.add(c("invsee.head-gm-label", "{gm}", gm));

                // ── XP ────────────────────────────────────────────────────
                if (snap.xpLevel > 0 || snap.xpProgress > 0) {
                    lore.add(c("invsee.head-offline-xp",
                            "{level}", String.valueOf(snap.xpLevel),
                            "{xp}",   String.valueOf(Math.round(snap.xpProgress * 100))));
                }

                // ── Timestamp ─────────────────────────────────────────────
                lore.add("");
                if (snap.snapshotAt > 0) {
                    java.time.Instant inst = java.time.Instant.ofEpochMilli(snap.snapshotAt);
                    java.time.ZonedDateTime zdt = inst.atZone(java.time.ZoneId.systemDefault());
                    String ts = String.format("%02d.%02d.%d %02d:%02d",
                            zdt.getDayOfMonth(), zdt.getMonthValue(), zdt.getYear(),
                            zdt.getHour(), zdt.getMinute());
                    lore.add(c("invsee.head-offline-date", "{date}", ts));
                }
                lore.add(c("invsee.head-offline-pending"));

            } else {
                // Noch kein Snapshot vorhanden
                lore.add("");
                lore.add(c("invsee.head-no-snapshot-1"));
                lore.add(c("invsee.head-no-snapshot-2"));
                lore.add("");
                lore.add(c("invsee.head-no-snapshot-3"));
            }
        }

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    /** Aktualisiert nur den Spielerkopf (Slot 53) im Viewer-Inventar. */
    private void refreshHeadItem(InvSession session) {
        if (session.isEnd) return;
        session.viewerInventory.setItem(SLOT_HEAD_INFO,
                buildHeadItem(session.targetUuid, session.targetName, session.isOnline));
    }

    /** Gibt true zurück wenn das Item ein Platzhalter (Rüstungsslot leer) ist. */
    private boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta m = item.getItemMeta();
        // Platzhalter erkennen anhand des Präfix-Zeichens 〔 (kommt von placeholder-* Keys)
        return m != null && m.getDisplayName().contains("〔");
    }

    /** Setzt Platzhalter-Item nur wenn der Slot leer ist. */
    private void fillArmorPlaceholder(Inventory inv, int slot, Material mat, String name) {
        if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
            ItemStack ph = new ItemStack(mat);
            ItemMeta  m  = ph.getItemMeta();
            if (m != null) {
                m.setDisplayName(name);
                m.setLore(List.of(
                        c("invsee.placeholder-lore-1"),
                        c("invsee.placeholder-lore-2")
                ));
                ph.setItemMeta(m);
            }
            inv.setItem(slot, ph);
        }
    }

    /**
     * Refresh der Armor-Platzhalter nach jeder Änderung:
     * Wenn ein echter Item-Slot belegt wurde, soll kein Platzhalter mehr drüber sein.
     * Wenn er leer wurde, soll der Platzhalter wieder erscheinen.
     */
    private void refreshArmorPlaceholders(InvSession session) {
        if (session.isEnd) return;
        Inventory inv = session.viewerInventory;
        refreshSlotPlaceholder(inv, SLOT_BOOTS,      Material.LEATHER_BOOTS,
                c("invsee.placeholder-boots"));
        refreshSlotPlaceholder(inv, SLOT_LEGGINGS,   Material.LEATHER_LEGGINGS,
                c("invsee.placeholder-leggings"));
        refreshSlotPlaceholder(inv, SLOT_CHESTPLATE, Material.LEATHER_CHESTPLATE,
                c("invsee.placeholder-chestplate"));
        refreshSlotPlaceholder(inv, SLOT_HELMET,     Material.LEATHER_HELMET,
                c("invsee.placeholder-helmet"));
        refreshSlotPlaceholder(inv, SLOT_OFFHAND,    Material.SHIELD,
                c("invsee.placeholder-offhand"));
    }

    private void refreshSlotPlaceholder(Inventory inv, int slot, Material mat, String name) {
        ItemStack cur = inv.getItem(slot);
        if (cur == null || cur.getType() == Material.AIR || isPlaceholder(cur)) {
            ItemStack ph = new ItemStack(mat);
            ItemMeta  m  = ph.getItemMeta();
            if (m != null) {
                m.setDisplayName(name);
                m.setLore(List.of(
                        c("invsee.placeholder-lore-1"),
                        c("invsee.placeholder-lore-2")
                ));
                ph.setItemMeta(m);
            }
            inv.setItem(slot, ph);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Inhalte laden
    // ══════════════════════════════════════════════════════════════════════

    private ItemStack[] fetchOnline(UUID targetUuid, boolean isEnd) {
        Player t = Bukkit.getPlayer(targetUuid);
        if (t == null) return null;
        return isEnd ? t.getEnderChest().getContents().clone()
                : buildViewerContents(t.getInventory());
    }

    /**
     * Liest das Offline-Inventar aus <world>/playerdata/<UUID>.dat via NMS.
     */
    /**
     * Liest das Offline-Inventar aus der JSON (plugins/DisMineBridge/players/<UUID>.json).
     * Kein NMS, kein Reflection – reines Base64/Bukkit-Serialisierung.
     */
    private ItemStack[] fetchOffline(UUID targetUuid, boolean isEnd) {
        OfflineInventoryStore store = plugin.getInventoryStoreManager().storeFor(targetUuid);

        if (!store.exists()) {
            // Keine JSON vorhanden – Spieler hat noch nie gejoint oder
            // InventoryStoreManager hat noch kein Quit-Snapshot erstellt.
            plugin.getLogger().warning("[InvSee] Kein JSON-Snapshot fuer " + targetUuid
                    + " – Spieler muss zuvor mindestens einmal offline gegangen sein.");
            return new ItemStack[isEnd ? END_VIEWER_SIZE : INV_VIEWER_SIZE];
        }

        return isEnd ? store.readEnderChest() : store.readInventory();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Viewer ↔ PlayerInventory
    // ══════════════════════════════════════════════════════════════════════

    private ItemStack[] buildViewerContents(PlayerInventory pi) {
        ItemStack[] r = new ItemStack[INV_VIEWER_SIZE];
        for (int i = 0; i <= 35; i++) r[i] = pi.getItem(i);
        ItemStack[] armor = pi.getArmorContents();
        r[SLOT_BOOTS]      = armor.length > 0 ? armor[0] : null;
        r[SLOT_LEGGINGS]   = armor.length > 1 ? armor[1] : null;
        r[SLOT_CHESTPLATE] = armor.length > 2 ? armor[2] : null;
        r[SLOT_HELMET]     = armor.length > 3 ? armor[3] : null;
        r[SLOT_OFFHAND]    = pi.getItemInOffHand();
        return r;
    }

    private void applyViewerToInventory(Inventory vi, PlayerInventory pi) {
        for (int i = 0; i <= 35; i++) pi.setItem(i, vi.getItem(i));
        pi.setArmorContents(new ItemStack[]{
                realItem(vi.getItem(SLOT_BOOTS),      Material.LEATHER_BOOTS),
                realItem(vi.getItem(SLOT_LEGGINGS),   Material.LEATHER_LEGGINGS),
                realItem(vi.getItem(SLOT_CHESTPLATE), Material.LEATHER_CHESTPLATE),
                realItem(vi.getItem(SLOT_HELMET),     Material.LEATHER_HELMET)
        });
        pi.setItemInOffHand(realItem(vi.getItem(SLOT_OFFHAND), Material.SHIELD));
    }

    /**
     * Gibt null zurück wenn das Item ein Platzhalter ist
     * (damit kein Leder-/Schild-Item ins echte Inventar gespeichert wird).
     */
    private ItemStack realItem(ItemStack item, Material placeholder) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (isPlaceholder(item)) return null;
        return item;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Änderungen pushen
    // ══════════════════════════════════════════════════════════════════════

    private void pushToTarget(InvSession session) {
        if (session.isOnline) pushToOnline(session);
        else                  pushToOffline(session);
    }

    private void pushToOnline(InvSession session) {
        Player t = Bukkit.getPlayer(session.targetUuid);
        if (t == null) return;
        Inventory vi = session.viewerInventory;
        if (session.isEnd) {
            Inventory ec = t.getEnderChest();
            for (int i = 0; i < Math.min(vi.getSize(), ec.getSize()); i++) ec.setItem(i, vi.getItem(i));
        } else {
            applyViewerToInventory(vi, t.getInventory());
        }
        t.updateInventory();
    }

    /**
     * Schreibt Änderungen in die JSON-Datei des Offline-Spielers.
     * Setzt pendingChanges=true → beim nächsten Join wird das Inventar
     * automatisch angewendet (via InventoryStoreManager.onPlayerJoin).
     */
    private void pushToOffline(InvSession session) {
        OfflineInventoryStore store = plugin.getInventoryStoreManager()
                .storeFor(session.targetUuid);

        boolean ok;
        if (session.isEnd) {
            ItemStack[] c = new ItemStack[END_VIEWER_SIZE];
            for (int i = 0; i < END_VIEWER_SIZE; i++) c[i] = session.viewerInventory.getItem(i);
            ok = store.writeEnderChest(c);
        } else {
            ItemStack[] c = new ItemStack[INV_VIEWER_SIZE];
            for (int i = 0; i < INV_VIEWER_SIZE; i++)
                if (!LOCKED_SLOTS.contains(i)) c[i] = session.viewerInventory.getItem(i);
            // Platzhalter aus Rüstungsslots bereinigen
            c[SLOT_BOOTS]      = realItem(c[SLOT_BOOTS],      Material.LEATHER_BOOTS);
            c[SLOT_LEGGINGS]   = realItem(c[SLOT_LEGGINGS],   Material.LEATHER_LEGGINGS);
            c[SLOT_CHESTPLATE] = realItem(c[SLOT_CHESTPLATE], Material.LEATHER_CHESTPLATE);
            c[SLOT_HELMET]     = realItem(c[SLOT_HELMET],     Material.LEATHER_HELMET);
            c[SLOT_OFFHAND]    = realItem(c[SLOT_OFFHAND],    Material.SHIELD);
            ok = store.writeInventory(c);
        }

        if (ok) {
            plugin.getLogger().info("[InvSee] Offline-Snapshot fuer " + session.targetName
                    + " gespeichert. Aenderungen werden beim naechsten Login angewendet.");
        } else {
            plugin.getLogger().warning("[InvSee] JSON schreiben fehlgeschlagen fuer "
                    + session.targetName);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Live-Sync
    // ══════════════════════════════════════════════════════════════════════

    private void tickSync() {
        for (InvSession session : sessions.values()) {
            if (!session.isOnline) continue;

            Player viewer = Bukkit.getPlayer(session.viewerUuid);
            if (viewer == null || !viewer.isOnline()) { terminateSession(session.viewerUuid, false); continue; }
            if (!viewer.getOpenInventory().getTopInventory().equals(session.viewerInventory)) continue;

            Player target = Bukkit.getPlayer(session.targetUuid);
            if (target == null || !target.isOnline()) { terminateSession(session.viewerUuid, true); continue; }

            ItemStack[] fresh = session.isEnd
                    ? target.getEnderChest().getContents()
                    : buildViewerContents(target.getInventory());

            Inventory vi = session.viewerInventory;
            for (int i = 0; i < Math.min(fresh.length, vi.getSize()); i++) {
                if (LOCKED_SLOTS.contains(i)) continue;
                ItemStack cur  = vi.getItem(i);
                ItemStack next = fresh[i];

                // Platzhalter im Sync nicht überschreiben durch null
                if (!session.isEnd && isArmorSlot(i)) {
                    if (next == null || next.getType() == Material.AIR) continue; // Platzhalter bleibt
                }
                if (!Objects.equals(cur, next)) vi.setItem(i, next);
            }

            // Platzhalter nach Sync aktualisieren
            if (!session.isEnd) {
                refreshArmorPlaceholders(session);
                refreshHeadItem(session);
            }
        }
    }

    private boolean isArmorSlot(int slot) {
        return slot == SLOT_BOOTS || slot == SLOT_LEGGINGS
                || slot == SLOT_CHESTPLATE || slot == SLOT_HELMET || slot == SLOT_OFFHAND;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Events
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        InvSession session = sessions.get(viewer.getUniqueId());
        if (session == null) return;

        Inventory top = session.viewerInventory;
        Inventory clicked = event.getClickedInventory();

        // ── Fall A: Klick im oberen Viewer-Inventar ───────────────────────
        if (clicked != null && clicked.equals(top)) {
            int slot = event.getSlot();

            // Gesperrte Deko-Slots (Labels, Füller, Spielerkopf) → immer Cancel
            if (LOCKED_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }

            // Edit-Permission prüfen
            if (!hasPerm(viewer, "dmb.invsee.edit")) {
                event.setCancelled(true);
                viewer.sendMessage(lang.getMessage("minecraft.invsee.no-permission-edit"));
                return;
            }

            // Platzhalter-Slot (leerer Rüstungsslot):
            //   • Cursor hält echtes Item → Platzhalter löschen, Bukkit legt Item ab ✓
            //   • Cursor leer (Spieler will Platzhalter rausnehmen) → Cancel ✗
            //   • Shift-Click auf Platzhalter (= Platzhalter ins eigene Inv verschieben) → Cancel ✗
            if (isArmorSlot(slot) && isPlaceholder(top.getItem(slot))) {
                if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    // Shift-Click auf Platzhalter im Top-Inventar → unterbinden
                    event.setCancelled(true);
                    return;
                }
                ItemStack cursor = viewer.getItemOnCursor();
                if (cursor == null || cursor.getType() == Material.AIR) {
                    // Platzhalter rausnehmen → unterbinden
                    event.setCancelled(true);
                    return;
                }
                // Echtes Item ablegen → Platzhalter entfernen damit Bukkit normal arbeitet
                top.setItem(slot, null);
            }

            // Zulässige Aktion → nach 1 Tick pushen & refreshen
            Bukkit.getScheduler().runTask(plugin, () -> {
                pushToTarget(session);
                refreshArmorPlaceholders(session);
                refreshHeadItem(session);
            });
            return;
        }

        // ── Fall B: Shift-Click aus dem unteren Viewer-Inventar ───────────
        // clicked = eigenes Inventar des Admins; MOVE_TO_OTHER_INVENTORY
        // verschiebt das Item in das Top-Inventar. Bukkit sucht automatisch
        // den ersten freien Slot – dabei könnte ein Platzhalter-Slot gewählt
        // werden (Platzhalter wird verdrängt und landet im Admin-Inventar).
        // Wir lassen den Event durch, löschen aber vorher alle Platzhalter
        // aus den Armor-Slots damit Bukkit nur in echte freie Slots legt.
        // Direkt danach werden fehlende Platzhalter wieder gesetzt.
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!hasPerm(viewer, "dmb.invsee.edit")) {
                event.setCancelled(true);
                viewer.sendMessage(lang.getMessage("minecraft.invsee.no-permission-edit"));
                return;
            }
            // Platzhalter kurz entfernen damit Bukkit sie nicht als "frei" betrachtet
            // und nichts hineinschiebt (Platzhalter sind Material wie LEATHER_BOOTS etc.)
            // Eigentlich ist das hier nicht nötig weil Bukkit bei MOVE_TO_OTHER_INVENTORY
            // nur wirklich leere Slots (AIR) befüllt – aber sicherheitshalber:
            // → wir canceln und setzen das Item manuell in den richtigen Slot.
            // Das ist die sicherste Methode.
            ItemStack moving = event.getCurrentItem();
            if (moving != null && moving.getType() != Material.AIR) {
                event.setCancelled(true);
                // Manuell in ersten freien nicht-gesperrten nicht-Platzhalter-Slot legen
                for (int i = 0; i < top.getSize(); i++) {
                    if (LOCKED_SLOTS.contains(i)) continue;
                    ItemStack there = top.getItem(i);
                    if (there == null || there.getType() == Material.AIR || isPlaceholder(there)) {
                        if (!isPlaceholder(there)) { // nur wirklich leere Slots
                            top.setItem(i, moving);
                            event.setCurrentItem(null);
                            break;
                        }
                    }
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pushToTarget(session);
                    refreshArmorPlaceholders(session);
                    refreshHeadItem(session);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        InvSession session = sessions.get(viewer.getUniqueId());
        if (session == null) return;

        boolean topAffected = event.getRawSlots().stream()
                .anyMatch(s -> s < session.viewerInventory.getSize());
        if (!topAffected) return;

        for (int slot : event.getInventorySlots()) {
            if (LOCKED_SLOTS.contains(slot)) { event.setCancelled(true); return; }
        }
        if (!hasPerm(viewer, "dmb.invsee.edit")) {
            event.setCancelled(true);
            viewer.sendMessage(lang.getMessage("minecraft.invsee.no-permission-edit"));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pushToTarget(session);
            refreshArmorPlaceholders(session);
            refreshHeadItem(session);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) return;
        InvSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !event.getInventory().equals(session.viewerInventory)) return;

        pushToTarget(session);
        sessions.remove(viewer.getUniqueId());
        Set<UUID> vs = targetToViewers.get(session.targetUuid);
        if (vs != null) vs.remove(viewer.getUniqueId());

        plugin.getLogger().info("[InvSee] " + viewer.getName()
                + " hat " + session.targetName + "'s Inventar geschlossen.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitting = event.getPlayer();

        InvSession vs = sessions.remove(quitting.getUniqueId());
        if (vs != null) {
            pushToTarget(vs);
            Set<UUID> s = targetToViewers.get(vs.targetUuid);
            if (s != null) s.remove(quitting.getUniqueId());
        }

        Set<UUID> viewers = targetToViewers.remove(quitting.getUniqueId());
        if (viewers != null) {
            for (UUID viewerUuid : viewers) {
                sessions.remove(viewerUuid);
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                    viewer.sendMessage(lang.getMessage("minecraft.invsee.target-offline")
                            .replace("{player}", quitting.getName()));
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Item-Factories
    // ══════════════════════════════════════════════════════════════════════

    /** Erstellt ein Glas-Deko-Item. */
    private static ItemStack makeGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    /** Erstellt ein Label-Item mit Lore für die untere Info-Zeile. */
    private static ItemStack makeLabelItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Session-Verwaltung & Util
    // ══════════════════════════════════════════════════════════════════════

    private void terminateSession(UUID viewerUuid, boolean notify) {
        InvSession session = sessions.remove(viewerUuid);
        if (session == null) return;
        Set<UUID> vs = targetToViewers.get(session.targetUuid);
        if (vs != null) vs.remove(viewerUuid);
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null && viewer.isOnline()) {
            viewer.closeInventory();
            if (notify) viewer.sendMessage(lang.getMessage("minecraft.invsee.target-offline")
                    .replace("{player}", session.targetName));
        }
    }

    private boolean hasPerm(Player p, String perm) {
        return p.hasPermission(perm) || p.hasPermission("dmb.admin");
    }

    private void sendUsage(Player p) {
        p.sendMessage(lang.getMessage("minecraft.invsee.usage"));
        p.sendMessage(lang.getMessage("minecraft.invsee.example-inv"));
        p.sendMessage(lang.getMessage("minecraft.invsee.example-end"));
    }

    public void shutdown() {
        if (syncTask != null) syncTask.cancel();
        for (UUID u : new HashSet<>(sessions.keySet())) terminateSession(u, false);
        sessions.clear();
        targetToViewers.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tab-Completion
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        if (args.length == 1) return Arrays.asList("inv", "end").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        return Collections.emptyList();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Innere Klasse
    // ══════════════════════════════════════════════════════════════════════

    private static class InvSession {
        final UUID viewerUuid, targetUuid;
        final String targetName;
        final Inventory viewerInventory;
        final boolean isEnd, isOnline;

        InvSession(UUID v, UUID t, String tn, Inventory vi, boolean e, boolean o) {
            viewerUuid = v; targetUuid = t; targetName = tn;
            viewerInventory = vi; isEnd = e; isOnline = o;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lang-Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Kurzform: Sprachkey holen + &-Codes auflösen.
     * Verwendung: c("invsee.label-boots-name")
     */
    private String c(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                lang.getMessage("minecraft." + key));
    }

    /**
     * Sprachkey mit einfachen String-Ersetzungen holen.
     * Verwendung: c("invsee.head-hearts-label", "{hp}", "14", "{maxhp}", "20")
     * Paare: key1, value1, key2, value2, ...
     */
    private String c(String key, String... replacements) {
        String s = lang.getMessage("minecraft." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            s = s.replace(replacements[i], replacements[i + 1]);
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}