package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.PunishmentManager;
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
import java.util.List;
import java.util.stream.Collectors;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final LanguageManager lang;
    private final PunishmentManager punishmentManager;

    public PunishCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("dmb.punish") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.punish.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lang.getMessage("minecraft.punish.usage"));
            sender.sendMessage(lang.getMessage("minecraft.punish.examples-header"));
            sender.sendMessage(lang.getMessage("minecraft.punish.example-1"));
            sender.sendMessage(lang.getMessage("minecraft.punish.example-2"));
            sender.sendMessage(lang.getMessage("minecraft.punish.example-3"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(lang.getMessage("minecraft.punish.player-not-found")
                    .replace("{player}", targetName));
            return true;
        }

        // Grund zusammenbauen (mit Unterstrichen)
        String reasonName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                .replace("_", " ");

        // Suche den Grund in der Datenbank
        PunishmentManager.PunishmentReason reason = punishmentManager.getReasonByName(reasonName);

        if (reason == null) {
            sender.sendMessage(lang.getMessage("minecraft.punish.reason-not-found")
                    .replace("{reason}", reasonName));
            sender.sendMessage(lang.getMessage("minecraft.punish.use-punishment-list"));
            return true;
        }

        // Hole Verstoß-Anzahl
        int violationCount;
        try {
            violationCount = DatabaseManager.getInstance().getViolationCount(
                    target.getUniqueId().toString(),
                    reasonName
            );
        } catch (SQLException e) {
            sender.sendMessage(lang.getMessage("minecraft.punish.database-error"));
            e.printStackTrace();
            return true;
        }

        // Bestimme nächste Strafe
        String nextPunishment = getNextPunishmentType(violationCount);

        // Zeige Info an Executor
        sender.sendMessage(lang.getMessage("minecraft.punish.executing")
                .replace("{player}", target.getName())
                .replace("{reason}", reasonName)
                .replace("{violation}", String.valueOf(violationCount + 1))
                .replace("{punishment}", nextPunishment));

        sender.sendMessage(lang.getMessage("minecraft.punish.executing-details")
                .replace("{reason}", reasonName));
        sender.sendMessage(lang.getMessage("minecraft.punish.executing-violation")
                .replace("{violation}", String.valueOf(violationCount + 1)));
        sender.sendMessage(lang.getMessage("minecraft.punish.executing-punishment")
                .replace("{punishment}", nextPunishment));

        // Führe die eskalierende Bestrafung aus
        Player executorPlayer = sender instanceof Player ? (Player) sender : null;

        if (punishmentManager.executePunishment(target, reasonName, executorPlayer)) {
            sender.sendMessage(lang.getMessage("minecraft.punish.success"));
        } else {
            sender.sendMessage(lang.getMessage("minecraft.punish.error"));
        }

        return true;
    }

    private String getNextPunishmentType(int violationCount) {
        switch (violationCount) {
            case 0: return lang.getMessage("minecraft.punish.level.warn");
            case 1: return lang.getMessage("minecraft.punish.level.kick");
            case 2: return lang.getMessage("minecraft.punish.level.tempban-short");
            case 3: return lang.getMessage("minecraft.punish.level.tempban-medium");
            case 4: return lang.getMessage("minecraft.punish.level.tempban-long");
            default: return lang.getMessage("minecraft.punish.level.permban");
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
            // Alle Gründe aus allen Typen laden
            for (String type : java.util.Arrays.asList("WARN", "KICK", "BAN")) {
                List<PunishmentManager.PunishmentReason> reasons = punishmentManager.getReasons(type);
                for (PunishmentManager.PunishmentReason reason : reasons) {
                    completions.add(reason.getReason().replace(" ", "_"));
                }
            }

            // Filtern basierend auf Input
            String input = args[1].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .distinct()
                    .collect(Collectors.toList());
        }

        return completions;
    }
}