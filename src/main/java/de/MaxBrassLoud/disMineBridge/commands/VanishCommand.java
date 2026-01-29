package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.features.vanish.VanishManager;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final VanishManager vanishManager;
    private final LanguageManager language;

    public VanishCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.vanishManager = plugin.getVanishmanager();
        this.language = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /vanish ohne Argumente - Toggle für sich selbst
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(language.getMessage("minecraft.vanish.player-only"));
                return true;
            }

            Player p = (Player) sender;

            if (!p.hasPermission("dmb.vanish")) {
                p.sendMessage(language.getMessage("minecraft.vanish.no-permission"));
                return true;
            }

            toggleVanish(p, sender);
            return true;
        }

        // /vanish list - Zeige alle im Vanish
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("liste")) {
            if (!sender.hasPermission("dmb.vanish.see")) {
                sender.sendMessage(language.getMessage("minecraft.vanish.no-permission-list"));
                return true;
            }
            showVanishList(sender);
            return true;
        }

        // /vanish info - Zeige Status
        if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(language.getMessage("minecraft.vanish.player-only"));
                return true;
            }
            showVanishInfo((Player) sender);
            return true;
        }

        // /vanish <player> - Toggle für anderen Spieler
        if (args.length == 1) {
            if (!sender.hasPermission("dmb.vanish.others")) {
                sender.sendMessage(language.getMessage("minecraft.vanish.no-permission-others"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(language.getMessage("minecraft.vanish.player-not-found")
                        .replace("{player}", args[0]));
                return true;
            }

            toggleVanish(target, sender);
            return true;
        }

        // /vanish <player> <on|off> - Setze Vanish für Spieler
        if (args.length == 2) {
            if (!sender.hasPermission("dmb.vanish.others")) {
                sender.sendMessage(language.getMessage("minecraft.vanish.no-permission-others"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(language.getMessage("minecraft.vanish.player-not-found")
                        .replace("{player}", args[0]));
                return true;
            }

            boolean state;
            if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("an")) {
                state = true;
            } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("aus")) {
                state = false;
            } else {
                sender.sendMessage(language.getMessage("minecraft.vanish.usage"));
                return true;
            }

            if (vanishManager.isVanished(target) == state) {
                sender.sendMessage(language.getMessage("minecraft.vanish.already-" + (state ? "enabled" : "disabled"))
                        .replace("{player}", target.getName()));
                return true;
            }

            setVanish(target, state, sender);
            return true;
        }

        sender.sendMessage(language.getMessage("minecraft.vanish.usage"));
        return true;
    }

    private void toggleVanish(Player p, CommandSender executor) {
        boolean newState = !vanishManager.isVanished(p);
        setVanish(p, newState, executor);
    }

    private void setVanish(Player p, boolean state, CommandSender executor) {
        vanishManager.setVanish(p, state);

        if (state) {
            // Vanish AKTIVIERT
            p.sendMessage("");
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.header"));
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.title"));
            p.sendMessage("");
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.invisible"));
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.hidden"));
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.fake-quit"));

            int canSee = vanishManager.getPlayersWhoCanSee(p);
            if (canSee > 0) {
                p.sendMessage(language.getMessage("minecraft.vanish.enabled.team-can-see")
                        .replace("{count}", String.valueOf(canSee)));
            }

            p.sendMessage("");
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.footer"));
            p.sendMessage(language.getMessage("minecraft.vanish.enabled.header"));
            p.sendMessage("");

            // Benachrichtige Executor falls anders
            if (!executor.equals(p)) {
                executor.sendMessage(language.getMessage("minecraft.vanish.enabled-by-other")
                        .replace("{player}", p.getName()));
            }

        } else {
            // Vanish DEAKTIVIERT
            p.sendMessage("");
            p.sendMessage(language.getMessage("minecraft.vanish.disabled.header"));
            p.sendMessage(language.getMessage("minecraft.vanish.disabled.title"));
            p.sendMessage("");
            p.sendMessage(language.getMessage("minecraft.vanish.disabled.visible"));
            p.sendMessage(language.getMessage("minecraft.vanish.disabled.shown"));
            p.sendMessage(language.getMessage("minecraft.vanish.disabled.fake-join"));
            p.sendMessage("");
            p.sendMessage(language.getMessage("minecraft.vanish.disabled.header"));
            p.sendMessage("");

            // Benachrichtige Executor falls anders
            if (!executor.equals(p)) {
                executor.sendMessage(language.getMessage("minecraft.vanish.disabled-by-other")
                        .replace("{player}", p.getName()));
            }
        }

        Bukkit.getLogger().info("[Vanish] " + p.getName() + " Vanish " +
                (state ? "aktiviert" : "deaktiviert") +
                " von " + executor.getName());
    }

    private void showVanishList(CommandSender sender) {
        List<Player> vanished = vanishManager.getVanishedPlayers();

        if (vanished.isEmpty()) {
            sender.sendMessage(language.getMessage("minecraft.vanish.list.empty"));
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(language.getMessage("minecraft.vanish.list.header")
                .replace("{count}", String.valueOf(vanished.size())));
        sender.sendMessage("");

        for (Player p : vanished) {
            long duration = vanishManager.getVanishDuration(p);
            String timeStr = formatDuration(duration);

            sender.sendMessage(language.getMessage("minecraft.vanish.list.entry")
                    .replace("{player}", p.getName())
                    .replace("{time}", timeStr));
        }

        sender.sendMessage("");
        sender.sendMessage(language.getMessage("minecraft.vanish.list.total")
                .replace("{count}", String.valueOf(vanished.size())));
        sender.sendMessage(language.getMessage("minecraft.vanish.list.footer"));
        sender.sendMessage("");
    }

    private void showVanishInfo(Player p) {
        boolean vanished = vanishManager.isVanished(p);

        p.sendMessage("");
        p.sendMessage(language.getMessage("minecraft.vanish.info.header"));
        p.sendMessage("");

        String statusKey = vanished ? "minecraft.vanish.info.status-vanished" : "minecraft.vanish.info.status-visible";
        p.sendMessage(language.getMessage(statusKey));

        if (vanished) {
            long duration = vanishManager.getVanishDuration(p);
            String timeStr = formatDuration(duration);
            p.sendMessage(language.getMessage("minecraft.vanish.info.duration")
                    .replace("{time}", timeStr));

            int canSee = vanishManager.getPlayersWhoCanSee(p);
            p.sendMessage(language.getMessage("minecraft.vanish.info.can-see")
                    .replace("{count}", String.valueOf(canSee)));
        }

        p.sendMessage(language.getMessage("minecraft.vanish.info.permission-vanish")
                .replace("{status}", p.hasPermission("dmb.vanish") ? "✓" : "✗"));
        p.sendMessage(language.getMessage("minecraft.vanish.info.permission-others")
                .replace("{status}", p.hasPermission("dmb.vanish.others") ? "✓" : "✗"));
        p.sendMessage(language.getMessage("minecraft.vanish.info.permission-see")
                .replace("{status}", p.hasPermission("dmb.vanish.see") ? "✓" : "✗"));

        p.sendMessage("");
        p.sendMessage(language.getMessage("minecraft.vanish.info.footer"));
        p.sendMessage("");
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";

        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";

        long hours = minutes / 60;
        if (hours < 24) return hours + "h";

        long days = hours / 24;
        return days + "d";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Erste Argument - Spieler oder Befehle
            if (sender.hasPermission("dmb.vanish.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }

            // Füge Befehle hinzu
            if ("list".startsWith(args[0].toLowerCase()) && sender.hasPermission("dmb.vanish.see")) {
                completions.add("list");
            }
            if ("info".startsWith(args[0].toLowerCase()) && sender.hasPermission("dmb.vanish")) {
                completions.add("info");
            }

        } else if (args.length == 2 && sender.hasPermission("dmb.vanish.others")) {
            // Zweites Argument - on/off
            completions.add("on");
            completions.add("off");
            completions.add("an");
            completions.add("aus");
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}