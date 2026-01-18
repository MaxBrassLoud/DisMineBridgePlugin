package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.List;

public class TicketManager {

    private final DisMineBridge plugin;

    public TicketManager(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    public int createTicket(String discordId, String ticketType, String description, String minecraftName, Map<String, String> additionalData) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();
        int userId = plugin.getDatabaseManager().createOrGetUser(discordId);

        String insert = "INSERT INTO tickets (creator_id, ticket_type, status, description, minecraft_name, additional_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(insert);
        stmt.setInt(1, userId);
        stmt.setString(2, ticketType);
        stmt.setString(3, "open");
        stmt.setString(4, description);
        stmt.setString(5, minecraftName);
        stmt.setString(6, additionalDataToJson(additionalData));
        long now = System.currentTimeMillis();
        stmt.setLong(7, now);
        stmt.setLong(8, now);
        stmt.executeUpdate();

        ResultSet rs = stmt.getGeneratedKeys();
        int ticketId = 0;
        if (rs.next()) {
            ticketId = rs.getInt(1);
        }
        rs.close();
        stmt.close();

        return ticketId;
    }

    public TextChannel createTicketChannel(Guild guild, User user, int ticketId, String ticketType) {
        try {
            String channelName = plugin.getLanguageManager().getMessage("discord.ticket.channel-name")
                    .replace("{username}", user.getName())
                    .replace("{id}", String.valueOf(ticketId));

            String categoryId = null;
            if (ticketType.equals("support")) {
                categoryId = getSetupValue(guild.getId(), "support_category_id");
            } else if (ticketType.equals("bug-report")) {
                categoryId = getSetupValue(guild.getId(), "bug_category_id");
            }

            Category category = categoryId != null ? guild.getCategoryById(categoryId) : null;

            TextChannel channel;
            if (category != null) {
                channel = category.createTextChannel(channelName).complete();
            } else {
                channel = guild.createTextChannel(channelName).complete();
            }

            // Berechtigungen setzen
            channel.upsertPermissionOverride(guild.getPublicRole())
                    .deny(Permission.VIEW_CHANNEL)
                    .queue();
            Member member = guild.getMember(user);
            if (member == null) {
                throw new IllegalStateException("User ist kein Member der Guild");
            }
            channel.upsertPermissionOverride(member)
                    .grant(
                            Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_SEND,
                            Permission.MESSAGE_HISTORY,
                            Permission.MESSAGE_ATTACH_FILES
                    )
                    .queue();

            // Rollen mit Zugriff hinzufügen
            addStaffPermissions(channel, guild, ticketType);

            return channel;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void addStaffPermissions(TextChannel channel, Guild guild, String ticketType) {
        String adminRoles = getSetupValue(guild.getId(), "admin_roles");
        String modRoles = getSetupValue(guild.getId(), "moderator_roles");
        String supportRoles = getSetupValue(guild.getId(), "supporter_roles");
        String devRoles = getSetupValue(guild.getId(), "developer_roles");

        List<String> roleIds = new ArrayList<>();

        if (adminRoles != null) roleIds.addAll(Arrays.asList(adminRoles.split(",")));
        if (modRoles != null) roleIds.addAll(Arrays.asList(modRoles.split(",")));

        if (ticketType.equals("support") && supportRoles != null) {
            roleIds.addAll(Arrays.asList(supportRoles.split(",")));
        } else if (ticketType.equals("bug-report") && devRoles != null) {
            roleIds.addAll(Arrays.asList(devRoles.split(",")));
        }

        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId.trim());
            if (role != null) {
                channel.upsertPermissionOverride(role)
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                        .queue();
            }
        }
    }

    public void sendTicketMessage(TextChannel channel, int ticketId, User creator, String ticketType, String description, String minecraftName) {
        LanguageManager lang = plugin.getLanguageManager();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(lang.getMessage("discord.ticket.message.title").replace("{id}", String.valueOf(ticketId)));
        embed.setColor(getTicketColor(ticketType));
        embed.addField(lang.getMessage("discord.ticket.message.creator"), creator.getAsMention(), false);
        embed.addField(lang.getMessage("discord.ticket.message.type"), getTicketTypeName(ticketType), false);

        if (minecraftName != null && !minecraftName.isEmpty()) {
            embed.addField(lang.getMessage("discord.ticket.create.field-minecraft-name"), minecraftName, false);
        }

        embed.addField(lang.getMessage("discord.ticket.message.description"), description, false);
        embed.addField(lang.getMessage("discord.ticket.message.status"), lang.getMessage("status.open"), false);
        embed.setTimestamp(Instant.now());
        embed.setFooter(lang.getMessage("discord.ticket.message.created-at").replace("{time}", ""), null);

        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("ticket_claim_" + ticketId, lang.getMessage("discord.ticket.button.claim")));
        buttons.add(Button.primary("ticket_adduser_" + ticketId, lang.getMessage("discord.ticket.button.add-user")));
        buttons.add(Button.danger("ticket_close_" + ticketId, lang.getMessage("discord.ticket.button.close")));

        channel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(buttons))
                .queue(message -> {
                    try {
                        saveTicketMessage(ticketId, message.getId());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });
    }

    private Color getTicketColor(String ticketType) {
        String colorHex = plugin.getConfig().getString("tickets.categories." + ticketType + ".color", "#0099ff");
        return Color.decode(colorHex);
    }

    private String getTicketTypeName(String ticketType) {
        return plugin.getConfig().getString("tickets.categories." + ticketType + ".name", ticketType);
    }

    public void claimTicket(int ticketId, String discordId) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();
        int userId = plugin.getDatabaseManager().createOrGetUser(discordId);

        String update = "UPDATE tickets SET claimer_id = ?, status = ?, updated_at = ? WHERE ticket_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setInt(1, userId);
        stmt.setString(2, "claimed");
        stmt.setLong(3, System.currentTimeMillis());
        stmt.setInt(4, ticketId);
        stmt.executeUpdate();
        stmt.close();
    }

    public void unclaimTicket(int ticketId) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();

        String update = "UPDATE tickets SET claimer_id = NULL, status = ?, updated_at = ? WHERE ticket_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setString(1, "open");
        stmt.setLong(2, System.currentTimeMillis());
        stmt.setInt(3, ticketId);
        stmt.executeUpdate();
        stmt.close();
    }

    public void closeTicket(int ticketId) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();

        String update = "UPDATE tickets SET status = ?, closed_at = ?, updated_at = ? WHERE ticket_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setString(1, "closed");
        long now = System.currentTimeMillis();
        stmt.setLong(2, now);
        stmt.setLong(3, now);
        stmt.setInt(4, ticketId);
        stmt.executeUpdate();
        stmt.close();
    }

    public void logMessage(int ticketId, int userId, String content, String messageId) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();

        String insert = "INSERT INTO messages (ticket_id, user_id, message_content, discord_message_id, timestamp) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(insert);
        stmt.setInt(1, ticketId);
        stmt.setInt(2, userId);
        stmt.setString(3, content);
        stmt.setString(4, messageId);
        stmt.setLong(5, System.currentTimeMillis());
        stmt.executeUpdate();
        stmt.close();
    }

    private void saveTicketMessage(int ticketId, String messageId) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();

        String update = "UPDATE tickets SET discord_channel_id = ? WHERE ticket_id = ?";
        PreparedStatement stmt = conn.prepareStatement(update);
        stmt.setString(1, messageId);
        stmt.setInt(2, ticketId);
        stmt.executeUpdate();
        stmt.close();
    }

    public String getSetupValue(String guildId, String column) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            String query = "SELECT " + column + " FROM ticket_setup WHERE guild_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            String value = null;
            if (rs.next()) {
                value = rs.getString(column);
            }

            rs.close();
            stmt.close();
            return value;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String additionalDataToJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) return null;
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            json.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue().replace("\"", "\\\"")).append("\",");
        }
        json.setLength(json.length() - 1);
        json.append("}");
        return json.toString();
    }
}