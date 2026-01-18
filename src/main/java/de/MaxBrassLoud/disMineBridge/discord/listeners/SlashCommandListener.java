package de.MaxBrassLoud.disMineBridge.discord.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;

import java.awt.*;

public class SlashCommandListener extends ListenerAdapter {

    private final DisMineBridge plugin;

    public SlashCommandListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("ticketsetup")) {
            handleTicketSetup(event);
        }
    }

    private void handleTicketSetup(SlashCommandInteractionEvent event) {
        var lang = plugin.getLanguageManager();

        // Permission Check
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply(lang.getMessage("discord.setup.no-permission"))
                    .setEphemeral(true).queue();
            return;
        }

        // Setup Menu erstellen
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(lang.getMessage("discord.setup.title"));
        embed.setDescription(lang.getMessage("discord.setup.description"));
        embed.setColor(Color.BLUE);

        // Channel Select für Ticket Panel
        EntitySelectMenu panelChannelSelect = EntitySelectMenu.create("setup_panel_channel",
                        EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder(lang.getMessage("discord.setup.select-panel-channel"))
                .setRequiredRange(1, 1)
                .build();

        event.replyEmbeds(embed.build())
                .addActionRow(panelChannelSelect)
                .setEphemeral(true)
                .queue();
    }
}