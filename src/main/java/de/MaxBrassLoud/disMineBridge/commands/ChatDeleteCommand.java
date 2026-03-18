package de.MaxBrassLoud.disMineBridge.commands;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.ChatManager;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /chatdelete <msgId>
 *
 * Wird von Moderatoren genutzt, indem sie auf den [X]-Button
 * hinter einer Chat-Nachricht klicken (oder den Befehl manuell eingeben).
 *
 * Löscht die angegebene Nachricht für alle Online-Spieler:
 * Da Minecraft Chat-Nachrichten nicht zurückziehen kann, sendet der Befehl
 * eine sichtbare Platzhalter-Zeile die anzeigt, dass eine Nachricht entfernt wurde.
 */
public class ChatDeleteCommand implements CommandExecutor {

    private final DisMineBridge plugin;
    private final LanguageManager lang;

    public ChatDeleteCommand(DisMineBridge plugin) {
        this.plugin = plugin;
        this.lang   = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Nur Spieler mit Moderator-Berechtigung
        if (!sender.hasPermission("dmb.chat.moderate") && !sender.hasPermission("dmb.admin")) {
            sender.sendMessage(lang.getMessage("minecraft.chat.delete-no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(lang.getMessage("minecraft.chat.delete-usage"));
            return true;
        }

        // UUID parsen
        UUID msgId;
        try {
            msgId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(lang.getMessage("minecraft.chat.delete-invalid-id"));
            return true;
        }

        // Nachricht löschen
        Player moderator = sender instanceof Player p ? p : null;
        if (moderator == null) {
            // Konsole kann auch löschen – erstelle einen Dummy-Proxy
            // Dafür rufen wir direkt den ChatManager auf
            ChatManager.ChatEntry entry = plugin.getChatManager().getEntry(msgId);
            if (entry == null || entry.deleted) {
                sender.sendMessage(lang.getMessage("minecraft.chat.delete-not-found"));
                return true;
            }
            // Konsolen-Löschung: wir übergeben null-safe Variante
            if (plugin.getChatManager().deleteMessageConsole(msgId, sender.getName())) {
                sender.sendMessage(lang.getMessage("minecraft.chat.delete-success"));
            } else {
                sender.sendMessage(lang.getMessage("minecraft.chat.delete-not-found"));
            }
            return true;
        }

        boolean success = plugin.getChatManager().deleteMessage(msgId, moderator);
        if (success) {
            sender.sendMessage(lang.getMessage("minecraft.chat.delete-success"));
        } else {
            sender.sendMessage(lang.getMessage("minecraft.chat.delete-not-found"));
        }

        return true;
    }
}