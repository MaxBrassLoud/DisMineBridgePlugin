package de.MaxBrassLoud.disMineBridgePlugin.listener;

import de.MaxBrassLoud.disMineBridgePlugin.voicechat.VoiceChatMutePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final MuteListener muteListener;
    private VoiceChatMutePlugin voiceChatPlugin;

    public PlayerQuitListener(MuteListener muteListener) {
        this.muteListener = muteListener;
    }

    public void setVoiceChatPlugin(VoiceChatMutePlugin voiceChatPlugin) {
        this.voiceChatPlugin = voiceChatPlugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cleanup für MuteListener
        muteListener.cleanup(event.getPlayer().getUniqueId());

        // Cleanup für VoiceChat falls vorhanden
        if (voiceChatPlugin != null) {
            voiceChatPlugin.cleanup(event.getPlayer().getUniqueId());
        }
    }
}