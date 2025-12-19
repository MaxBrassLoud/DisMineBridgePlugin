package de.MaxBrassLoud.disMineBridgePlugin.discord;

import de.MaxBrassLoud.disMineBridgePlugin.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordManager {

    private static final Logger logger = Logger.getLogger("DisMineBridge");
    private static JDA jda;
    private static JavaPlugin plugin;
    private static FileConfiguration config;
    private static boolean enabled = false;

    private static String guildId;
    private static String whitelistRequestChannelId;
    private static List<String> whitelistRoles;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        config = plugin.getConfig();

        enabled = config.getBoolean("discord.enabled", false);

        if (!enabled) {
            logger.info("[Discord] Discord-Integration ist deaktiviert.");
            return;
        }

        String token = config.getString("discord.token", "");
        guildId = config.getString("discord.guild-id", "");
        whitelistRequestChannelId = config.getString("discord.whitelist-request-channel-id", "");
        whitelistRoles = config.getStringList("discord.whitelist-roles");

        if (token.isEmpty() || token.equals("DEIN_BOT_TOKEN_HIER")) {
            logger.warning("[Discord] Kein gültiger Bot-Token in der Config! Discord-Integration deaktiviert.");
            enabled = false;
            return;
        }

        if (guildId.isEmpty() || guildId.equals("DEINE_SERVER_ID")) {
            logger.warning("[Discord] Keine gültige Guild-ID in der Config! Discord-Integration deaktiviert.");
            enabled = false;
            return;
        }

        try {
            logger.info("[Discord] Starte Discord Bot...");

            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .addEventListeners(new DiscordWhitelistListener())
                    .build();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    jda.awaitReady();
                    logger.info("[Discord] JDA ready");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            logger.info("[Discord] ✔ Bot erfolgreich gestartet!");
            logger.info("[Discord] Bot Name: " + jda.getSelfUser().getName());
            logger.info("[Discord] Bot Tag: " + jda.getSelfUser().getAsTag());

            createDatabaseTables();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Starten des Bots!", e);
            enabled = false;
        }
    }

    private static void createDatabaseTables() {
        try {
            String sql;
            if (DatabaseManager.getInstance().isMySQL()) {
                sql = """
                    CREATE TABLE IF NOT EXISTS discord_whitelist (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        discord_id VARCHAR(20) NOT NULL UNIQUE,
                        discord_name VARCHAR(100),
                        minecraft_name VARCHAR(16),
                        minecraft_uuid VARCHAR(36),
                        status VARCHAR(20) DEFAULT 'PENDING',
                        requested_at BIGINT,
                        approved_by VARCHAR(100),
                        approved_at BIGINT
                    )
                """;
            } else {
                sql = """
                    CREATE TABLE IF NOT EXISTS discord_whitelist (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        discord_id TEXT NOT NULL UNIQUE,
                        discord_name TEXT,
                        minecraft_name TEXT,
                        minecraft_uuid TEXT,
                        status TEXT DEFAULT 'PENDING',
                        requested_at INTEGER,
                        approved_by TEXT,
                        approved_at INTEGER
                    )
                """;
            }

            DatabaseManager.getInstance().getConnection().createStatement().execute(sql);
            logger.info("[Discord] Datenbank-Tabellen erstellt/geladen.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Erstellen der Tabellen!", e);
        }
    }

    public static void shutdown() {
        if (jda != null) {
            logger.info("[Discord] Fahre Bot herunter...");
            jda.shutdown();
        }
    }

    public static boolean isEnabled() {
        return enabled && jda != null;
    }

    public static JDA getJDA() {
        return jda;
    }

    public static String getGuildId() {
        return guildId;
    }

    public static String getWhitelistRequestChannelId() {
        return whitelistRequestChannelId;
    }

    public static void setWhitelistRequestChannelId(String channelId) {
        whitelistRequestChannelId = channelId;
        config.set("discord.whitelist-request-channel-id", channelId);
        plugin.saveConfig();
    }

    public static List<String> getWhitelistRoles() {
        return whitelistRoles;
    }

    /**
     * Sendet Ticket-Nachricht in einen Kanal
     */
    public static void sendTicketMessage(String channelId) {
        if (!isEnabled()) {
            logger.warning("[Discord] Bot ist nicht aktiviert!");
            return;
        }

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                logger.warning("[Discord] Kanal nicht gefunden: " + channelId);
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎫 Ticket System")
                    .setDescription("Wähle eine Kategorie aus, um ein Ticket zu erstellen:")
                    .addField("📋 Whitelist-Anfrage", "Beantrage Zugang zum Server", false)
                    .addField("❓ Support", "Erhalte Hilfe vom Team", false)
                    .addField("🐛 Bug Report", "Melde einen Bug", false)
                    .setColor(Color.ORANGE)
                    .setFooter("DisMineBridge Ticket System", null);

            channel.sendMessageEmbeds(embed.build())
                    .setActionRow(
                            Button.primary("ticket_whitelist", "📋 Whitelist"),
                            Button.success("ticket_support", "❓ Support"),
                            Button.danger("ticket_bug", "🐛 Bug Report")
                    )
                    .queue(
                            success -> logger.info("[Discord] Ticket-Nachricht gesendet in " + channel.getName()),
                            error -> logger.warning("[Discord] Fehler beim Senden: " + error.getMessage())
                    );

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Senden der Ticket-Nachricht!", e);
        }
    }
    public static  void createTicket(User user, String ProblemDescription, String Mode, MessageReceivedEvent event) {
        if (!isEnabled()) return;

        String Name = user.getName();


        String ChannelName = Name + "-" + Mode;

        try {
            event.getGuild().createTextChannel(ChannelName).queue();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(Mode +"Ticket")
                    .setDescription(Name + " hat ein Ticket erstellt!")
                    .addField("Ersteller:", user.getAsMention(), true)
                    .addField("ProblemBeschreibung", ProblemDescription, true)
                    .addField("Angefragt", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", false)
                    .setColor(Color.YELLOW)
                    .setThumbnail(user.getAvatarUrl())
                    .setFooter("User ID: " + user.getId(), null);
            TextChannel Channelid = event.getGuild().getTextChannelsByName(ChannelName, true).getFirst();
            Channelid.sendMessageEmbeds(embed.build())
                    .setActionRow(
                            Button.success("take_over" + user.getId(), "✅ Übernehmen"),
                            Button.danger("close" + user.getId(), "❌ Schließen")
                    )
                    .queue();






        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim erstellen des Tickets");
        }
    }
    /**
     * Erstellt Whitelist-Anfrage
     */
    public static void createWhitelistRequest(User user, String minecraftName) {
        if (!isEnabled()) return;

        try {
            // Prüfe ob bereits Anfrage existiert
            if (hasExistingRequest(user.getId())) {
                user.openPrivateChannel().queue(channel ->
                        channel.sendMessage("❌ Du hast bereits eine offene Whitelist-Anfrage!").queue()
                );
                return;
            }

            // Speichere in Datenbank
            String sql = "INSERT INTO discord_whitelist (discord_id, discord_name, minecraft_name, status, requested_at) VALUES (?, ?, ?, ?, ?)";
            DatabaseManager.getInstance().executeUpdate(sql,
                    user.getId(),
                    user.getAsTag(),
                    minecraftName,
                    "PENDING",
                    System.currentTimeMillis()
            );

            // Sende Nachricht im Whitelist-Kanal
            TextChannel channel = jda.getTextChannelById(whitelistRequestChannelId);
            if (channel == null) {
                logger.warning("[Discord] Whitelist-Request-Channel nicht gefunden!");
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 Neue Whitelist-Anfrage")
                    .setDescription("Ein neuer Spieler möchte auf die Whitelist!")
                    .addField("Discord", user.getAsMention(), true)
                    .addField("Minecraft Name", minecraftName, true)
                    .addField("Angefragt", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", false)
                    .setColor(Color.YELLOW)
                    .setThumbnail(user.getAvatarUrl())
                    .setFooter("User ID: " + user.getId(), null);

            channel.sendMessageEmbeds(embed.build())
                    .setActionRow(
                            Button.success("whitelist_approve_" + user.getId(), "✅ Annehmen"),
                            Button.danger("whitelist_deny_" + user.getId(), "❌ Ablehnen")
                    )
                    .queue();

            // Bestätigung an User
            user.openPrivateChannel().queue(dm ->
                    dm.sendMessage("✅ Deine Whitelist-Anfrage wurde eingereicht!\n" +
                            "📋 Minecraft Name: `" + minecraftName + "`\n" +
                            "⏳ Bitte warte auf die Entscheidung eines Team-Mitglieds.").queue()
            );

            logger.info("[Discord] Whitelist-Anfrage erstellt: " + user.getAsTag() + " (" + minecraftName + ")");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Erstellen der Whitelist-Anfrage!", e);
        }
    }

    /**
     * Genehmigt Whitelist-Anfrage
     */
    public static void approveWhitelistRequest(String discordId, User approver) {
        if (!isEnabled()) return;

        try {
            // Hole Minecraft-Namen aus Datenbank
            String sql = "SELECT minecraft_name, discord_name FROM discord_whitelist WHERE discord_id = ? AND status = 'PENDING'";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, discordId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                logger.warning("[Discord] Keine ausstehende Anfrage gefunden für: " + discordId);
                return;
            }

            String minecraftName = rs.getString("minecraft_name");
            String discordName = rs.getString("discord_name");
            rs.close();
            ps.close();

            // Füge zur Minecraft-Whitelist hinzu
            de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager.addToWhitelist(
                    minecraftName,
                    "Discord:" + approver.getAsTag(),
                    false
            );

            // Update Discord-Whitelist-Status
            sql = "UPDATE discord_whitelist SET status = 'APPROVED', approved_by = ?, approved_at = ? WHERE discord_id = ?";
            DatabaseManager.getInstance().executeUpdate(sql,
                    approver.getAsTag(),
                    System.currentTimeMillis(),
                    discordId
            );

            // Update Minecraft-Whitelist mit Discord-ID
            sql = "UPDATE whitelist SET discord_id = ? WHERE username = ?";
            DatabaseManager.getInstance().executeUpdate(sql, discordId, minecraftName);

            // Benachrichtige User
            jda.retrieveUserById(discordId).queue(user ->
                    user.openPrivateChannel().queue(dm ->
                            dm.sendMessage("✅ **Deine Whitelist-Anfrage wurde angenommen!**\n" +
                                    "🎮 Du kannst dich nun auf dem Server einloggen.\n" +
                                    "📋 Minecraft Name: `" + minecraftName + "`").queue()
                    )
            );

            logger.info("[Discord] Whitelist-Anfrage genehmigt: " + discordName + " (" + minecraftName + ") von " + approver.getAsTag());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Genehmigen der Whitelist-Anfrage!", e);
        }
    }

    /**
     * Lehnt Whitelist-Anfrage ab
     */
    public static void denyWhitelistRequest(String discordId, User denier) {
        if (!isEnabled()) return;

        try {
            // Update Status
            String sql = "UPDATE discord_whitelist SET status = 'DENIED', approved_by = ?, approved_at = ? WHERE discord_id = ?";
            DatabaseManager.getInstance().executeUpdate(sql,
                    denier.getAsTag(),
                    System.currentTimeMillis(),
                    discordId
            );

            // Benachrichtige User
            jda.retrieveUserById(discordId).queue(user ->
                    user.openPrivateChannel().queue(dm ->
                            dm.sendMessage("❌ **Deine Whitelist-Anfrage wurde abgelehnt.**\n" +
                                    "📝 Bei Fragen wende dich an ein Team-Mitglied.").queue()
                    )
            );

            logger.info("[Discord] Whitelist-Anfrage abgelehnt für Discord-ID: " + discordId + " von " + denier.getAsTag());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Ablehnen der Whitelist-Anfrage!", e);
        }
    }

    /**
     * Entfernt Spieler von Whitelist wenn er Discord verlässt
     */
    public static void handleMemberLeave(String discordId) {
        if (!isEnabled()) return;

        try {
            // Hole Minecraft-Namen
            String sql = "SELECT minecraft_name FROM discord_whitelist WHERE discord_id = ? AND status = 'APPROVED'";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, discordId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String minecraftName = rs.getString("minecraft_name");

                // Entferne von Minecraft-Whitelist
                de.MaxBrassLoud.disMineBridgePlugin.whitelist.WhitelistManager.removeFromWhitelist(minecraftName);

                // Update Discord-Whitelist
                sql = "UPDATE discord_whitelist SET status = 'REMOVED_LEFT' WHERE discord_id = ?";
                DatabaseManager.getInstance().executeUpdate(sql, discordId);

                logger.info("[Discord] Whitelist entfernt (User left): " + minecraftName + " (Discord: " + discordId + ")");
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Discord] Fehler beim Entfernen nach Leave!", e);
        }
    }

    /**
     * Findet Discord-User via Minecraft-Name
     */
    public static String findDiscordByMinecraft(String minecraftName) {
        try {
            String sql = "SELECT discord_id, discord_name, status FROM discord_whitelist WHERE minecraft_name = ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, minecraftName);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String discordId = rs.getString("discord_id");
                String discordName = rs.getString("discord_name");
                String status = rs.getString("status");

                rs.close();
                ps.close();

                return "Discord: " + discordName + " (ID: " + discordId + ") [Status: " + status + "]";
            }

            rs.close();
            ps.close();
            return null;

        } catch (Exception e) {
            logger.log(Level.WARNING, "[Discord] Fehler bei Discord-Suche!", e);
            return null;
        }
    }

    /**
     * Findet Minecraft-Name via Discord-ID oder Name
     */
    public static String findMinecraftByDiscord(String discordIdentifier) {
        try {
            String sql = "SELECT minecraft_name, discord_name, status FROM discord_whitelist WHERE discord_id = ? OR discord_name LIKE ?";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, discordIdentifier, "%" + discordIdentifier + "%");
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String minecraftName = rs.getString("minecraft_name");
                String discordName = rs.getString("discord_name");
                String status = rs.getString("status");

                rs.close();
                ps.close();

                return "Minecraft: " + minecraftName + " (Discord: " + discordName + ") [Status: " + status + "]";
            }

            rs.close();
            ps.close();
            return null;

        } catch (Exception e) {
            logger.log(Level.WARNING, "[Discord] Fehler bei Minecraft-Suche!", e);
            return null;
        }
    }

    /**
     * Prüft ob bereits eine Anfrage existiert
     */
    private static boolean hasExistingRequest(String discordId) {
        try {
            String sql = "SELECT COUNT(*) as count FROM discord_whitelist WHERE discord_id = ? AND status = 'PENDING'";
            PreparedStatement ps = DatabaseManager.getInstance().prepareStatement(sql, discordId);
            ResultSet rs = ps.executeQuery();

            boolean exists = rs.next() && rs.getInt("count") > 0;

            rs.close();
            ps.close();

            return exists;

        } catch (Exception e) {
            return false;
        }


    }


}