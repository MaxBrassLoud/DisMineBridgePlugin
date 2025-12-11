package de.MaxBrassLoud.disMineBridgePlugin.command;

import de.MaxBrassLoud.disMineBridgePlugin.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class vanish implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /vanish ohne Argumente - Toggle für sich selbst
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Nur Spieler können /vanish ohne Argument nutzen.");
                return true;
            }

            Player p = (Player) sender;

            if (!p.hasPermission("dmb.vanish")) {
                p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
                return true;
            }

            toggleVanish(p, sender);
            return true;
        }

        // /vanish list - Zeige alle im Vanish
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("liste")) {
            if (!sender.hasPermission("dmb.vanish.see")) {
                sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, die Vanish-Liste zu sehen.");
                return true;
            }
            showVanishList(sender);
            return true;
        }

        // /vanish info - Zeige Status
        if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl nutzen.");
                return true;
            }
            showVanishInfo((Player) sender);
            return true;
        }

        // /vanish <player> - Toggle für anderen Spieler
        if (args.length == 1) {
            if (!sender.hasPermission("dmb.vanish.others")) {
                sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, andere Spieler zu vanishen.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Spieler " + ChatColor.YELLOW + args[0] +
                        ChatColor.RED + " ist nicht online.");
                return true;
            }

            toggleVanish(target, sender);
            return true;
        }

        // /vanish <player> <on|off> - Setze Vanish für Spieler
        if (args.length == 2) {
            if (!sender.hasPermission("dmb.vanish.others")) {
                sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, andere Spieler zu vanishen.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Spieler " + ChatColor.YELLOW + args[0] +
                        ChatColor.RED + " ist nicht online.");
                return true;
            }

            boolean state;
            if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("an")) {
                state = true;
            } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("aus")) {
                state = false;
            } else {
                sender.sendMessage(ChatColor.RED + "Nutze: /vanish <Spieler> <on|off>");
                return true;
            }

            if (VanishManager.isVanished(target) == state) {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + ChatColor.GRAY + " ist bereits " +
                        (state ? "im Vanish" : "sichtbar") + ".");
                return true;
            }

            setVanish(target, state, sender);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Nutze: /vanish [Spieler] [on|off|list|info]");
        return true;
    }

    private void toggleVanish(Player p, CommandSender executor) {
        boolean newState = !VanishManager.isVanished(p);
        setVanish(p, newState, executor);
    }

    private void setVanish(Player p, boolean state, CommandSender executor) {
        VanishManager.setVanish(p, state);

        if (state) {
            // Vanish AKTIVIERT
            p.sendMessage("");
            p.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "          VANISH AKTIVIERT");
            p.sendMessage("");
            p.sendMessage(ChatColor.GRAY + "  » Du bist nun " + ChatColor.RED + ChatColor.BOLD + "UNSICHTBAR");
            p.sendMessage(ChatColor.GRAY + "  » Andere Spieler können dich nicht mehr sehen");
            p.sendMessage(ChatColor.GRAY + "  » Eine Fake-Quit-Nachricht wurde gesendet");

            int canSee = VanishManager.getPlayersWhoCanSee(p);
            if (canSee > 0) {
                p.sendMessage(ChatColor.GRAY + "  » " + ChatColor.YELLOW + canSee +
                        ChatColor.GRAY + " Team-Mitglieder können dich sehen");
            }

            p.sendMessage("");
            p.sendMessage(ChatColor.DARK_GRAY + "  Nutze " + ChatColor.RED + "/vanish" +
                    ChatColor.DARK_GRAY + " erneut zum Deaktivieren");
            p.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            p.sendMessage("");

            // Benachrichtige Executor falls anders
            if (!executor.equals(p)) {
                executor.sendMessage(ChatColor.GRAY + "Vanish für " + ChatColor.YELLOW + p.getName() +
                        ChatColor.GRAY + " wurde " + ChatColor.RED + "aktiviert" + ChatColor.GRAY + ".");
            }

        } else {
            // Vanish DEAKTIVIERT
            p.sendMessage("");
            p.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            p.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "          VANISH DEAKTIVIERT");
            p.sendMessage("");
            p.sendMessage(ChatColor.GRAY + "  » Du bist nun " + ChatColor.GREEN + ChatColor.BOLD + "SICHTBAR");
            p.sendMessage(ChatColor.GRAY + "  » Alle Spieler können dich wieder sehen");
            p.sendMessage(ChatColor.GRAY + "  » Eine Fake-Join-Nachricht wurde gesendet");
            p.sendMessage("");
            p.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            p.sendMessage("");

            // Benachrichtige Executor falls anders
            if (!executor.equals(p)) {
                executor.sendMessage(ChatColor.GRAY + "Vanish für " + ChatColor.YELLOW + p.getName() +
                        ChatColor.GRAY + " wurde " + ChatColor.GREEN + "deaktiviert" + ChatColor.GRAY + ".");
            }
        }

        Bukkit.getLogger().info("[Vanish] " + p.getName() + " Vanish " +
                (state ? "aktiviert" : "deaktiviert") +
                " von " + executor.getName());
    }

    private void showVanishList(CommandSender sender) {
        List<Player> vanished = VanishManager.getVanishedPlayers();

        if (vanished.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Keine Spieler sind im Vanish.");
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "▬▬▬▬▬▬ " + ChatColor.BOLD + "VANISH LISTE (" + vanished.size() + ")" +
                ChatColor.RESET + ChatColor.RED + " ▬▬▬▬▬▬");
        sender.sendMessage("");

        for (Player p : vanished) {
            long duration = VanishManager.getVanishDuration(p);
            String timeStr = formatDuration(duration);

            sender.sendMessage(ChatColor.GRAY + "  » " + ChatColor.YELLOW + p.getName() +
                    ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + "seit " + timeStr);
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Insgesamt: " + ChatColor.YELLOW + vanished.size() +
                ChatColor.GRAY + " Spieler im Vanish");
        sender.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private void showVanishInfo(Player p) {
        boolean vanished = VanishManager.isVanished(p);

        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬ " + ChatColor.BOLD + "VANISH INFO" +
                ChatColor.RESET + ChatColor.GOLD + " ▬▬▬▬▬▬");
        p.sendMessage("");
        p.sendMessage(ChatColor.YELLOW + "Status: " + (vanished ?
                ChatColor.RED + "✖ Im Vanish" : ChatColor.GREEN + "✔ Sichtbar"));

        if (vanished) {
            long duration = VanishManager.getVanishDuration(p);
            String timeStr = formatDuration(duration);
            p.sendMessage(ChatColor.YELLOW + "Im Vanish seit: " + ChatColor.WHITE + timeStr);

            int canSee = VanishManager.getPlayersWhoCanSee(p);
            p.sendMessage(ChatColor.YELLOW + "Können dich sehen: " + ChatColor.WHITE + canSee + " Spieler");
        }

        p.sendMessage(ChatColor.YELLOW + "Permission: " + (p.hasPermission("dmb.vanish") ?
                ChatColor.GREEN + "✔" : ChatColor.RED + "✖"));
        p.sendMessage(ChatColor.YELLOW + "Andere vanishen: " + (p.hasPermission("dmb.vanish.others") ?
                ChatColor.GREEN + "✔" : ChatColor.RED + "✖"));
        p.sendMessage(ChatColor.YELLOW + "Vanish sehen: " + (p.hasPermission("dmb.vanish.see") ?
                ChatColor.GREEN + "✔" : ChatColor.RED + "✖"));

        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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