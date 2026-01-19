package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager.BanInfo;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class UnbanCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public UnbanCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
        this.language = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.unban") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(language.getMessage("minecraft.unban.no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(language.getMessage("minecraft.unban.usage"));
            sender.sendMessage(language.getMessage("minecraft.unban.example"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        String uuid = null;

        // Versuche UUID zu bekommen (online oder aus Datenbank)
        if (target != null && target.isOnline()) {
            uuid = target.getUniqueId().toString();
        } else {
            // Offline-Spieler - suche in Datenbank
            try {
                uuid = database.getMinecraftUuidByName(targetName);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Suchen der UUID", e);
            }
        }

        if (uuid == null) {
            // Versuche Ban direkt über Namen zu finden
            try {
                BanInfo banInfo = database.getActiveBanByName(targetName);
                if (banInfo == null) {
                    sender.sendMessage(language.getMessage("minecraft.unban.player-not-found")
                            .replace("{player}", targetName));
                    return true;
                }
                // UUID aus BanInfo verwenden falls vorhanden
                // Fahre mit dem Unban fort
            } catch (SQLException e) {
                sender.sendMessage(language.getMessage("minecraft.unban.player-not-found")
                        .replace("{player}", targetName));
                return true;
            }
        }
        // Grund für Unban (optional)
        String pardonReason = args.length > 1 ?
                String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        .replace("_", " ")
                        .replace("/n", "\n") :
                language.getMessage("minecraft.unban.no-reason-given");

        try {
            // Prüfe ob ein aktiver Ban existiert
            BanInfo banInfo = database.getActiveBan(uuid);

            if (banInfo == null) {
                sender.sendMessage(language.getMessage("minecraft.unban.not-banned")
                        .replace("{player}", targetName));
                return true;
            }

            // Erstelle Pardoner User-ID
            int pardonerId = sender instanceof Player ?
                    database.createOrGetUser(((Player) sender).getUniqueId().toString()) : 0;

            // Pardon den Ban
            database.pardonBan(banInfo.banId, pardonerId, pardonReason);

            // Bestätigung
            sender.sendMessage(language.getMessage("minecraft.unban.success")
                    .replace("{player}", targetName));

            // Broadcast
            Bukkit.broadcastMessage(language.getMessage("minecraft.unban.broadcast")
                    .replace("{player}", targetName)
                    .replace("{unbanner}", sender.getName()));

            plugin.getLogger().info("[Unban] " + targetName + " wurde von " +
                    sender.getName() + " entbannt. Grund: " + pardonReason);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Entbannen von " + targetName, e);
            sender.sendMessage(language.getMessage("minecraft.unban.database-error"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Spieler-Namen vorschlagen (online Spieler)
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            // Gründe für Unban vorschlagen
            String defaultReasons = language.getMessage("minecraft.unban.default-reasons");
            return Arrays.asList(defaultReasons.split(","));
        }

        return Collections.emptyList();
    }
}