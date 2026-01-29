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
import java.util.*;
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

        // Grund zusammenfügen (optional)
        String reason = args.length > 1 ?
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)).replace("_", " ") :
                language.getMessage("minecraft.unban.no-reason-given");

        try {
            // Suche aktiven Ban NACH NAMEN (nicht UUID!)
            BanInfo banInfo = database.getActiveBanByName(targetName);

            if (banInfo == null) {
                sender.sendMessage(language.getMessage("minecraft.unban.not-banned")
                        .replace("{player}", targetName));
                return true;
            }

            // Hole Pardoner ID
            int pardonerId = 0;
            if (sender instanceof Player) {
                Player pardoner = (Player) sender;
                pardonerId = database.createOrGetUser(pardoner.getUniqueId().toString());
            }

            // Entbanne den Spieler
            database.pardonBan(banInfo.banId, pardonerId, reason);

            // Erfolgsmeldung
            sender.sendMessage(language.getMessage("minecraft.unban.success")
                    .replace("{player}", targetName));

            // Broadcast
            Bukkit.broadcastMessage(language.getMessage("minecraft.unban.broadcast")
                    .replace("{player}", targetName)
                    .replace("{unbanner}", sender.getName()));

            plugin.getLogger().info("[Unban] " + targetName + " wurde von " + sender.getName() +
                    " entbannt. Grund: " + reason);

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
            // Zeige aktuell gebannte Spieler
            try {
                List<String> bannedPlayers = database.getBannedPlayerNames();
                String input = args[0].toLowerCase();

                for (String playerName : bannedPlayers) {
                    if (playerName.toLowerCase().startsWith(input)) {
                        completions.add(playerName);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Fehler beim Laden der gebannten Spieler für Tab-Completion");
            }

            return completions;
        }

        if (args.length == 2) {
            // Gründe vorschlagen
            String defaultReasons = language.getMessage("minecraft.unban.default-reasons");
            return Arrays.stream(defaultReasons.split(","))
                    .map(s -> s.replace(" ", "_"))
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}