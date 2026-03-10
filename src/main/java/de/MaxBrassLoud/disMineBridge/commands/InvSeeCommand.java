//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.util.OfflineInventoryStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

public class InvSeeCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int INV_VIEWER_SIZE = 54;
    private static final int END_VIEWER_SIZE = 27;
    private static final int SLOT_BOOTS = 36;
    private static final int SLOT_LEGGINGS = 37;
    private static final int SLOT_CHESTPLATE = 38;
    private static final int SLOT_HELMET = 39;
    private static final int SLOT_OFFHAND = 40;
    private static final Set<Integer> LOCKED_SLOTS = new HashSet(Arrays.asList(41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53));
    private static final int LABEL_BOOTS = 45;
    private static final int LABEL_LEGGINGS = 46;
    private static final int LABEL_CHESTPLATE = 47;
    private static final int LABEL_HELMET = 48;
    private static final int LABEL_OFFHAND = 49;
    private static final int SLOT_HEAD_INFO = 53;
    private static final ItemStack SEPARATOR;
    private static final ItemStack FILLER;
    private final DisMineBridge plugin;
    private final LanguageManager lang;
    private final Map<UUID, InvSession> sessions = new ConcurrentHashMap();
    private final Map<UUID, Set<UUID>> targetToViewers = new ConcurrentHashMap();
    private BukkitTask syncTask;

    public InvSeeCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSync, 4L, 4L);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player viewer) {
            if (!this.hasPerm(viewer, "dmb.invsee")) {
                viewer.sendMessage(this.lang.getMessage("minecraft.invsee.no-permission"));
                return true;
            } else if (args.length < 2) {
                this.sendUsage(viewer);
                return true;
            } else {
                String mode = args[0].toLowerCase();
                String target = args[1];
                if (!mode.equals("inv") && !mode.equals("end")) {
                    this.sendUsage(viewer);
                    return true;
                } else {
                    boolean isEnd = mode.equals("end");
                    if (isEnd && !this.hasPerm(viewer, "dmb.invsee.end")) {
                        viewer.sendMessage(this.lang.getMessage("minecraft.invsee.no-permission-end"));
                        return true;
                    } else {
                        Player online = Bukkit.getPlayer(target);
                        if (online != null) {
                            this.openSession(viewer, online.getUniqueId(), online.getName(), isEnd, true);
                        } else {
                            if (!this.hasPerm(viewer, "dmb.invsee.offline")) {
                                viewer.sendMessage(this.lang.getMessage("minecraft.invsee.no-permission-offline"));
                                return true;
                            }

                            OfflinePlayer op = Bukkit.getOfflinePlayer(target);
                            if (!op.hasPlayedBefore()) {
                                viewer.sendMessage(this.lang.getMessage("minecraft.invsee.player-not-found").replace("{player}", target));
                                return true;
                            }

                            String name = op.getName() != null ? op.getName() : target;
                            this.openSession(viewer, op.getUniqueId(), name, isEnd, false);
                        }

                        return true;
                    }
                }
            }
        } else {
            sender.sendMessage(this.lang.getMessage("minecraft.invsee.player-only"));
            return true;
        }
    }

    private void openSession(Player viewer, UUID targetUuid, String targetName, boolean isEnd, boolean isOnline) {
        this.terminateSession(viewer.getUniqueId(), false);
        ItemStack[] contents = isOnline ? this.fetchOnline(targetUuid, isEnd) : this.fetchOffline(targetUuid, isEnd);
        String titleKey = isOnline ? (isEnd ? "minecraft.invsee.title-end" : "minecraft.invsee.title-inv") : (isEnd ? "minecraft.invsee.title-end-offline" : "minecraft.invsee.title-inv-offline");
        String title = ChatColor.translateAlternateColorCodes('&', this.lang.getMessage(titleKey).replace("{player}", targetName));
        int size = isEnd ? 27 : 54;
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, size, title);
        if (contents != null) {
            for(int i = 0; i < Math.min(contents.length, size); ++i) {
                inv.setItem(i, contents[i]);
            }
        }

        if (!isEnd) {
            this.buildDecoRow(inv, targetUuid, targetName, isOnline);
        }

        InvSession session = new InvSession(viewer.getUniqueId(), targetUuid, targetName, inv, isEnd, isOnline);
        this.sessions.put(viewer.getUniqueId(), session);
        if (isOnline) {
            ((Set)this.targetToViewers.computeIfAbsent(targetUuid, (k) -> ConcurrentHashMap.newKeySet())).add(viewer.getUniqueId());
        }

        viewer.openInventory(inv);
        String msgKey = isOnline ? "minecraft.invsee.opened" : "minecraft.invsee.opened-offline";
        viewer.sendMessage(this.lang.getMessage(msgKey).replace("{player}", targetName).replace("{mode}", isEnd ? "Enderchest" : "Inventar"));
        Logger var10000 = this.plugin.getLogger();
        String var10001 = viewer.getName();
        var10000.info("[InvSee] " + var10001 + " oeffnet " + (isOnline ? "" : "OFFLINE ") + targetName + "'s " + (isEnd ? "Enderchest" : "Inventar"));
    }

    private void buildDecoRow(Inventory inv, UUID targetUuid, String targetName, boolean isOnline) {
        for(int s = 41; s <= 44; ++s) {
            inv.setItem(s, SEPARATOR);
        }

        inv.setItem(45, makeLabelItem(Material.LEATHER_BOOTS, String.valueOf(ChatColor.YELLOW) + "✦ Stiefel", String.valueOf(ChatColor.GRAY) + "Slot 36 – Rüstung"));
        inv.setItem(46, makeLabelItem(Material.LEATHER_LEGGINGS, String.valueOf(ChatColor.YELLOW) + "✦ Hose", String.valueOf(ChatColor.GRAY) + "Slot 37 – Rüstung"));
        inv.setItem(47, makeLabelItem(Material.LEATHER_CHESTPLATE, String.valueOf(ChatColor.YELLOW) + "✦ Brustpanzer", String.valueOf(ChatColor.GRAY) + "Slot 38 – Rüstung"));
        inv.setItem(48, makeLabelItem(Material.LEATHER_HELMET, String.valueOf(ChatColor.YELLOW) + "✦ Helm", String.valueOf(ChatColor.GRAY) + "Slot 39 – Rüstung"));
        inv.setItem(49, makeLabelItem(Material.SHIELD, String.valueOf(ChatColor.AQUA) + "✦ Nebenhand", String.valueOf(ChatColor.GRAY) + "Slot 40 – Offhand"));

        for(int s = 50; s <= 52; ++s) {
            inv.setItem(s, FILLER);
        }

        inv.setItem(53, this.buildHeadItem(targetUuid, targetName, isOnline));
        this.fillArmorPlaceholder(inv, 36, Material.LEATHER_BOOTS, String.valueOf(ChatColor.DARK_GRAY) + "〔 Stiefel – leer 〕");
        this.fillArmorPlaceholder(inv, 37, Material.LEATHER_LEGGINGS, String.valueOf(ChatColor.DARK_GRAY) + "〔 Hose – leer 〕");
        this.fillArmorPlaceholder(inv, 38, Material.LEATHER_CHESTPLATE, String.valueOf(ChatColor.DARK_GRAY) + "〔 Brustpanzer – leer 〕");
        this.fillArmorPlaceholder(inv, 39, Material.LEATHER_HELMET, String.valueOf(ChatColor.DARK_GRAY) + "〔 Helm – leer 〕");
        this.fillArmorPlaceholder(inv, 40, Material.SHIELD, String.valueOf(ChatColor.DARK_GRAY) + "〔 Nebenhand – leer 〕");
    }

    private ItemStack buildHeadItem(UUID targetUuid, String targetName, boolean isOnline) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta)skull.getItemMeta();
        if (meta == null) {
            return skull;
        } else {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
            List<String> lore = new ArrayList();
            Player t = Bukkit.getPlayer(targetUuid);
            if (isOnline && t != null) {
                String var49 = String.valueOf(ChatColor.GOLD);
                meta.setDisplayName(var49 + String.valueOf(ChatColor.BOLD) + targetName + String.valueOf(ChatColor.GREEN) + " ● Online");
                double hp = t.getHealth();
                double maxHp = t.getMaxHealth();
                int filled = (int)Math.ceil(hp / (double)2.0F);
                int total = (int)(maxHp / (double)2.0F);
                StringBuilder hearts = new StringBuilder();

                for(int i = 0; i < total; ++i) {
                    hearts.append(i < filled ? String.valueOf(ChatColor.RED) + "❤" : String.valueOf(ChatColor.DARK_GRAY) + "❤");
                }

                var49 = String.valueOf(ChatColor.RED);
                lore.add(var49 + "❤ Herzen:  " + String.valueOf(ChatColor.WHITE) + (int)hp + " " + String.valueOf(ChatColor.DARK_GRAY) + "/ " + (int)maxHp);
                lore.add("  " + String.valueOf(hearts));
                int food = t.getFoodLevel();
                StringBuilder hunger = new StringBuilder();

                for(int i = 0; i < 10; ++i) {
                    hunger.append(i < food / 2 ? String.valueOf(ChatColor.GOLD) + "\ud83c\udf57" : String.valueOf(ChatColor.DARK_GRAY) + "\ud83c\udf57");
                }

                var49 = String.valueOf(ChatColor.GOLD);
                lore.add(var49 + "\ud83c\udf57 Hunger:  " + String.valueOf(ChatColor.WHITE) + food + " " + String.valueOf(ChatColor.DARK_GRAY) + "/ 20");
                lore.add("  " + String.valueOf(hunger));
                lore.add("");
                Location loc = t.getLocation();
                lore.add(String.valueOf(ChatColor.AQUA) + "\ud83d\udccd Position");
                var49 = String.valueOf(ChatColor.GRAY);
                lore.add(var49 + "   X " + String.valueOf(ChatColor.WHITE) + (int)loc.getX() + String.valueOf(ChatColor.GRAY) + "  Y " + String.valueOf(ChatColor.WHITE) + (int)loc.getY() + String.valueOf(ChatColor.GRAY) + "  Z " + String.valueOf(ChatColor.WHITE) + (int)loc.getZ());
                String var40;
                switch (t.getWorld().getEnvironment()) {
                    case NORMAL -> var40 = String.valueOf(ChatColor.GREEN) + "☀ Overworld";
                    case NETHER -> var40 = String.valueOf(ChatColor.RED) + "\ud83d\udd25 Nether";
                    case THE_END -> var40 = String.valueOf(ChatColor.LIGHT_PURPLE) + "✦ The End";
                    default -> var40 = String.valueOf(ChatColor.GRAY) + "Unbekannt";
                }

                String dim = var40;
                var49 = String.valueOf(ChatColor.AQUA);
                lore.add(var49 + "\ud83c\udf0d Dimension: " + dim);
                switch (t.getGameMode()) {
                    case SURVIVAL -> var40 = String.valueOf(ChatColor.GREEN) + "Überleben";
                    case CREATIVE -> var40 = String.valueOf(ChatColor.AQUA) + "Kreativ";
                    case ADVENTURE -> var40 = String.valueOf(ChatColor.YELLOW) + "Abenteuer";
                    case SPECTATOR -> var40 = String.valueOf(ChatColor.GRAY) + "Zuschauer";
                    default -> throw new MatchException((String)null, (Throwable)null);
                }

                String gm = var40;
                var49 = String.valueOf(ChatColor.AQUA);
                lore.add(var49 + "\ud83c\udfae Spielmodus: " + gm);
                lore.add("");
                lore.add(String.valueOf(ChatColor.DARK_GRAY) + "⟳ Live-Aktualisierung aktiv");
            } else {
                OfflineInventoryStore store = this.plugin.getInventoryStoreManager().storeFor(targetUuid);
                OfflineInventoryStore.PlayerSnapshot snap = store.getSnapshot();
                String var10001 = String.valueOf(ChatColor.GRAY);
                meta.setDisplayName(var10001 + String.valueOf(ChatColor.BOLD) + targetName + String.valueOf(ChatColor.DARK_GRAY) + " ● Offline");
                if (snap != null) {
                    int curHp = (int)snap.health;
                    int maxHp = (int)snap.maxHealth;
                    int filled = (int)Math.ceil(snap.health / (double)2.0F);
                    int total = (int)(snap.maxHealth / (double)2.0F);
                    StringBuilder hearts = new StringBuilder();

                    for(int i = 0; i < total; ++i) {
                        hearts.append(i < filled ? String.valueOf(ChatColor.RED) + "❤" : String.valueOf(ChatColor.DARK_GRAY) + "❤");
                    }

                    var10001 = String.valueOf(ChatColor.RED);
                    lore.add(var10001 + "❤ Herzen:  " + String.valueOf(ChatColor.WHITE) + curHp + " " + String.valueOf(ChatColor.DARK_GRAY) + "/ " + maxHp);
                    lore.add("  " + String.valueOf(hearts));
                    StringBuilder hunger = new StringBuilder();

                    for(int i = 0; i < 10; ++i) {
                        hunger.append(i < snap.foodLevel / 2 ? String.valueOf(ChatColor.GOLD) + "\ud83c\udf57" : String.valueOf(ChatColor.DARK_GRAY) + "\ud83c\udf57");
                    }

                    var10001 = String.valueOf(ChatColor.GOLD);
                    lore.add(var10001 + "\ud83c\udf57 Hunger:  " + String.valueOf(ChatColor.WHITE) + snap.foodLevel + " " + String.valueOf(ChatColor.DARK_GRAY) + "/ 20");
                    lore.add("  " + String.valueOf(hunger));
                    lore.add("");
                    lore.add(String.valueOf(ChatColor.AQUA) + "\ud83d\udccd Letzte Position");
                    var10001 = String.valueOf(ChatColor.GRAY);
                    lore.add(var10001 + "   X " + String.valueOf(ChatColor.WHITE) + (int)snap.x + String.valueOf(ChatColor.GRAY) + "  Y " + String.valueOf(ChatColor.WHITE) + (int)snap.y + String.valueOf(ChatColor.GRAY) + "  Z " + String.valueOf(ChatColor.WHITE) + (int)snap.z);
                    String var10000;
                    switch (snap.environment) {
                        case "NETHER" -> var10000 = String.valueOf(ChatColor.RED) + "\ud83d\udd25 Nether";
                        case "THE_END" -> var10000 = String.valueOf(ChatColor.LIGHT_PURPLE) + "✦ The End";
                        default -> var10000 = String.valueOf(ChatColor.GREEN) + "☀ Overworld";
                    }

                    String dim = var10000;
                    var10001 = String.valueOf(ChatColor.AQUA);
                    lore.add(var10001 + "\ud83c\udf0d Dimension: " + dim);
                    switch (snap.gameMode) {
                        case "CREATIVE" -> var10000 = String.valueOf(ChatColor.AQUA) + "Kreativ";
                        case "ADVENTURE" -> var10000 = String.valueOf(ChatColor.YELLOW) + "Abenteuer";
                        case "SPECTATOR" -> var10000 = String.valueOf(ChatColor.GRAY) + "Zuschauer";
                        default -> var10000 = String.valueOf(ChatColor.GREEN) + "Überleben";
                    }

                    String gm = var10000;
                    var10001 = String.valueOf(ChatColor.AQUA);
                    lore.add(var10001 + "\ud83c\udfae Spielmodus: " + gm);
                    if (snap.xpLevel > 0 || snap.xpProgress > 0.0F) {
                        var10001 = String.valueOf(ChatColor.GREEN);
                        lore.add(var10001 + "✨ Level: " + String.valueOf(ChatColor.WHITE) + snap.xpLevel + String.valueOf(ChatColor.DARK_GRAY) + " (+" + Math.round(snap.xpProgress * 100.0F) + "%)");
                    }

                    lore.add("");
                    if (snap.snapshotAt > 0L) {
                        Instant inst = Instant.ofEpochMilli(snap.snapshotAt);
                        ZonedDateTime zdt = inst.atZone(ZoneId.systemDefault());
                        String ts = String.format("%02d.%02d.%d %02d:%02d", zdt.getDayOfMonth(), zdt.getMonthValue(), zdt.getYear(), zdt.getHour(), zdt.getMinute());
                        var10001 = String.valueOf(ChatColor.DARK_GRAY);
                        lore.add(var10001 + "\ud83d\udcc5 Zuletzt gesehen: " + ts);
                    }

                    lore.add(String.valueOf(ChatColor.DARK_GRAY) + "✎ Änderungen → beim Login angewendet");
                } else {
                    lore.add("");
                    lore.add(String.valueOf(ChatColor.DARK_GRAY) + "Noch kein Snapshot vorhanden.");
                    lore.add(String.valueOf(ChatColor.DARK_GRAY) + "Spieler muss mind. einmal den");
                    lore.add(String.valueOf(ChatColor.DARK_GRAY) + "Server verlassen haben.");
                    lore.add("");
                    lore.add(String.valueOf(ChatColor.DARK_GRAY) + "✎ Änderungen → beim Login angewendet");
                }
            }

            meta.setLore(lore);
            skull.setItemMeta(meta);
            return skull;
        }
    }

    private void refreshHeadItem(InvSession session) {
        if (!session.isEnd) {
            session.viewerInventory.setItem(53, this.buildHeadItem(session.targetUuid, session.targetName, session.isOnline));
        }
    }

    private boolean isPlaceholder(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta m = item.getItemMeta();
            return m != null && m.getDisplayName().startsWith(String.valueOf(ChatColor.DARK_GRAY) + "〔");
        } else {
            return false;
        }
    }

    private void fillArmorPlaceholder(Inventory inv, int slot, Material mat, String name) {
        if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
            ItemStack ph = new ItemStack(mat);
            ItemMeta m = ph.getItemMeta();
            if (m != null) {
                m.setDisplayName(name);
                m.setLore(List.of(String.valueOf(ChatColor.DARK_GRAY) + "Dieser Rüstungsslot ist leer.", String.valueOf(ChatColor.DARK_GRAY) + "Lege ein Item hier ab um es anzulegen."));
                ph.setItemMeta(m);
            }

            inv.setItem(slot, ph);
        }

    }

    private void refreshArmorPlaceholders(InvSession session) {
        if (!session.isEnd) {
            Inventory inv = session.viewerInventory;
            this.refreshSlotPlaceholder(inv, 36, Material.LEATHER_BOOTS, String.valueOf(ChatColor.DARK_GRAY) + "〔 Stiefel – leer 〕");
            this.refreshSlotPlaceholder(inv, 37, Material.LEATHER_LEGGINGS, String.valueOf(ChatColor.DARK_GRAY) + "〔 Hose – leer 〕");
            this.refreshSlotPlaceholder(inv, 38, Material.LEATHER_CHESTPLATE, String.valueOf(ChatColor.DARK_GRAY) + "〔 Brustpanzer – leer 〕");
            this.refreshSlotPlaceholder(inv, 39, Material.LEATHER_HELMET, String.valueOf(ChatColor.DARK_GRAY) + "〔 Helm – leer 〕");
            this.refreshSlotPlaceholder(inv, 40, Material.SHIELD, String.valueOf(ChatColor.DARK_GRAY) + "〔 Nebenhand – leer 〕");
        }
    }

    private void refreshSlotPlaceholder(Inventory inv, int slot, Material mat, String name) {
        ItemStack cur = inv.getItem(slot);
        if (cur == null || cur.getType() == Material.AIR || this.isPlaceholder(cur)) {
            ItemStack ph = new ItemStack(mat);
            ItemMeta m = ph.getItemMeta();
            if (m != null) {
                m.setDisplayName(name);
                m.setLore(List.of(String.valueOf(ChatColor.DARK_GRAY) + "Dieser Rüstungsslot ist leer.", String.valueOf(ChatColor.DARK_GRAY) + "Lege ein Item hier ab um es anzulegen."));
                ph.setItemMeta(m);
            }

            inv.setItem(slot, ph);
        }

    }

    private ItemStack[] fetchOnline(UUID targetUuid, boolean isEnd) {
        Player t = Bukkit.getPlayer(targetUuid);
        if (t == null) {
            return null;
        } else {
            return isEnd ? (ItemStack[])t.getEnderChest().getContents().clone() : this.buildViewerContents(t.getInventory());
        }
    }

    private ItemStack[] fetchOffline(UUID targetUuid, boolean isEnd) {
        OfflineInventoryStore store = this.plugin.getInventoryStoreManager().storeFor(targetUuid);
        if (!store.exists()) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = String.valueOf(targetUuid);
            var10000.warning("[InvSee] Kein JSON-Snapshot fuer " + var10001 + " – Spieler muss zuvor mindestens einmal offline gegangen sein.");
            return new ItemStack[isEnd ? 27 : 54];
        } else {
            return isEnd ? store.readEnderChest() : store.readInventory();
        }
    }

    private ItemStack[] buildViewerContents(PlayerInventory pi) {
        ItemStack[] r = new ItemStack[54];

        for(int i = 0; i <= 35; ++i) {
            r[i] = pi.getItem(i);
        }

        ItemStack[] armor = pi.getArmorContents();
        r[36] = armor.length > 0 ? armor[0] : null;
        r[37] = armor.length > 1 ? armor[1] : null;
        r[38] = armor.length > 2 ? armor[2] : null;
        r[39] = armor.length > 3 ? armor[3] : null;
        r[40] = pi.getItemInOffHand();
        return r;
    }

    private void applyViewerToInventory(Inventory vi, PlayerInventory pi) {
        for(int i = 0; i <= 35; ++i) {
            pi.setItem(i, vi.getItem(i));
        }

        pi.setArmorContents(new ItemStack[]{this.realItem(vi.getItem(36), Material.LEATHER_BOOTS), this.realItem(vi.getItem(37), Material.LEATHER_LEGGINGS), this.realItem(vi.getItem(38), Material.LEATHER_CHESTPLATE), this.realItem(vi.getItem(39), Material.LEATHER_HELMET)});
        pi.setItemInOffHand(this.realItem(vi.getItem(40), Material.SHIELD));
    }

    private ItemStack realItem(ItemStack item, Material placeholder) {
        if (item != null && item.getType() != Material.AIR) {
            return this.isPlaceholder(item) ? null : item;
        } else {
            return null;
        }
    }

    private void pushToTarget(InvSession session) {
        if (session.isOnline) {
            this.pushToOnline(session);
        } else {
            this.pushToOffline(session);
        }

    }

    private void pushToOnline(InvSession session) {
        Player t = Bukkit.getPlayer(session.targetUuid);
        if (t != null) {
            Inventory vi = session.viewerInventory;
            if (session.isEnd) {
                Inventory ec = t.getEnderChest();

                for(int i = 0; i < Math.min(vi.getSize(), ec.getSize()); ++i) {
                    ec.setItem(i, vi.getItem(i));
                }
            } else {
                this.applyViewerToInventory(vi, t.getInventory());
            }

            t.updateInventory();
        }
    }

    private void pushToOffline(InvSession session) {
        OfflineInventoryStore store = this.plugin.getInventoryStoreManager().storeFor(session.targetUuid);
        boolean ok;
        if (session.isEnd) {
            ItemStack[] c = new ItemStack[27];

            for(int i = 0; i < 27; ++i) {
                c[i] = session.viewerInventory.getItem(i);
            }

            ok = store.writeEnderChest(c);
        } else {
            ItemStack[] c = new ItemStack[54];

            for(int i = 0; i < 54; ++i) {
                if (!LOCKED_SLOTS.contains(i)) {
                    c[i] = session.viewerInventory.getItem(i);
                }
            }

            c[36] = this.realItem(c[36], Material.LEATHER_BOOTS);
            c[37] = this.realItem(c[37], Material.LEATHER_LEGGINGS);
            c[38] = this.realItem(c[38], Material.LEATHER_CHESTPLATE);
            c[39] = this.realItem(c[39], Material.LEATHER_HELMET);
            c[40] = this.realItem(c[40], Material.SHIELD);
            ok = store.writeInventory(c);
        }

        if (ok) {
            this.plugin.getLogger().info("[InvSee] Offline-Snapshot fuer " + session.targetName + " gespeichert. Aenderungen werden beim naechsten Login angewendet.");
        } else {
            this.plugin.getLogger().warning("[InvSee] JSON schreiben fehlgeschlagen fuer " + session.targetName);
        }

    }

    private void tickSync() {
        for(InvSession session : this.sessions.values()) {
            if (session.isOnline) {
                Player viewer = Bukkit.getPlayer(session.viewerUuid);
                if (viewer != null && viewer.isOnline()) {
                    if (viewer.getOpenInventory().getTopInventory().equals(session.viewerInventory)) {
                        Player target = Bukkit.getPlayer(session.targetUuid);
                        if (target != null && target.isOnline()) {
                            ItemStack[] fresh = session.isEnd ? target.getEnderChest().getContents() : this.buildViewerContents(target.getInventory());
                            Inventory vi = session.viewerInventory;

                            for(int i = 0; i < Math.min(fresh.length, vi.getSize()); ++i) {
                                if (!LOCKED_SLOTS.contains(i)) {
                                    ItemStack cur = vi.getItem(i);
                                    ItemStack next = fresh[i];
                                    if ((session.isEnd || !this.isArmorSlot(i) || next != null && next.getType() != Material.AIR) && !Objects.equals(cur, next)) {
                                        vi.setItem(i, next);
                                    }
                                }
                            }

                            if (!session.isEnd) {
                                this.refreshArmorPlaceholders(session);
                                this.refreshHeadItem(session);
                            }
                        } else {
                            this.terminateSession(session.viewerUuid, true);
                        }
                    }
                } else {
                    this.terminateSession(session.viewerUuid, false);
                }
            }
        }

    }

    private boolean isArmorSlot(int slot) {
        return slot == 36 || slot == 37 || slot == 38 || slot == 39 || slot == 40;
    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player viewer) {
            InvSession session = (InvSession)this.sessions.get(viewer.getUniqueId());
            if (session != null) {
                Inventory top = session.viewerInventory;
                Inventory clicked = event.getClickedInventory();
                if (clicked != null && clicked.equals(top)) {
                    int slot = event.getSlot();
                    if (LOCKED_SLOTS.contains(slot)) {
                        event.setCancelled(true);
                    } else if (!this.hasPerm(viewer, "dmb.invsee.edit")) {
                        event.setCancelled(true);
                        viewer.sendMessage(this.lang.getMessage("minecraft.invsee.no-permission-edit"));
                    } else {
                        if (this.isArmorSlot(slot) && this.isPlaceholder(top.getItem(slot))) {
                            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                                event.setCancelled(true);
                                return;
                            }

                            ItemStack cursor = viewer.getItemOnCursor();
                            if (cursor == null || cursor.getType() == Material.AIR) {
                                event.setCancelled(true);
                                return;
                            }

                            top.setItem(slot, (ItemStack)null);
                        }

                        Bukkit.getScheduler().runTask(this.plugin, () -> {
                            this.pushToTarget(session);
                            this.refreshArmorPlaceholders(session);
                            this.refreshHeadItem(session);
                        });
                    }
                } else {
                    if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        if (!this.hasPerm(viewer, "dmb.invsee.edit")) {
                            event.setCancelled(true);
                            viewer.sendMessage(this.lang.getMessage("minecraft.invsee.no-permission-edit"));
                            return;
                        }

                        ItemStack moving = event.getCurrentItem();
                        if (moving != null && moving.getType() != Material.AIR) {
                            event.setCancelled(true);

                            for(int i = 0; i < top.getSize(); ++i) {
                                if (!LOCKED_SLOTS.contains(i)) {
                                    ItemStack there = top.getItem(i);
                                    if ((there == null || there.getType() == Material.AIR || this.isPlaceholder(there)) && !this.isPlaceholder(there)) {
                                        top.setItem(i, moving);
                                        event.setCurrentItem((ItemStack)null);
                                        break;
                                    }
                                }
                            }

                            Bukkit.getScheduler().runTask(this.plugin, () -> {
                                this.pushToTarget(session);
                                this.refreshArmorPlaceholders(session);
                                this.refreshHeadItem(session);
                            });
                        }
                    }

                }
            }
        }
    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player viewer) {
            InvSession session = (InvSession)this.sessions.get(viewer.getUniqueId());
            if (session != null) {
                boolean topAffected = event.getRawSlots().stream().anyMatch((s) -> s < session.viewerInventory.getSize());
                if (topAffected) {
                    for(int slot : event.getInventorySlots()) {
                        if (LOCKED_SLOTS.contains(slot)) {
                            event.setCancelled(true);
                            return;
                        }
                    }

                    if (!this.hasPerm(viewer, "dmb.invsee.edit")) {
                        event.setCancelled(true);
                        viewer.sendMessage(this.lang.getMessage("minecraft.invsee.no-permission-edit"));
                    } else {
                        Bukkit.getScheduler().runTask(this.plugin, () -> {
                            this.pushToTarget(session);
                            this.refreshArmorPlaceholders(session);
                            this.refreshHeadItem(session);
                        });
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity var3 = event.getPlayer();
        if (var3 instanceof Player viewer) {
            InvSession session = (InvSession)this.sessions.get(viewer.getUniqueId());
            if (session != null && event.getInventory().equals(session.viewerInventory)) {
                this.pushToTarget(session);
                this.sessions.remove(viewer.getUniqueId());
                Set<UUID> vs = (Set)this.targetToViewers.get(session.targetUuid);
                if (vs != null) {
                    vs.remove(viewer.getUniqueId());
                }

                Logger var10000 = this.plugin.getLogger();
                String var10001 = viewer.getName();
                var10000.info("[InvSee] " + var10001 + " hat " + session.targetName + "'s Inventar geschlossen.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitting = event.getPlayer();
        InvSession vs = (InvSession)this.sessions.remove(quitting.getUniqueId());
        if (vs != null) {
            this.pushToTarget(vs);
            Set<UUID> s = (Set)this.targetToViewers.get(vs.targetUuid);
            if (s != null) {
                s.remove(quitting.getUniqueId());
            }
        }

        Set<UUID> viewers = (Set)this.targetToViewers.remove(quitting.getUniqueId());
        if (viewers != null) {
            for(UUID viewerUuid : viewers) {
                this.sessions.remove(viewerUuid);
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                    viewer.sendMessage(this.lang.getMessage("minecraft.invsee.target-offline").replace("{player}", quitting.getName()));
                }
            }
        }

    }

    private static ItemStack makeGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack makeLabelItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }

        return item;
    }

    private void terminateSession(UUID viewerUuid, boolean notify) {
        InvSession session = (InvSession)this.sessions.remove(viewerUuid);
        if (session != null) {
            Set<UUID> vs = (Set)this.targetToViewers.get(session.targetUuid);
            if (vs != null) {
                vs.remove(viewerUuid);
            }

            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) {
                viewer.closeInventory();
                if (notify) {
                    viewer.sendMessage(this.lang.getMessage("minecraft.invsee.target-offline").replace("{player}", session.targetName));
                }
            }

        }
    }

    private boolean hasPerm(Player p, String perm) {
        return p.hasPermission(perm) || p.hasPermission("dmb.admin");
    }

    private void sendUsage(Player p) {
        p.sendMessage(this.lang.getMessage("minecraft.invsee.usage"));
        p.sendMessage(this.lang.getMessage("minecraft.invsee.example-inv"));
        p.sendMessage(this.lang.getMessage("minecraft.invsee.example-end"));
    }

    public void shutdown() {
        if (this.syncTask != null) {
            this.syncTask.cancel();
        }

        for(UUID u : new HashSet<UUID>(this.sessions.keySet())) {
            this.terminateSession(u, false);
        }

        this.sessions.clear();
        this.targetToViewers.clear();
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        } else if (args.length == 1) {
            return (List)Arrays.asList("inv", "end").stream().filter((s) -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else {
            return args.length == 2 ? (List)Bukkit.getOnlinePlayers().stream().map(Player::getName).filter((n) -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList()) : Collections.emptyList();
        }
    }

    static {
        SEPARATOR = makeGlass(Material.GRAY_STAINED_GLASS_PANE, String.valueOf(ChatColor.DARK_GRAY) + "▪");
        FILLER = makeGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static class InvSession {
        final UUID viewerUuid;
        final UUID targetUuid;
        final String targetName;
        final Inventory viewerInventory;
        final boolean isEnd;
        final boolean isOnline;

        InvSession(UUID v, UUID t, String tn, Inventory vi, boolean e, boolean o) {
            this.viewerUuid = v;
            this.targetUuid = t;
            this.targetName = tn;
            this.viewerInventory = vi;
            this.isEnd = e;
            this.isOnline = o;
        }
    }
}