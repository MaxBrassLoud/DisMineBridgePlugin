package de.MaxBrassLoud.disMineBridge.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Liest und schreibt Spieler-Inventar und Enderchest direkt aus der
 * Minecraft Spieler-Datendatei: <world>/playerdata/<UUID>.dat
 *
 * Keine externe Library – nutzt ausschliesslich NMS-Klassen via Reflection.
 * Kompatibel mit Spigot/Paper 1.20.4 – 1.21.x
 *
 * Vanilla NBT-Slot-IDs:
 *   0–35  = Hotbar (0–8) + Hauptinventar (9–35)
 *   100   = Boots
 *   101   = Leggings
 *   102   = Chestplate
 *   103   = Helmet
 *   -106  = Offhand (als signed byte)
 */
public class OfflinePlayerData {

    // Vanilla NBT-Slot-IDs
    private static final int NBT_BOOTS      = 100;
    private static final int NBT_LEGGINGS   = 101;
    private static final int NBT_CHESTPLATE = 102;
    private static final int NBT_HELMET     = 103;
    private static final int NBT_OFFHAND    = -106;

    // Positionen im 54-Slot Viewer-Inventar (aus InvSeeCommand)
    public static final int VIEW_BOOTS      = 36;
    public static final int VIEW_LEGGINGS   = 37;
    public static final int VIEW_CHESTPLATE = 38;
    public static final int VIEW_HELMET     = 39;
    public static final int VIEW_OFFHAND    = 40;

    private final UUID   uuid;
    private final File   datFile;
    private final Logger log;

    // Gecachte NMS-Klassen (lazy, einmalig)
    private static volatile boolean classesLoaded    = false;
    private static Class<?> clsNbtIo;
    private static Class<?> clsCompoundTag;
    private static Class<?> clsListTag;
    private static Class<?> clsNmsItemStack;
    private static Class<?> clsCraftItemStack;
    private static Class<?> clsNbtAccounter;

    // ─────────────────────────────────────────────────────────────────────

    public OfflinePlayerData(UUID uuid, Logger log) {
        this.uuid = uuid;
        this.log  = log;
        File world = Bukkit.getWorlds().get(0).getWorldFolder();
        this.datFile = new File(world, "playerdata" + File.separator + uuid + ".dat");
    }

    public boolean exists() { return datFile.exists(); }

    // ─────────────────────────────────────────────────────────────────────
    // Oeffentliche API
    // ─────────────────────────────────────────────────────────────────────

    /** Liest Hauptinventar aus .dat -> 54-Slot-Viewer-Array. Gibt null bei Fehler zurueck. */
    public ItemStack[] readInventory() {
        try {
            Object root = readDat();
            if (root == null) return null;

            ItemStack[] result = new ItemStack[54];
            Object list = getListTag(root, "Inventory");
            if (list == null) return result;

            for (int i = 0, n = listSize(list); i < n; i++) {
                Object tag  = listGetCompound(list, i);
                if (tag == null) continue;
                int slot    = getTagByte(tag, "Slot"); // signed
                ItemStack is = tagToItem(tag);
                if (is == null || is.getType() == Material.AIR) continue;

                if      (slot >= 0 && slot <= 35)  result[slot]             = is;
                else if (slot == NBT_BOOTS)         result[VIEW_BOOTS]       = is;
                else if (slot == NBT_LEGGINGS)      result[VIEW_LEGGINGS]    = is;
                else if (slot == NBT_CHESTPLATE)    result[VIEW_CHESTPLATE]  = is;
                else if (slot == NBT_HELMET)        result[VIEW_HELMET]      = is;
                else if (slot == NBT_OFFHAND || slot == 150) result[VIEW_OFFHAND] = is;
            }
            return result;
        } catch (Exception e) {
            log.warning("[InvSee] readInventory() Fehler: " + e);
            return null;
        }
    }

    /** Liest Enderchest aus .dat -> 27-Slot-Array. Gibt null bei Fehler zurueck. */
    public ItemStack[] readEnderChest() {
        try {
            Object root = readDat();
            if (root == null) return null;

            ItemStack[] result = new ItemStack[27];
            Object list = getListTag(root, "EnderItems");
            if (list == null) return result;

            for (int i = 0, n = listSize(list); i < n; i++) {
                Object tag = listGetCompound(list, i);
                if (tag == null) continue;
                int slot = getTagByte(tag, "Slot") & 0xFF;
                ItemStack is = tagToItem(tag);
                if (is != null && slot < 27) result[slot] = is;
            }
            return result;
        } catch (Exception e) {
            log.warning("[InvSee] readEnderChest() Fehler: " + e);
            return null;
        }
    }

    /** Schreibt Inventar (54-Slot-Viewer-Array) zurueck in die .dat-Datei. */
    public boolean writeInventory(ItemStack[] viewerContents) {
        try {
            Object root = readDat();
            if (root == null) return false;

            Object list = newListTag();
            for (int slot = 0; slot <= 35; slot++)
                addItem(list, getOrNull(viewerContents, slot), (byte) slot);
            addItem(list, getOrNull(viewerContents, VIEW_BOOTS),      (byte) NBT_BOOTS);
            addItem(list, getOrNull(viewerContents, VIEW_LEGGINGS),   (byte) NBT_LEGGINGS);
            addItem(list, getOrNull(viewerContents, VIEW_CHESTPLATE), (byte) NBT_CHESTPLATE);
            addItem(list, getOrNull(viewerContents, VIEW_HELMET),     (byte) NBT_HELMET);
            addItem(list, getOrNull(viewerContents, VIEW_OFFHAND),    (byte) NBT_OFFHAND);

            putTag(root, "Inventory", list);
            return writeDat(root);
        } catch (Exception e) {
            log.warning("[InvSee] writeInventory() Fehler: " + e);
            return false;
        }
    }

    /** Schreibt Enderchest (27-Slot-Array) zurueck in die .dat-Datei. */
    public boolean writeEnderChest(ItemStack[] contents) {
        try {
            Object root = readDat();
            if (root == null) return false;

            Object list = newListTag();
            for (int i = 0; i < Math.min(27, contents.length); i++)
                addItem(list, contents[i], (byte) i);

            putTag(root, "EnderItems", list);
            return writeDat(root);
        } catch (Exception e) {
            log.warning("[InvSee] writeEnderChest() Fehler: " + e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // .dat Datei-I/O
    // ─────────────────────────────────────────────────────────────────────

    private Object readDat() throws Exception {
        if (!datFile.exists()) return null;
        load();

        // Modern (1.20.2+): NbtIo.readCompressed(Path, NbtAccounter)
        if (clsNbtAccounter != null) {
            try {
                Object unlimited = clsNbtAccounter.getMethod("unlimitedHeap").invoke(null);
                return clsNbtIo.getMethod("readCompressed", Path.class, clsNbtAccounter)
                        .invoke(null, datFile.toPath(), unlimited);
            } catch (NoSuchMethodException ignored) {}
        }
        // Fallback: NbtIo.readCompressed(InputStream)
        try (InputStream in = Files.newInputStream(datFile.toPath())) {
            return clsNbtIo.getMethod("readCompressed", InputStream.class).invoke(null, in);
        }
    }

    private boolean writeDat(Object root) throws Exception {
        load();
        // Backup anlegen
        Path bak = datFile.toPath().resolveSibling(uuid + ".dat.bak");
        Files.copy(datFile.toPath(), bak, StandardCopyOption.REPLACE_EXISTING);

        // Modern: NbtIo.writeCompressed(CompoundTag, Path)
        try {
            clsNbtIo.getMethod("writeCompressed", clsCompoundTag, Path.class)
                    .invoke(null, root, datFile.toPath());
            return true;
        } catch (NoSuchMethodException ignored) {}

        // Fallback: NbtIo.writeCompressed(CompoundTag, OutputStream)
        try (OutputStream out = Files.newOutputStream(datFile.toPath())) {
            clsNbtIo.getMethod("writeCompressed", clsCompoundTag, OutputStream.class)
                    .invoke(null, root, out);
            return true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // NBT-Hilfsmethoden
    // ─────────────────────────────────────────────────────────────────────

    private Object getListTag(Object compound, String key) throws Exception {
        Object list = clsCompoundTag.getMethod("getList", String.class, int.class)
                .invoke(compound, key, 10); // type 10 = CompoundTag
        return listSize(list) == 0 ? null : list;
    }

    private int listSize(Object list) throws Exception {
        return (int) list.getClass().getMethod("size").invoke(list);
    }

    private Object listGetCompound(Object list, int i) throws Exception {
        return clsListTag.getMethod("getCompound", int.class).invoke(list, i);
    }

    private int getTagByte(Object compound, String key) throws Exception {
        return (byte) clsCompoundTag.getMethod("getByte", String.class).invoke(compound, key);
    }

    private Object newListTag() throws Exception {
        return clsListTag.getDeclaredConstructor().newInstance();
    }

    private void addItem(Object list, ItemStack item, byte slot) throws Exception {
        if (item == null || item.getType() == Material.AIR) return;
        Object tag = itemToTag(item);
        if (tag == null) return;
        setTagByte(tag, "Slot", slot);
        // ListTag.add(Tag)
        for (Method m : list.getClass().getMethods()) {
            if (m.getName().equals("add") && m.getParameterCount() == 1) {
                try { m.invoke(list, tag); return; } catch (Exception ignored) {}
            }
        }
    }

    private void putTag(Object compound, String key, Object value) throws Exception {
        // CompoundTag.put(String, Tag) – "Tag" ist die abstrakte Superklasse
        for (Method m : clsCompoundTag.getMethods()) {
            if (m.getName().equals("put") && m.getParameterCount() == 2) {
                try { m.invoke(compound, key, value); return; } catch (Exception ignored) {}
            }
        }
    }

    private void setTagByte(Object compound, String key, byte value) throws Exception {
        // ByteTag.valueOf(byte) oder new NBTTagByte(byte)
        Class<?> byteTagCls = findClass("net.minecraft.nbt.ByteTag", "NBTTagByte");
        Object byteTag;
        try   { byteTag = byteTagCls.getMethod("valueOf", byte.class).invoke(null, value); }
        catch (NoSuchMethodException e) { byteTag = byteTagCls.getDeclaredConstructor(byte.class).newInstance(value); }
        putTag(compound, key, byteTag);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ItemStack <-> NBT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * NBT-CompoundTag -> Bukkit ItemStack.
     * Probiert mehrere API-Varianten (1.21, 1.20.x, Legacy).
     */
    private ItemStack tagToItem(Object tag) {
        try {
            load();
            Object nmsItem = null;

            // 1.21: ItemStack.parse(HolderLookup.Provider, CompoundTag) -> Optional<ItemStack>
            try {
                Class<?> hlp = findClass("net.minecraft.core.HolderLookup$Provider", null);
                Class<?> mcs = findClass("net.minecraft.server.MinecraftServer", "MinecraftServer");
                if (hlp != null && mcs != null) {
                    Object srv = mcs.getMethod("getServer").invoke(null);
                    Object reg = srv.getClass().getMethod("registryAccess").invoke(srv);
                    Object opt = clsNmsItemStack.getMethod("parse", hlp, clsCompoundTag).invoke(null, reg, tag);
                    nmsItem    = opt.getClass().getMethod("orElse", Object.class).invoke(opt, (Object) null);
                }
            } catch (Exception ignored) {}

            // 1.20.x: ItemStack.of(CompoundTag)
            if (nmsItem == null) {
                try { nmsItem = clsNmsItemStack.getMethod("of", clsCompoundTag).invoke(null, tag); }
                catch (NoSuchMethodException ignored) {}
            }

            if (nmsItem == null) return null;

            return (ItemStack) clsCraftItemStack.getMethod("asBukkitCopy", clsNmsItemStack).invoke(null, nmsItem);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bukkit ItemStack -> NBT-CompoundTag.
     */
    private Object itemToTag(ItemStack item) {
        try {
            load();
            Object nms = clsCraftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);

            // 1.21: ItemStack.save(HolderLookup.Provider) -> Tag
            try {
                Class<?> hlp = findClass("net.minecraft.core.HolderLookup$Provider", null);
                Class<?> mcs = findClass("net.minecraft.server.MinecraftServer", "MinecraftServer");
                if (hlp != null && mcs != null) {
                    Object srv = mcs.getMethod("getServer").invoke(null);
                    Object reg = srv.getClass().getMethod("registryAccess").invoke(srv);
                    return clsNmsItemStack.getMethod("save", hlp).invoke(nms, reg);
                }
            } catch (Exception ignored) {}

            // 1.20.x: ItemStack.save(CompoundTag) -> CompoundTag
            Object tag = clsCompoundTag.getDeclaredConstructor().newInstance();
            try { return clsNmsItemStack.getMethod("save", clsCompoundTag).invoke(nms, tag); }
            catch (NoSuchMethodException ignored) {}

            // Legacy obfuskiert: ItemStack.b(CompoundTag)
            try { return clsNmsItemStack.getMethod("b", clsCompoundTag).invoke(nms, tag); }
            catch (NoSuchMethodException ignored) {}

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Klassen-Loader
    // ─────────────────────────────────────────────────────────────────────

    private static synchronized void load() throws Exception {
        if (classesLoaded) return;
        clsNbtIo          = findClass("net.minecraft.nbt.NbtIo",            "NBTCompressedStreamTools");
        clsCompoundTag    = findClass("net.minecraft.nbt.CompoundTag",       "NBTTagCompound");
        clsListTag        = findClass("net.minecraft.nbt.ListTag",           "NBTTagList");
        clsNmsItemStack   = findClass("net.minecraft.world.item.ItemStack",  "ItemStack");
        clsCraftItemStack = findCraft("inventory.CraftItemStack");
        try { clsNbtAccounter = findClass("net.minecraft.nbt.NbtAccounter", "NBTReadLimiter"); }
        catch (Exception ignored) {}
        if (clsNbtIo == null || clsCompoundTag == null || clsNmsItemStack == null)
            throw new ClassNotFoundException("NMS-Pflichtklassen nicht gefunden");
        classesLoaded = true;
    }

    private static Class<?> findClass(String modern, String legacy) {
        try { return Class.forName(modern); } catch (ClassNotFoundException ignored) {}
        if (legacy != null) {
            try {
                String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                return Class.forName("net.minecraft.server." + v + "." + legacy);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Class<?> findCraft(String sub) {
        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            return Class.forName("org.bukkit.craftbukkit." + v + "." + sub);
        } catch (Exception ignored) {}
        try { return Class.forName("org.bukkit.craftbukkit." + sub); }
        catch (Exception ignored) {}
        return null;
    }

    private static ItemStack getOrNull(ItemStack[] a, int i) {
        return (a != null && i < a.length) ? a[i] : null;
    }
}