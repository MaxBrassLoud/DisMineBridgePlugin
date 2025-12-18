package de.MaxBrassLoud.disMineBridgePlugin.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.Color;
import java.util.List;
import java.util.logging.Logger;

public class DiscordWhitelistListener extends ListenerAdapter {

    private static final Logger logger = Logger.getLogger("DisMineBridge");

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // Whitelist-Anfrage Button
        if (buttonId.equals("ticket_whitelist")) {
            handleWhitelistRequest(event);
            return;
        }

        // Whitelist Approve Button
        if (buttonId.startsWith("whitelist_approve_")) {
            handleWhitelistApprove(event, buttonId);
            return;
        }

        // Whitelist Deny Button
        if (buttonId.startsWith("whitelist_deny_")) {
            handleWhitelistDeny(event, buttonId);
            return;
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("whitelist_request_modal")) {
            handleWhitelistRequestSubmit(event);
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        // Prüfe ob es der richtige Server ist
        if (!event.getGuild().getId().equals(DiscordManager.getGuildId())) {
            return;
        }

        String discordId = event.getUser().getId();
        logger.info("[Discord] User left: " + event.getUser().getAsTag() + " - Prüfe Whitelist...");

        DiscordManager.handleMemberLeave(discordId);
    }

    /**
     * Behandelt Whitelist-Anfrage Button-Klick
     */
    private void handleWhitelistRequest(ButtonInteractionEvent event) {
        // Erstelle Modal für Minecraft-Namen Eingabe
        TextInput minecraftNameInput = TextInput.create("minecraft_name", "Minecraft Name", TextInputStyle.SHORT)
                .setPlaceholder("Dein exakter Minecraft-Name")
                .setRequired(true)
                .setMinLength(3)
                .setMaxLength(16)
                .build();

        Modal modal = Modal.create("whitelist_request_modal", "📋 Whitelist-Anfrage")
                .addActionRow(minecraftNameInput)
                .build();

        event.replyModal(modal).queue();
    }

    /**
     * Behandelt Modal-Submit für Whitelist-Anfrage
     */
    private void handleWhitelistRequestSubmit(ModalInteractionEvent event) {
        String minecraftName = event.getValue("minecraft_name").getAsString().trim();
        User user = event.getUser();

        // Validierung
        if (!isValidMinecraftName(minecraftName)) {
            event.reply("❌ Ungültiger Minecraft-Name! Nur Buchstaben, Zahlen und Unterstriche erlaubt (3-16 Zeichen).")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.reply("✅ Deine Whitelist-Anfrage wird bearbeitet...")
                .setEphemeral(true)
                .queue();

        DiscordManager.createWhitelistRequest(user, minecraftName);
    }

    /**
     * Behandelt Whitelist-Genehmigung
     */
    private void handleWhitelistApprove(ButtonInteractionEvent event, String buttonId) {
        // Prüfe Berechtigung
        if (!hasWhitelistPermission(event.getMember())) {
            event.reply("❌ Du hast keine Berechtigung dafür!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String discordId = buttonId.replace("whitelist_approve_", "");
        User approver = event.getUser();

        DiscordManager.approveWhitelistRequest(discordId, approver);

        // Update Embed
        EmbedBuilder embed = new EmbedBuilder(event.getMessage().getEmbeds().get(0))
                .setColor(Color.GREEN)
                .addField("Status", "✅ Genehmigt von " + approver.getAsMention(), false);

        event.editMessageEmbeds(embed.build())
                .setActionRow(
                        event.getButton().asDisabled().withLabel("✅ Genehmigt")
                )
                .queue();

        event.reply("✅ Whitelist-Anfrage wurde genehmigt!")
                .setEphemeral(true)
                .queue();
    }

    /**
     * Behandelt Whitelist-Ablehnung
     */
    private void handleWhitelistDeny(ButtonInteractionEvent event, String buttonId) {
        // Prüfe Berechtigung
        if (!hasWhitelistPermission(event.getMember())) {
            event.reply("❌ Du hast keine Berechtigung dafür!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String discordId = buttonId.replace("whitelist_deny_", "");
        User denier = event.getUser();

        DiscordManager.denyWhitelistRequest(discordId, denier);

        // Update Embed
        EmbedBuilder embed = new EmbedBuilder(event.getMessage().getEmbeds().get(0))
                .setColor(Color.RED)
                .addField("Status", "❌ Abgelehnt von " + denier.getAsMention(), false);

        event.editMessageEmbeds(embed.build())
                .setActionRow(
                        event.getButton().asDisabled().withLabel("❌ Abgelehnt")
                )
                .queue();

        event.reply("✅ Whitelist-Anfrage wurde abgelehnt.")
                .setEphemeral(true)
                .queue();
    }

    /**
     * Validiert Minecraft-Namen
     */
    private boolean isValidMinecraftName(String name) {
        return name.matches("^[a-zA-Z0-9_]{3,16}$");
    }

    /**
     * Prüft ob Member Whitelist-Berechtigung hat
     */
    private boolean hasWhitelistPermission(net.dv8tion.jda.api.entities.Member member) {
        if (member == null) return false;

        List<String> allowedRoles = DiscordManager.getWhitelistRoles();
        if (allowedRoles.isEmpty()) return true; // Wenn keine Rollen definiert, alle erlaubt

        return member.getRoles().stream()
                .anyMatch(role -> allowedRoles.contains(role.getId()) ||
                        allowedRoles.contains(role.getName()));
    }
}