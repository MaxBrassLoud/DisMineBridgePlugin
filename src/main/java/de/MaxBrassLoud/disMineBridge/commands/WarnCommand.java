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

public class WarnCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final LanguageManager lang;

    public WarnCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.warn") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.warn.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lang.getMessage("minecraft.warn.usage"));
            sender.sendMessage(lang.getMessage("minecraft.warn.examples-header"));
            sender.sendMessage(lang.getMessage("minecraft.warn.example-1"));
            sender.sendMessage(lang.getMessage("minecraft.warn.example-2"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(lang.getMessage("minecraft.warn.player-not-found")
                    .replace("{player}", targetName));
            return true;
        }

        // Grund zusammenbauen
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                .replace("_", " ")
                .replace("/n", "\n");
        if (reason.trim().isEmpty()) {
            reason = lang.getMessage("minecraft.warn.no-reason-given");
        }

        // Speichere in Datenbank
        try {
            DatabaseManager.createWarn(
                    target.getUniqueId().toString(),
                    target.getName(),
                    reason,
                    sender.getName()
            );
        } catch (SQLException e) {
            sender.sendMessage(lang.getMessage("minecraft.warn.database-error"));
            e.printStackTrace();
            return true;
        }

        // Zähle Gesamtanzahl der Verwarnungen
        int totalWarns = getWarnCount(target);

        // Nachricht an Spieler
        target.sendMessage("");
        target.sendMessage(lang.getMessage("minecraft.warn.message.header"));
        target.sendMessage(lang.getMessage("minecraft.warn.message.title"));
        target.sendMessage("");
        target.sendMessage(lang.getMessage("minecraft.warn.message.reason")
                .replace("{reason}", reason));
        target.sendMessage(lang.getMessage("minecraft.warn.message.by")
                .replace("{warner}", sender.getName()));
        target.sendMessage(lang.getMessage("minecraft.warn.message.total")
                .replace("{total}", String.valueOf(totalWarns)));
        target.sendMessage("");

        // Warnung bei vielen Verwarnungen
        if (totalWarns >= 3) {
            target.sendMessage(lang.getMessage("minecraft.warn.message.warning-threshold-1"));
            target.sendMessage(lang.getMessage("minecraft.warn.message.warning-threshold-2"));
        }

        target.sendMessage(lang.getMessage("minecraft.warn.message.footer"));
        target.sendMessage("");

        // Broadcast an Server
        Bukkit.broadcastMessage(lang.getMessage("minecraft.warn.broadcast")
                .replace("{player}", target.getName())
                .replace("{warner}", sender.getName()));

        sender.sendMessage(lang.getMessage("minecraft.warn.success"));
        sender.sendMessage(lang.getMessage("minecraft.warn.total-warns")
                .replace("{total}", String.valueOf(totalWarns)));

        return true;
    }

    /**
     * Zählt die Anzahl der Verwarnungen eines Spielers
     */
    private int getWarnCount(Player player) {
        try {
            return DatabaseManager.getWarnCount(player.getUniqueId().toString());
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
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
                List<String> reasons = DatabaseManager.getPunishmentReasons("WARN");
                for (String reason : reasons) {
                    completions.add(reason.replace(" ", "_"));
                }
            } catch (SQLException e) {
                // Fallback zu Standard-Gründen
                String defaultReasons = lang.getMessage("minecraft.warn.default-reasons");
                completions.addAll(Arrays.asList(defaultReasons.split(",")));
            }

            // Füge "Eigener_Grund" hinzu
            completions.add(lang.getMessage("minecraft.warn.custom-reason"));

            // Filtern basierend auf Input
            String input = args[1].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        return completions;
    }
}