package de.MaxBrassLoud.disMineBridge.discord.listeners;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.sql.*;

public class MessageListener extends ListenerAdapter {

    private final DisMineBridge plugin;

    public MessageListener(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!plugin.getConfig().getBoolean("tickets.log-messages", true)) return;

        // Prüfen ob in einem Ticket-Channel
        String channelName = event.getChannel().getName();
        if (!channelName.startsWith("ticket-")) return;

        // Ticket ID aus Channelname extrahieren und Nachricht loggen
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Finde Ticket anhand Channel
                Connection conn = plugin.getDatabaseManager().getConnection();
                String query = "SELECT ticket_id FROM tickets WHERE discord_channel_id = ?";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, event.getChannel().getId());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int ticketId = rs.getInt("ticket_id");
                    int userId = plugin.getDatabaseManager().createOrGetUser(event.getAuthor().getId());

                    plugin.getTicketManager().logMessage(
                            ticketId,
                            userId,
                            event.getMessage().getContentRaw(),
                            event.getMessageId()
                    );
                }

                rs.close();
                stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}