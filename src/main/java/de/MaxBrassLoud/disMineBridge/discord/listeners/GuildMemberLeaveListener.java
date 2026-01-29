package de.MaxBrassLoud.disMineBridge.discord.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildMemberLeaveListener extends ListenerAdapter {

    private final DisMineBridge plugin;

    public GuildMemberLeaveListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String discordId = event.getUser().getId();

        // Asynchron verarbeiten
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getWhitelistManager().handleDiscordLeave(discordId);
        });
    }
}