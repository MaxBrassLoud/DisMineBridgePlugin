package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import de.MaxBrassLoud.disMineBridge.managers.PunishmentManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PunishmentCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final LanguageManager lang;
    private final PunishmentManager punishmentManager;

    public PunishmentCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        // Subcommand Handling
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                return handleAdd(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dmb.punishment.add") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.usage"));
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.examples-header"));
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.example-1"));
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.example-2"));
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.example-3"));
            return true;
        }

        String type = args[1].toUpperCase();
        String duration = args[2];
        String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        // Validiere Type
        if (!isValidType(type)) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.invalid-type")
                    .replace("{type}", type));
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.valid-types"));
            return true;
        }

        // Füge Grund hinzu
        if (punishmentManager.addReason(type, reason, duration)) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.success")
                    .replace("{type}", type)
                    .replace("{reason}", reason)
                    .replace("{duration}", duration));
        } else {
            sender.sendMessage(lang.getMessage("minecraft.punishment.add.error"));
        }

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dmb.punishment.remove") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.remove.usage"));
            return true;
        }

        try {
            int reasonId = Integer.parseInt(args[1]);

            if (punishmentManager.removeReason(reasonId)) {
                sender.sendMessage(lang.getMessage("minecraft.punishment.remove.success")
                        .replace("{id}", String.valueOf(reasonId)));
            } else {
                sender.sendMessage(lang.getMessage("minecraft.punishment.remove.error"));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.remove.invalid-id"));
        }

        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dmb.punishment.list") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.no-permission"));
            return true;
        }

        String type = args.length > 1 ? args[1].toUpperCase() : "ALL";

        if (!type.equals("ALL") && !isValidType(type)) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.list.invalid-type"));
            return true;
        }

        sender.sendMessage(lang.getMessage("minecraft.punishment.list.header"));

        if (type.equals("ALL")) {
            // Zeige alle Typen
            for (String t : Arrays.asList("WARN", "KICK", "BAN")) {
                displayReasonsForType(sender, t);
            }
        } else {
            displayReasonsForType(sender, type);
        }

        sender.sendMessage(lang.getMessage("minecraft.punishment.list.footer"));

        return true;
    }

    private void displayReasonsForType(CommandSender sender, String type) {
        List<PunishmentManager.PunishmentReason> reasons = punishmentManager.getReasons(type);

        if (reasons.isEmpty()) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.list.no-reasons")
                    .replace("{type}", type));
            return;
        }

        sender.sendMessage(lang.getMessage("minecraft.punishment.list.type-header")
                .replace("{type}", type)
                .replace("{count}", String.valueOf(reasons.size())));

        for (PunishmentManager.PunishmentReason reason : reasons) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.list.entry")
                    .replace("{id}", String.valueOf(reason.getId()))
                    .replace("{reason}", reason.getReason())
                    .replace("{duration}", reason.getDuration().isEmpty() ? "-" : reason.getDuration()));
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("dmb.punishment.reload") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.punishment.no-permission"));
            return true;
        }

        punishmentManager.reloadCache();
        sender.sendMessage(lang.getMessage("minecraft.punishment.reload.success"));

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(lang.getMessage("minecraft.punishment.usage.header"));
        sender.sendMessage(lang.getMessage("minecraft.punishment.usage.add"));
        sender.sendMessage(lang.getMessage("minecraft.punishment.usage.remove"));
        sender.sendMessage(lang.getMessage("minecraft.punishment.usage.list"));
        sender.sendMessage(lang.getMessage("minecraft.punishment.usage.reload"));
    }

    private boolean isValidType(String type) {
        return Arrays.asList("WARN", "KICK", "BAN").contains(type.toUpperCase());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcommands
            completions.addAll(Arrays.asList("add", "remove", "list", "reload"));
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();

            if (subCmd.equals("add") || subCmd.equals("list")) {
                // Punishment Types
                completions.addAll(Arrays.asList("WARN", "KICK", "BAN"));
            } else if (subCmd.equals("remove")) {
                // Hier könnten IDs vorgeschlagen werden
                completions.add("<ID>");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            // Duration suggestions
            completions.addAll(Arrays.asList("-", "1d", "7d", "30d", "perm"));
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}