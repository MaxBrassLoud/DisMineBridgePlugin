package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.MuteManager;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class UnmuteCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final DatabaseManager database;
    private final LanguageManager language;
    private final MuteManager muteManager;

    public UnmuteCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.database = DatabaseManager.getInstance();
        this.language = plugin.getLanguageManager();
        this.muteManager = plugin.getMuteManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.unmute") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(language.getMessage("minecraft.unmute.no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(language.getMessage("minecraft.unmute.usage"));
            sender.sendMessage(language.getMessage("minecraft.unmute.example"));
            return true;
        }

        String targetName = args[0];

        // Versuche online Spieler zu finden
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUuid;

        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            // Offline Spieler
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            if (!offlinePlayer.hasPlayedBefore()) {
                sender.sendMessage(language.getMessage("minecraft.unmute.player-not-found")
                        .replace("{player}", targetName));
                return true;
            }
            targetUuid = offlinePlayer.getUniqueId();
            targetName = offlinePlayer.getName();
        }

        // Prüfe ob gemutet
        if (!muteManager.isMuted(targetUuid)) {
            sender.sendMessage(language.getMessage("minecraft.unmute.not-muted")
                    .replace("{player}", targetName));
            return true;
        }

        // Grund (optional)
        String reason = args.length > 1 ?
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)).replace("_", " ") :
                language.getMessage("minecraft.unmute.no-reason-given");

        try {
            // Entmute in Datenbank
            database.unmutePlaye(targetUuid.toString(), sender.getName(), reason);

            // Entmute im MuteManager
            muteManager.unmutePlayer(targetUuid);

            // Nachricht an Spieler falls online
            if (target != null) {
                target.sendMessage(language.getMessage("minecraft.unmute.unmuted"));
            }

            // Broadcast
            Bukkit.broadcastMessage(language.getMessage("minecraft.unmute.broadcast")
                    .replace("{player}", targetName)
                    .replace("{unmuter}", sender.getName()));

            // Bestätigung
            sender.sendMessage(language.getMessage("minecraft.unmute.success")
                    .replace("{player}", targetName));

            plugin.getLogger().info("[Unmute] " + targetName + " wurde von " + sender.getName() +
                    " entmutet. Grund: " + reason);

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Entmuten: " + e.getMessage());
            sender.sendMessage(language.getMessage("minecraft.unmute.database-error"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Zeige aktuell gemutete Spieler
            try {
                List<String> mutedPlayers = database.getMutedPlayerNames();
                String input = args[0].toLowerCase();

                for (String playerName : mutedPlayers) {
                    if (playerName.toLowerCase().startsWith(input)) {
                        completions.add(playerName);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Fehler beim Laden der gemuteten Spieler");
            }

            return completions;
        }

        if (args.length == 2) {
            // Gründe vorschlagen
            String defaultReasons = language.getMessage("minecraft.unmute.default-reasons");
            return Arrays.stream(defaultReasons.split(","))
                    .map(s -> s.replace(" ", "_"))
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}