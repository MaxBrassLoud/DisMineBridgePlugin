package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WhitelistCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;

    public WhitelistCommand(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();

        if (!sender.hasPermission("dmb.whitelist")) {
            sender.sendMessage(lang.getMessage("minecraft.whitelist.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lang.getMessage("minecraft.whitelist.usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "on":
                plugin.getConfig().set("whitelist-enabled", true);
                plugin.saveConfig();
                sender.sendMessage(lang.getMessage("minecraft.whitelist.enabled"));
                break;

            case "off":
                plugin.getConfig().set("whitelist-enabled", false);
                plugin.saveConfig();
                sender.sendMessage(lang.getMessage("minecraft.whitelist.disabled"));
                break;

            case "add":
                if (args.length < 2) {
                    sender.sendMessage(lang.getMessage("minecraft.whitelist.usage"));
                    return true;
                }

                String playerToAdd = args[1];

                if (plugin.getWhitelistManager().isWhitelisted(playerToAdd)) {
                    sender.sendMessage(lang.getMessage("minecraft.whitelist.already-added")
                            .replace("{player}", playerToAdd));
                    return true;
                }

                plugin.getWhitelistManager().addToWhitelist(playerToAdd);
                sender.sendMessage(lang.getMessage("minecraft.whitelist.added")
                        .replace("{player}", playerToAdd));
                break;

            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(lang.getMessage("minecraft.whitelist.usage"));
                    return true;
                }

                String playerToRemove = args[1];

                if (!plugin.getWhitelistManager().isWhitelisted(playerToRemove)) {
                    sender.sendMessage(lang.getMessage("minecraft.whitelist.not-on-list")
                            .replace("{player}", playerToRemove));
                    return true;
                }

                plugin.getWhitelistManager().removeFromWhitelist(playerToRemove);
                sender.sendMessage(lang.getMessage("minecraft.whitelist.removed")
                        .replace("{player}", playerToRemove));
                break;

            default:
                sender.sendMessage(lang.getMessage("minecraft.whitelist.usage"));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("on", "off", "add", "remove"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            // Hier könnten Spielernamen vorgeschlagen werden
            return null; // Bukkit's Standard-Vervollständigung für Spielernamen
        }

        return completions;
    }
}