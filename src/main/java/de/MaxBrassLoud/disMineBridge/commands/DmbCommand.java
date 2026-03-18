package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.features.web.WebPermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /dmb <subcommand> [args...]
 *
 * Subcommands:
 *   webpermission grant <Spieler>   – Gibt einem Spieler Zugang zum Web-Dashboard
 *   webpermission remove <Spieler>  – Entzieht den Zugang
 */
public class DmbCommand implements CommandExecutor, TabCompleter {

    private final DisMineBridge plugin;
    private final WebPermissionManager webPermissions;

    private static final String PERM_WEBPERMISSION = "disminebridge.webpermission";

    public DmbCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.webPermissions = plugin.getWebPermissionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "webpermission", "webperm", "wp" -> handleWebPermission(sender, args);
            default -> {
                sender.sendMessage("§c[DMB] Unbekannter Subbefehl: §e" + sub);
                sendUsage(sender);
            }
        }
        return true;
    }

    // ────────────────────────────────────────────────
    //  /dmb webpermission [grant|remove] <Spieler>
    // ────────────────────────────────────────────────

    private void handleWebPermission(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_WEBPERMISSION)) {
            sender.sendMessage("§cDu hast keine Berechtigung für diesen Befehl!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cBenutzung: §e/dmb webpermission <grant|remove> <Spieler>");
            return;
        }

        String action = args[1].toLowerCase();
        String targetName = args[2];

        if (!action.equals("grant") && !action.equals("remove")) {
            sender.sendMessage("§cAktion muss §egrant §coder §eremove §csein.");
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cSpieler §e" + targetName + " §cwurde nie auf dem Server gesehen!");
            return;
        }

        String grantedBy = (sender instanceof Player p) ? p.getName() : "Konsole";
        // getName() kann bei OfflinePlayer null sein – Fallback auf targetName
        String resolvedName = target.getName() != null ? target.getName() : targetName;

        switch (action) {
            case "grant" -> {
                // Prüfe Discord-Link mit UUID UND Name als Fallback
                if (!webPermissions.hasDiscordLink(target.getUniqueId(), resolvedName)) {
                    sender.sendMessage("§c§e" + targetName + " §chat keinen verknüpften Discord-Account!");
                    sender.sendMessage("§7Der Spieler muss zunächst seinen Discord-Account über Discord verknüpfen.");
                    return;
                }
                boolean success = webPermissions.grantPermission(
                        target.getUniqueId(), resolvedName, grantedBy);
                if (success) {
                    sender.sendMessage("§a✓ Web-Dashboard-Zugang für §e" + targetName + " §agewährt!");
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.sendMessage("§a✓ Dir wurde Zugang zum §eWeb-Dashboard §agewährt!");
                        online.sendMessage("§7Du kannst dich jetzt über Discord einloggen.");
                    }
                } else {
                    sender.sendMessage("§c✘ Fehler beim Gewähren des Zugangs. Siehe Konsole.");
                }
            }
            case "remove" -> {
                // Revoke mit UUID UND Name als Fallback
                boolean success = webPermissions.revokePermission(
                        target.getUniqueId(), resolvedName);
                if (success) {
                    sender.sendMessage("§a✓ Web-Dashboard-Zugang für §e" + targetName + " §aentfernt!");
                } else {
                    sender.sendMessage("§c✘ §e" + targetName + " §chat keinen Web-Zugang oder Fehler aufgetreten.");
                }
            }
        }
    }

    // ────────────────────────────────────────────────
    //  Usage
    // ────────────────────────────────────────────────

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━━━ §eDMB BEFEHLE §6§l━━━━━━━");
        sender.sendMessage("§e/dmb webpermission grant <Spieler>  §7– Web-Zugang gewähren");
        sender.sendMessage("§e/dmb webpermission remove <Spieler> §7– Web-Zugang entziehen");
    }

    // ────────────────────────────────────────────────
    //  Tab Completion
    // ────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("webpermission");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("webpermission")) {
            return List.of("grant", "remove");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("webpermission")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}