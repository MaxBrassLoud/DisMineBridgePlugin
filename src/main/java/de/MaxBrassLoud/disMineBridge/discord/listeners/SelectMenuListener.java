package de.MaxBrassLoud.disMineBridge.discord.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
import java.sql.*;

public class SelectMenuListener extends ListenerAdapter {

    private final DisMineBridge plugin;

    public SelectMenuListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String menuId = event.getComponentId();
        LanguageManager lang = plugin.getLanguageManager();

        if (menuId.equals("setup_panel_channel")) {
            String channelId = event.getMentions().getChannels().get(0).getId();

            // In Datenbank speichern
            saveSetupValue(event.getGuild().getId(), "panel_channel_id", channelId);

            // Ticket Panel erstellen
            createTicketPanel(event.getGuild().getTextChannelById(channelId));

            event.reply(lang.getMessage("discord.setup.success"))
                    .setEphemeral(true).queue();
        }
    }

    private void saveSetupValue(String guildId, String column, String value) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Check if exists
            String check = "SELECT guild_id FROM ticket_setup WHERE guild_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(check);
            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Update
                String update = "UPDATE ticket_setup SET " + column + " = ?, updated_at = ? WHERE guild_id = ?";
                PreparedStatement stmt = conn.prepareStatement(update);
                stmt.setString(1, value);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, guildId);
                stmt.executeUpdate();
                stmt.close();
            } else {
                // Insert
                String insert = "INSERT INTO ticket_setup (guild_id, " + column + ", updated_at) VALUES (?, ?, ?)";
                PreparedStatement stmt = conn.prepareStatement(insert);
                stmt.setString(1, guildId);
                stmt.setString(2, value);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
                stmt.close();
            }

            rs.close();
            checkStmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTicketPanel(TextChannel channel) {
        if (channel == null) return;

        LanguageManager lang = plugin.getLanguageManager();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(lang.getMessage("discord.panel.title"));
        embed.setDescription(lang.getMessage("discord.panel.description"));
        embed.setColor(Color.CYAN);

        Button supportButton = Button.success("create_ticket_support",
                lang.getMessage("discord.panel.button-support"));
        Button bugButton = Button.danger("create_ticket_bug",
                lang.getMessage("discord.panel.button-bug"));
        Button whitelistButton = Button.primary("create_ticket_whitelist",
                lang.getMessage("discord.panel.button-whitelist"));

        channel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(supportButton, bugButton, whitelistButton))
                .queue(message -> {
                    // Message ID speichern
                    saveSetupValue(channel.getGuild().getId(), "panel_message_id", message.getId());
                });
    }
}