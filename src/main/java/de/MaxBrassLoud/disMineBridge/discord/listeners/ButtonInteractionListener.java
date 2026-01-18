package de.MaxBrassLoud.disMineBridge.discord.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.LanguageManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ButtonInteractionListener extends ListenerAdapter {

    private final DisMineBridge plugin;

    public ButtonInteractionListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        LanguageManager lang = plugin.getLanguageManager();

        // Ticket Panel Buttons
        if (buttonId.equals("create_ticket_support")) {
            showTicketModal(event, "support");
        } else if (buttonId.equals("create_ticket_bug")) {
            showTicketModal(event, "bug-report");
        } else if (buttonId.equals("create_ticket_whitelist")) {
            showWhitelistModal(event);
        }

        // Ticket Management Buttons
        else if (buttonId.startsWith("ticket_claim_")) {
            handleTicketClaim(event, buttonId);
        } else if (buttonId.startsWith("ticket_unclaim_")) {
            handleTicketUnclaim(event, buttonId);
        } else if (buttonId.startsWith("ticket_adduser_")) {
            handleTicketAddUser(event, buttonId);
        } else if (buttonId.startsWith("ticket_close_")) {
            handleTicketClose(event, buttonId);
        }

        // Close Confirmation Buttons
        else if (buttonId.startsWith("close_confirm_")) {
            handleCloseConfirm(event, buttonId);
        } else if (buttonId.startsWith("close_cancel_")) {
            event.getMessage().delete().queue();
            event.reply(lang.getMessage("general.cancel")).setEphemeral(true).queue();
        }

        // Whitelist Buttons
        else if (buttonId.startsWith("whitelist_approve_")) {
            handleWhitelistApprove(event, buttonId);
        } else if (buttonId.startsWith("whitelist_deny_")) {
            handleWhitelistDeny(event, buttonId);
        }
    }

    private void showTicketModal(ButtonInteractionEvent event, String ticketType) {
        LanguageManager lang = plugin.getLanguageManager();

        String title = ticketType.equals("support") ?
                lang.getMessage("discord.ticket.create.modal-title-support") :
                lang.getMessage("discord.ticket.create.modal-title-bug");

        TextInput description = TextInput.create("description",
                        lang.getMessage("discord.ticket.create.field-description"),
                        TextInputStyle.PARAGRAPH)
                .setPlaceholder(lang.getMessage("discord.ticket.create.field-description-placeholder"))
                .setRequired(true)
                .setMaxLength(1000)
                .build();

        Modal modal = Modal.create("ticket_create_" + ticketType, title)
                .addActionRow(description)
                .build();

        event.replyModal(modal).queue();
    }

    private void showWhitelistModal(ButtonInteractionEvent event) {
        LanguageManager lang = plugin.getLanguageManager();

        // Prüfen ob User bereits Whitelist hat oder Anfrage ausstehend
        if (!plugin.getWhitelistManager().canCreateWhitelistRequest(event.getUser().getId())) {
            String status = plugin.getWhitelistManager().getWhitelistStatus(event.getUser().getId());

            if ("approved".equals(status)) {
                event.reply(lang.getMessage("discord.ticket.error.already-whitelisted"))
                        .setEphemeral(true).queue();
            } else if ("pending".equals(status)) {
                event.reply(lang.getMessage("discord.ticket.error.pending-request"))
                        .setEphemeral(true).queue();
            } else {
                long cooldown = plugin.getWhitelistManager().getCooldownRemaining(event.getUser().getId());
                if (cooldown > 0) {
                    long days = cooldown / (24 * 60 * 60 * 1000);
                    event.reply(lang.getMessage("discord.ticket.error.cooldown")
                                    .replace("{time}", days + " Tage"))
                            .setEphemeral(true).queue();
                }
            }
            return;
        }

        Modal.Builder modalBuilder = Modal.create("ticket_create_whitelist",
                lang.getMessage("discord.ticket.create.modal-title-whitelist"));

        TextInput minecraftName = TextInput.create("minecraft_name",
                        lang.getMessage("discord.ticket.create.field-minecraft-name"),
                        TextInputStyle.SHORT)
                .setPlaceholder(lang.getMessage("discord.ticket.create.field-minecraft-name-placeholder"))
                .setRequired(true)
                .setMaxLength(16)
                .build();

        modalBuilder.addActionRow(minecraftName);

        // Zusätzliche Felder aus Config
        if (plugin.getConfig().getBoolean("whitelist.additional-fields.enabled", false)) {
            var fields = plugin.getConfig().getConfigurationSection("whitelist.additional-fields.fields");
            if (fields != null) {
                int fieldCount = 1;
                for (String key : fields.getKeys(false)) {
                    if (fieldCount >= 5) break; // Max 5 Felder in Modal

                    String label = plugin.getConfig().getString("whitelist.additional-fields.fields." + key + ".label");
                    boolean required = plugin.getConfig().getBoolean("whitelist.additional-fields.fields." + key + ".required", false);
                    int maxLength = plugin.getConfig().getInt("whitelist.additional-fields.fields." + key + ".max-length", 500);

                    TextInput field = TextInput.create(key, label,
                                    maxLength > 100 ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
                            .setRequired(required)
                            .setMaxLength(maxLength)
                            .build();

                    modalBuilder.addActionRow(field);
                    fieldCount++;
                }
            }
        }

        event.replyModal(modalBuilder.build()).queue();
    }

    private void handleTicketClaim(ButtonInteractionEvent event, String buttonId) {
        int ticketId = Integer.parseInt(buttonId.replace("ticket_claim_", ""));
        LanguageManager lang = plugin.getLanguageManager();

        try {
            // Prüfen ob bereits claimed
            Connection conn = plugin.getDatabaseManager().getConnection();
            String query = "SELECT claimer_id FROM tickets WHERE ticket_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, ticketId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && rs.getObject("claimer_id") != null) {
                event.reply(lang.getMessage("discord.ticket.claim.already-claimed"))
                        .setEphemeral(true).queue();
                rs.close();
                stmt.close();
                return;
            }
            rs.close();
            stmt.close();

            // Ticket claimen
            plugin.getTicketManager().claimTicket(ticketId, event.getUser().getId());

            // Nachricht updaten
            Message message = event.getMessage();
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.secondary("ticket_unclaim_" + ticketId,
                    lang.getMessage("discord.ticket.button.unclaim")));
            buttons.add(Button.primary("ticket_adduser_" + ticketId,
                    lang.getMessage("discord.ticket.button.add-user")));
            buttons.add(Button.danger("ticket_close_" + ticketId,
                    lang.getMessage("discord.ticket.button.close")));

            message.editMessageComponents(ActionRow.of(buttons)).queue();

            // Embed updaten
            if (!message.getEmbeds().isEmpty()) {
                EmbedBuilder embed = new EmbedBuilder(message.getEmbeds().get(0));
                embed.addField(lang.getMessage("discord.ticket.message.claimed-by"),
                        event.getUser().getAsMention(), false);
                embed.setColor(Color.ORANGE);
                message.editMessageEmbeds(embed.build()).queue();
            }

            event.reply(lang.getMessage("discord.ticket.claim.success"))
                    .setEphemeral(true).queue();

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    private void handleTicketUnclaim(ButtonInteractionEvent event, String buttonId) {
        int ticketId = Integer.parseInt(buttonId.replace("ticket_unclaim_", ""));
        LanguageManager lang = plugin.getLanguageManager();

        try {
            plugin.getTicketManager().unclaimTicket(ticketId);

            // Nachricht updaten
            Message message = event.getMessage();
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("ticket_claim_" + ticketId,
                    lang.getMessage("discord.ticket.button.claim")));
            buttons.add(Button.primary("ticket_adduser_" + ticketId,
                    lang.getMessage("discord.ticket.button.add-user")));
            buttons.add(Button.danger("ticket_close_" + ticketId,
                    lang.getMessage("discord.ticket.button.close")));

            message.editMessageComponents(ActionRow.of(buttons)).queue();

            event.reply(lang.getMessage("discord.ticket.claim.unclaimed"))
                    .setEphemeral(true).queue();

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    private void handleTicketAddUser(ButtonInteractionEvent event, String buttonId) {
        int ticketId = Integer.parseInt(buttonId.replace("ticket_adduser_", ""));
        LanguageManager lang = plugin.getLanguageManager();

        TextInput userInput = TextInput.create("user_id",
                        lang.getMessage("discord.ticket.add-user.field-user"),
                        TextInputStyle.SHORT)
                .setPlaceholder(lang.getMessage("discord.ticket.add-user.field-user-placeholder"))
                .setRequired(true)
                .build();

        Modal modal = Modal.create("ticket_adduser_" + ticketId,
                        lang.getMessage("discord.ticket.add-user.modal-title"))
                .addActionRow(userInput)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleTicketClose(ButtonInteractionEvent event, String buttonId) {
        int ticketId = Integer.parseInt(buttonId.replace("ticket_close_", ""));
        LanguageManager lang = plugin.getLanguageManager();

        // Prüfen ob User berechtigt ist direkt zu schließen (Admin/Claimer)
        // Für jetzt: Bestätigungsdialog anzeigen

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(lang.getMessage("discord.ticket.close.confirm-title"));
        embed.setDescription(lang.getMessage("discord.ticket.close.confirm-description"));
        embed.setColor(Color.RED);

        event.replyEmbeds(embed.build())
                .addActionRow(
                        Button.danger("close_confirm_" + ticketId,
                                lang.getMessage("discord.ticket.close.confirm-yes")),
                        Button.secondary("close_cancel_" + ticketId,
                                lang.getMessage("discord.ticket.close.confirm-no"))
                )
                .setEphemeral(true)
                .queue();
    }

    private void handleCloseConfirm(ButtonInteractionEvent event, String buttonId) {
        int ticketId = Integer.parseInt(buttonId.replace("close_confirm_", ""));
        LanguageManager lang = plugin.getLanguageManager();

        try {
            plugin.getTicketManager().closeTicket(ticketId);

            event.getMessage().delete().queue();
            event.getChannel().sendMessage(lang.getMessage("discord.ticket.close.closed")).queue();

            // Channel nach 5 Sekunden löschen
            event.getChannel().asTextChannel().delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    private void handleWhitelistApprove(ButtonInteractionEvent event, String buttonId) {
        String[] parts = buttonId.split("_");
        String discordId = parts[2];
        LanguageManager lang = plugin.getLanguageManager();

        try {
            // Minecraft Name aus DB holen
            Connection conn = plugin.getDatabaseManager().getConnection();
            String query = "SELECT minecraft_name FROM users WHERE discord_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();

            String minecraftName = null;
            if (rs.next()) {
                minecraftName = rs.getString("minecraft_name");
            }
            rs.close();
            stmt.close();

            if (minecraftName == null) {
                event.reply(lang.getMessage("error.unknown")).setEphemeral(true).queue();
                return;
            }

            plugin.getWhitelistManager().approveWhitelistRequest(discordId, minecraftName);

            event.reply(lang.getMessage("discord.whitelist.approve.success")).setEphemeral(true).queue();

            // DM an User senden
            User user = plugin.getDiscordBot().getJDA().retrieveUserById(discordId).complete();
            if (user != null) {
                EmbedBuilder dm = new EmbedBuilder();
                dm.setTitle(lang.getMessage("discord.whitelist.approve.dm-title"));
                dm.setDescription(lang.getMessage("discord.whitelist.approve.dm-description")
                        .replace("{name}", minecraftName));
                dm.setColor(Color.GREEN);
                user.openPrivateChannel().queue(channel -> channel.sendMessageEmbeds(dm.build()).queue());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            event.reply(lang.getMessage("error.database").replace("{error}", e.getMessage()))
                    .setEphemeral(true).queue();
        }
    }

    private void handleWhitelistDeny(ButtonInteractionEvent event, String buttonId) {
        String discordId = buttonId.replace("whitelist_deny_", "");
        LanguageManager lang = plugin.getLanguageManager();

        TextInput reason = TextInput.create("reason",
                        lang.getMessage("discord.whitelist.deny.field-reason"),
                        TextInputStyle.PARAGRAPH)
                .setPlaceholder(lang.getMessage("discord.whitelist.deny.field-reason-placeholder"))
                .setRequired(true)
                .setMaxLength(500)
                .build();

        Modal modal = Modal.create("whitelist_deny_" + discordId,
                        lang.getMessage("discord.whitelist.deny.modal-title"))
                .addActionRow(reason)
                .build();

        event.replyModal(modal).queue();
    }
}