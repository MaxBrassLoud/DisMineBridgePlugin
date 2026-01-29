package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KickCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final LanguageManager lang;

    public KickCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.kick") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.kick.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lang.getMessage("minecraft.kick.usage"));
            sender.sendMessage(lang.getMessage("minecraft.kick.examples-header"));
            sender.sendMessage(lang.getMessage("minecraft.kick.example-1"));
            sender.sendMessage(lang.getMessage("minecraft.kick.example-2"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(lang.getMessage("minecraft.kick.player-not-found")
                    .replace("{player}", targetName));
            return true;
        }

        // Grund zusammenbauen
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                .replace("_", " ");
        if (reason.isEmpty()) {
            reason = lang.getMessage("minecraft.kick.no-reason-given");
        }

        // In Datenbank speichern
        try {
            DatabaseManager.createKick(
                    target.getUniqueId().toString(),
                    target.getName(),
                    reason,
                    sender.getName()
            );
        } catch (SQLException e) {
            sender.sendMessage(lang.getMessage("minecraft.kick.database-error"));
            e.printStackTrace();
            return true;
        }

        // Kick Message erstellen und Spieler kicken
        String kickMessage = lang.getMessage("minecraft.kick.kick-message")
                .replace("{reason}", reason);
        target.kickPlayer(kickMessage);

        // Broadcast an Server
        Bukkit.broadcastMessage(lang.getMessage("minecraft.kick.broadcast")
                .replace("{player}", target.getName())
                .replace("{kicker}", sender.getName()));

        sender.sendMessage(lang.getMessage("minecraft.kick.success"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Spieler vorschlagen
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Gründe aus Datenbank laden
            try {
                List<String> reasons = DatabaseManager.getPunishmentReasons("KICK");
                for (String reason : reasons) {
                    completions.add(reason.replace(" ", "_"));
                }
            } catch (SQLException e) {
                // Fallback zu Standard-Gründen
                String defaultReasons = lang.getMessage("minecraft.kick.default-reasons");
                completions.addAll(Arrays.asList(defaultReasons.split(",")));
            }

            // Füge "Eigener_Grund" hinzu
            completions.add(lang.getMessage("minecraft.kick.custom-reason"));

            // Filtern basierend auf Input
            String input = args[1].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        return completions;
    }
}