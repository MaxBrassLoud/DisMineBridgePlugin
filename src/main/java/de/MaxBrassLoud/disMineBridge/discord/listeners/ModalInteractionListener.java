package de.MaxBrassLoud.disMineBridge.discord.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModalInteractionListener extends ListenerAdapter {

    private final DisMineBridge plugin;

    public ModalInteractionListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        LanguageManager lang = plugin.getLanguageManager();

        if (modalId.startsWith("ticket_create_support") || modalId.startsWith("ticket_create_bug-report")) {
            handleTicketCreation(event, modalId.replace("ticket_create_", ""));
        } else if (modalId.startsWith("ticket_create_whitelist")) {
            handleWhitelistCreation(event);
        } else if (modalId.startsWith("ticket_adduser_")) {
            handleAddUserToTicket(event, modalId);
        } else if (modalId.startsWith("whitelist_deny_")) {
            handleWhitelistDeny(event, modalId);
        }
    }

    private void handleTicketCreation(ModalInteractionEvent event, String ticketType) {
        LanguageManager lang = plugin.getLanguageManager();

        String description = event.getValue("description").getAsString();

        try {
            int ticketId = plugin.getTicketManager().createTicket(
                    event.getUser().getId(),
                    ticketType,
                    description,
                    null,
                    null
            );

            TextChannel channel = plugin.getTicketManager().createTicketChannel(
                    event.getGuild(),
                    event.getUser(),
                    ticketId,
                    ticketType
            );

            if (channel == null) {
                event.reply(lang.getMessage("error.unknown")).setEphemeral(true).queue();
                return;
            }

            plugin.getTicketManager().sendTicketMessage(
                    channel,
                    ticketId,
                    event.getUser(),
                    ticketType,
                    description,
                    null
            );

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(lang.getMessage("discord.ticket.created.title"));
            embed.setDescription(lang.getMessage("discord.ticket.created.description")
                    .replace("{channel}", channel.getAsMention()));
            embed.setColor(Color.GREEN);

            event.replyEmbeds(embed.build()).setEphemeral(true).queue();

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    private void handleWhitelistCreation(ModalInteractionEvent event) {
        LanguageManager lang = plugin.getLanguageManager();

        String minecraftName = event.getValue("minecraft_name").getAsString();

        // Zusätzliche Felder sammeln
        Map<String, String> additionalData = new HashMap<>();
        for (ModalMapping mapping : event.getValues()) {
            if (!mapping.getId().equals("minecraft_name")) {
                additionalData.put(mapping.getId(), mapping.getAsString());
            }
        }

        try {
            plugin.getWhitelistManager().createWhitelistRequest(
                    event.getUser().getId(),
                    minecraftName,
                    additionalData
            );


            String whitelistChannelId = plugin.getConfig().getString("discord.whitelist-channel");
            if (whitelistChannelId != null) {
                TextChannel channel = event.getGuild().getTextChannelById(whitelistChannelId);
                if (channel != null) {
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle(lang.getMessage("discord.whitelist.request.title"));
                    embed.addField(lang.getMessage("discord.whitelist.request.user"),
                            event.getUser().getAsMention(), false);
                    embed.addField(lang.getMessage("discord.whitelist.request.minecraft-name"),
                            minecraftName, false);

                    if (!additionalData.isEmpty()) {
                        StringBuilder info = new StringBuilder();
                        for (Map.Entry<String, String> entry : additionalData.entrySet()) {
                            String label = plugin.getConfig().getString(
                                    "whitelist.additional-fields.fields." + entry.getKey() + ".label",
                                    entry.getKey()
                            );
                            info.append("**").append(label).append(":** ")
                                    .append(entry.getValue()).append("\n");
                        }
                        embed.addField(lang.getMessage("discord.whitelist.request.additional-info"),
                                info.toString(), false);
                    }

                    embed.addField(lang.getMessage("discord.whitelist.request.status"),
                            lang.getMessage("discord.whitelist.status.pending"), false);
                    embed.setColor(Color.ORANGE);
                    embed.setTimestamp(java.time.Instant.now());

                    channel.sendMessageEmbeds(embed.build())
                            .addActionRow(
                                    Button.success("whitelist_approve_" + event.getUser().getId(),
                                                    lang.getMessage("discord.whitelist.button.approve"))
                                            .withEmoji(Emoji.fromUnicode("✅")),
                                    Button.danger("whitelist_deny_" + event.getUser().getId(),
                                                    lang.getMessage("discord.whitelist.button.deny"))
                                            .withEmoji(Emoji.fromUnicode("❌"))
                            )
                            .queue();
                }
            }

            // DM an User
            EmbedBuilder dm = new EmbedBuilder();
            dm.setTitle(lang.getMessage("discord.whitelist.dm.request-sent-title"));
            dm.setDescription(lang.getMessage("discord.whitelist.dm.request-sent-description"));
            dm.setColor(Color.BLUE);

            event.getUser().openPrivateChannel().queue(
                    privateChannel -> privateChannel.sendMessageEmbeds(dm.build()).queue()
            );

            event.reply(lang.getMessage("discord.whitelist.dm.request-sent-title"))
                    .setEphemeral(true).queue();

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    private void handleAddUserToTicket(ModalInteractionEvent event, String modalId) {
        int ticketId = Integer.parseInt(modalId.replace("ticket_adduser_", ""));
        LanguageManager lang = plugin.getLanguageManager();

        String userInput = event.getValue("user_id").getAsString();

        // User ID extrahieren (kann @mention oder direkte ID sein)
        String userId = userInput.replaceAll("[<@!>]", "");

        try {
            User user = plugin.getDiscordBot().getJDA().retrieveUserById(userId).complete();

            if (user != null && event.getChannel() instanceof TextChannel) {
                TextChannel channel = (TextChannel) event.getChannel();

                channel.upsertPermissionOverride((IPermissionHolder) user)
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                                Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES)
                        .queue();

                // In DB speichern
                int internalUserId = plugin.getDatabaseManager().createOrGetUser(userId);
                // TODO: In ticket_participants einfügen

                event.reply(lang.getMessage("discord.ticket.add-user.success")
                                .replace("{user}", user.getAsMention()))
                        .setEphemeral(true).queue();

                channel.sendMessage(user.getAsMention() + " wurde zum Ticket hinzugefügt!").queue();
            } else {
                event.reply(lang.getMessage("discord.ticket.add-user.error"))
                        .setEphemeral(true).queue();
            }
        } catch (Exception e) {
            event.reply(lang.getMessage("discord.ticket.add-user.error"))
                    .setEphemeral(true).queue();
        }
    }

    private void handleWhitelistDeny(ModalInteractionEvent event, String modalId) {
        String discordId = modalId.replace("whitelist_deny_", "");
        LanguageManager lang = plugin.getLanguageManager();

        String reason = event.getValue("reason").getAsString();

        try {
            // Minecraft Name holen
            String minecraftName = "Unknown";
            try {
                var conn = plugin.getDatabaseManager().getConnection();
                var stmt = conn.prepareStatement("SELECT minecraft_name FROM users WHERE discord_id = ?");
                stmt.setString(1, discordId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    minecraftName = rs.getString("minecraft_name");
                }
                rs.close();
                stmt.close();
            } catch (Exception ignored) {}

            plugin.getWhitelistManager().denyWhitelistRequest(discordId, reason);

            int cooldownDays = plugin.getConfig().getInt("whitelist.cooldown-after-rejection", 7);

            event.reply(lang.getMessage("discord.whitelist.deny.success")).setEphemeral(true).queue();

            // ✅ NEU: Finde und update die ursprüngliche Whitelist-Anfrage Message
            updateWhitelistEmbedOnDeny(event, discordId, minecraftName, reason, lang);

            // DM an User
            User user = plugin.getDiscordBot().getJDA().retrieveUserById(discordId).complete();
            if (user != null) {
                EmbedBuilder dm = new EmbedBuilder();
                dm.setTitle(lang.getMessage("discord.whitelist.deny.dm-title"));
                dm.setDescription(lang.getMessage("discord.whitelist.deny.dm-description")
                        .replace("{name}", minecraftName)
                        .replace("{reason}", reason)
                        .replace("{days}", String.valueOf(cooldownDays)));
                dm.setColor(Color.RED);

                user.openPrivateChannel().queue(channel -> channel.sendMessageEmbeds(dm.build()).queue());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    /**
     * ✅ NEU: Update das Whitelist-Embed nach Deny
     */
    private void updateWhitelistEmbedOnDeny(ModalInteractionEvent event, String discordId,
                                            String minecraftName, String reason, LanguageManager lang) {
        String whitelistChannelId = plugin.getConfig().getString("discord.whitelist-channel");
        if (whitelistChannelId == null) return;

        TextChannel whitelistChannel = event.getGuild().getTextChannelById(whitelistChannelId);
        if (whitelistChannel == null) return;

        // Suche nach der Message mit den Buttons für diesen User
        whitelistChannel.getIterableHistory().takeAsync(100).thenAccept(messages -> {
            for (var message : messages) {
                // Prüfe ob Message Buttons für diesen Discord-User hat
                if (!message.getButtons().isEmpty()) {
                    for (var button : message.getButtons()) {
                        if (button.getId() != null && button.getId().contains(discordId)) {
                            // Gefunden! Update diese Message
                            if (!message.getEmbeds().isEmpty()) {
                                EmbedBuilder updatedEmbed = new EmbedBuilder(message.getEmbeds().get(0));

                                // Setze Farbe auf ROT
                                updatedEmbed.setColor(Color.RED);

                                // Update Felder
                                updatedEmbed.clearFields();
                                updatedEmbed.addField(lang.getMessage("discord.whitelist.request.user"),
                                        "<@" + discordId + ">", false);
                                updatedEmbed.addField(lang.getMessage("discord.whitelist.request.minecraft-name"),
                                        minecraftName, false);
                                updatedEmbed.addField(lang.getMessage("discord.whitelist.request.status"),
                                        "❌ " + lang.getMessage("discord.whitelist.status.denied"), false);
                                updatedEmbed.addField("Abgelehnt von",
                                        event.getUser().getName(), false);
                                updatedEmbed.addField("Grund",
                                        reason, false);
                                updatedEmbed.setTimestamp(Instant.now());

                                // Disable alle Buttons
                                List<Button> disabledButtons = new ArrayList<>();
                                disabledButtons.add(Button.success("whitelist_approve_disabled",
                                                lang.getMessage("discord.whitelist.button.approve"))
                                        .withEmoji(Emoji.fromUnicode("✅"))
                                        .asDisabled());
                                disabledButtons.add(Button.danger("whitelist_deny_disabled",
                                                lang.getMessage("discord.whitelist.button.deny"))
                                        .withEmoji(Emoji.fromUnicode("❌"))
                                        .asDisabled());

                                // Update die Message
                                message.editMessageEmbeds(updatedEmbed.build())
                                        .setComponents(ActionRow.of(disabledButtons))
                                        .queue();
                            }
                            return; // Gefunden und updated, fertig
                        }
                    }
                }
            }
        });
    }
}