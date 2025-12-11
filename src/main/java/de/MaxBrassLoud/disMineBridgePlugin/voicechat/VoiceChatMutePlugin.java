package de.MaxBrassLoud.disMineBridgePlugin.voicechat;

import de.MaxBrassLoud.disMineBridgePlugin.DisMineBridgePlugin;
import de.MaxBrassLoud.disMineBridgePlugin.listener.MuteListener;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VoiceChatMutePlugin implements VoicechatPlugin {

    private final DisMineBridgePlugin plugin;
    private final MuteListener muteListener;
    private final Map<UUID, Long> lastMuteWarning = new HashMap<>();
    private final Map<UUID, Integer> muteAttempts = new HashMap<>();

    private static final long WARNING_COOLDOWN = 5000; // 5 Sekunden
    private static final int MAX_ATTEMPTS_BEFORE_WARNING = 3;

    public VoiceChatMutePlugin(DisMineBridgePlugin plugin, MuteListener muteListener) {
        this.plugin = plugin;
        this.muteListener = muteListener;
    }

    @Override
    public String getPluginId() {
        return "disminebridge_mute";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        plugin.getLogger().info("[DisMineBridge] VoiceChat-Events erfolgreich registriert!");
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        UUID senderUuid = event.getSenderConnection().getPlayer().getUuid();

        // Prüfe ob Spieler gemutet ist
        MuteListener.MuteInfo muteInfo = muteListener.getMuteInfo(senderUuid);

        if (muteInfo != null && muteInfo.isMuted) {
            // Blockiere das Mikrofon-Paket
            event.cancel();

            // Tracke Versuche zu sprechen
            int attempts = muteAttempts.getOrDefault(senderUuid, 0) + 1;
            muteAttempts.put(senderUuid, attempts);

            Player player = plugin.getServer().getPlayer(senderUuid);
            if (player != null && player.isOnline()) {
                long currentTime = System.currentTimeMillis();
                Long lastWarning = lastMuteWarning.get(senderUuid);

                // Sende Warnung nur alle 5 Sekunden
                if (lastWarning == null || currentTime - lastWarning > WARNING_COOLDOWN) {
                    sendVoiceMuteWarning(player, muteInfo, attempts);
                    lastMuteWarning.put(senderUuid, currentTime);
                }
            }
        } else {
            // Spieler ist nicht mehr gemutet - cleanup
            muteAttempts.remove(senderUuid);
            lastMuteWarning.remove(senderUuid);
        }
    }

    private void sendVoiceMuteWarning(Player player, MuteListener.MuteInfo info, int attempts) {
        // Erste Warnung - detailliert
        if (attempts <= MAX_ATTEMPTS_BEFORE_WARNING) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "    VOICE-CHAT BLOCKIERT");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "  Du bist gemutet und kannst nicht sprechen!");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "  Grund: " + ChatColor.WHITE + info.reason);
            player.sendMessage(ChatColor.GRAY + "  Verbleibend: " + ChatColor.YELLOW + info.remainingTime);
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("");
        } else {
            // Nach mehreren Versuchen - kompakte Warnung
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "Du bist gemutet! " +
                    ChatColor.YELLOW + info.remainingTime + ChatColor.GRAY + " verbleibend");
        }

        // Log für Admins
        plugin.getLogger().info("[VoiceChat] " + player.getName() +
                " versuchte zu sprechen während gemutet (" + attempts + " Versuche)");
    }

    /**
     * Cleanup wenn Spieler den Server verlässt
     */
    public void cleanup(UUID uuid) {
        lastMuteWarning.remove(uuid);
        muteAttempts.remove(uuid);
    }
}