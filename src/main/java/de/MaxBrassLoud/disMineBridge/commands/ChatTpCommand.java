package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /chattp <Spielername>
 *
 * Teleportiert den ausführenden Moderator sofort zum genannten Spieler.
 * Wird über den [TP]-Button hinter Chat-Nachrichten ausgelöst.
 *
 * Nur für Spieler mit dmb.chat.moderate oder dmb.admin.
 */
public class ChatTpCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final LanguageManager lang;

    public ChatTpCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang   = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player moderator)) {
            sender.sendMessage(lang.getMessage("general.player-only"));
            return true;
        }

        if (!moderator.hasPermission("dmb.chat.moderate") && !moderator.hasPermission("dmb.admin")) {
            moderator.sendMessage(lang.getMessage("minecraft.chat.tp-no-permission"));
            return true;
        }

        if (args.length < 1) {
            moderator.sendMessage(lang.getMessage("minecraft.chat.tp-usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            moderator.sendMessage(lang.getMessage("minecraft.chat.tp-not-found")
                    .replace("{player}", args[0]));
            return true;
        }

        if (target.equals(moderator)) {
            moderator.sendMessage(lang.getMessage("minecraft.chat.tp-self"));
            return true;
        }

        // Bukkit-Scheduler: Teleport muss auf dem Haupt-Thread laufen
        Bukkit.getScheduler().runTask(plugin, () -> {
            moderator.teleport(target.getLocation());
            moderator.sendMessage(lang.getMessage("minecraft.chat.tp-success")
                    .replace("{player}", target.getName()));
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}