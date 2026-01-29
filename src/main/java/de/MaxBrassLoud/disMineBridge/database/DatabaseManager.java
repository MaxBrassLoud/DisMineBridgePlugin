package de.MaxBrassLoud.disMineBridge.database;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.managers.PunishmentManager;

import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final DisMineBridge plugin;
    private static Connection connection;

    public DatabaseManager(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    public static DatabaseManager getInstance() {
        return DisMineBridge.getInstance().getDatabaseManager();
    }

    public boolean connect() {
        try {
            boolean useLocal = plugin.getConfig().getBoolean("database.use-local", true);

            if (useLocal) {
                String file = plugin.getConfig().getString("database.local.file", "disminebridge.db");
                String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/" + file;
                connection = DriverManager.getConnection(url);
            } else {
                String host = plugin.getConfig().getString("database.extern.host");
                int port = plugin.getConfig().getInt("database.extern.port", 3306);
                String database = plugin.getConfig().getString("database.extern.database");
                String username = plugin.getConfig().getString("database.extern.username");
                String password = plugin.getConfig().getString("database.extern.password");
                boolean useSSL = plugin.getConfig().getBoolean("database.extern.use-ssl", false);

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL;
                connection = DriverManager.getConnection(url, username, password);
            }

            createTables();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        // Users Tabelle
        String usersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "internal_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "discord_id TEXT UNIQUE NOT NULL," +
                "minecraft_name TEXT," +
                "minecraft_uuid TEXT," +
                "whitelist_status TEXT DEFAULT 'none'," +
                "is_on_server BOOLEAN DEFAULT 1," +
                "whitelist_request_date BIGINT," +
                "whitelist_cooldown_until BIGINT," +
                "created_at BIGINT NOT NULL" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            usersTable = usersTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT");
        }

        stmt.execute(usersTable);

        // Tickets Tabelle
        String ticketsTable = "CREATE TABLE IF NOT EXISTS tickets (" +
                "ticket_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "creator_id INTEGER NOT NULL," +
                "claimer_id INTEGER," +
                "ticket_type TEXT NOT NULL," +
                "status TEXT NOT NULL," +
                "discord_channel_id TEXT," +
                "description TEXT," +
                "minecraft_name TEXT," +
                "additional_data TEXT," +
                "created_at BIGINT NOT NULL," +
                "updated_at BIGINT NOT NULL," +
                "closed_at BIGINT," +
                "FOREIGN KEY (creator_id) REFERENCES users(internal_id)," +
                "FOREIGN KEY (claimer_id) REFERENCES users(internal_id)" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            ticketsTable = ticketsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER NOT NULL", "INT NOT NULL")
                    .replace("INTEGER,", "INT,");
        }

        stmt.execute(ticketsTable);

        // Ticket Teilnehmer Tabelle
        String participantsTable = "CREATE TABLE IF NOT EXISTS ticket_participants (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ticket_id INTEGER NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "added_at BIGINT NOT NULL," +
                "FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id)," +
                "FOREIGN KEY (user_id) REFERENCES users(internal_id)" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            participantsTable = participantsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER NOT NULL", "INT NOT NULL");
        }

        stmt.execute(participantsTable);

        // Nachrichten Tabelle
        String messagesTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "message_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ticket_id INTEGER NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "message_content TEXT NOT NULL," +
                "discord_message_id TEXT," +
                "timestamp BIGINT NOT NULL," +
                "FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id)," +
                "FOREIGN KEY (user_id) REFERENCES users(internal_id)" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            messagesTable = messagesTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER NOT NULL", "INT NOT NULL");
        }

        stmt.execute(messagesTable);

        // Persistente Komponenten
        String componentsTable = "CREATE TABLE IF NOT EXISTS persistent_components (" +
                "component_id TEXT PRIMARY KEY," +
                "component_type TEXT NOT NULL," +
                "discord_message_id TEXT NOT NULL," +
                "ticket_id INTEGER," +
                "data TEXT," +
                "created_at BIGINT NOT NULL," +
                "FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id)" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            componentsTable = componentsTable.replace("INTEGER,", "INT,");
        }

        stmt.execute(componentsTable);

        // Ticket Setup Konfiguration
        String setupTable = "CREATE TABLE IF NOT EXISTS ticket_setup (" +
                "guild_id TEXT PRIMARY KEY," +
                "panel_channel_id TEXT," +
                "panel_message_id TEXT," +
                "support_category_id TEXT," +
                "bug_category_id TEXT," +
                "whitelist_channel_id TEXT," +
                "admin_roles TEXT," +
                "moderator_roles TEXT," +
                "supporter_roles TEXT," +
                "developer_roles TEXT," +
                "updated_at BIGINT NOT NULL" +
                ")";

        stmt.execute(setupTable);

        // Vanish Tabelle
        String vanishTable = "CREATE TABLE IF NOT EXISTS vanish (" +
                "user_id INTEGER PRIMARY KEY," +
                "is_vanished BOOLEAN DEFAULT 0," +
                "vanish_start_time BIGINT," +
                "FOREIGN KEY (user_id) REFERENCES users(internal_id)" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            vanishTable = vanishTable.replace("INTEGER PRIMARY KEY", "INT PRIMARY KEY")
                    .replace("BOOLEAN", "TINYINT(1)");
        }
        stmt.execute(vanishTable);

        // Bans Tabelle
        String bansTable = "CREATE TABLE IF NOT EXISTS bans (" +
                "ban_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "minecraft_uuid TEXT," +
                "minecraft_name TEXT NOT NULL," +
                "reason TEXT NOT NULL," +
                "banner_id INTEGER," +
                "banner_name TEXT NOT NULL," +
                "expire_time BIGINT NOT NULL," +
                "created_at BIGINT NOT NULL," +
                "is_pardoned BOOLEAN DEFAULT 0," +
                "pardoned_by INTEGER," +
                "pardoned_at BIGINT," +
                "pardon_reason TEXT" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            bansTable = bansTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER,", "INT,")
                    .replace("INTEGER NOT NULL", "INT NOT NULL")
                    .replace("BOOLEAN", "TINYINT(1)");
        }

        stmt.execute(bansTable);

        // Punishment Reasons Tabelle
        String reasonsTable = "CREATE TABLE IF NOT EXISTS punishment_reasons (" +
                "reason_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "duration TEXT DEFAULT '-'," +
                "severity INTEGER DEFAULT 5," +
                "enabled BOOLEAN DEFAULT 1," +
                "sort_order INTEGER DEFAULT 0," +
                "created_at BIGINT NOT NULL" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            reasonsTable = reasonsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER DEFAULT", "INT DEFAULT")
                    .replace("BOOLEAN", "TINYINT(1)");
        }

        stmt.execute(reasonsTable);

        // Warns Tabelle
        String warnsTable = "CREATE TABLE IF NOT EXISTS warns (" +
                "warn_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "reason TEXT NOT NULL," +
                "warner TEXT NOT NULL," +
                "created_at BIGINT NOT NULL" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            warnsTable = warnsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT");
        }

        stmt.execute(warnsTable);

        // Kicks Tabelle
        String kicksTable = "CREATE TABLE IF NOT EXISTS kicks (" +
                "kick_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "reason TEXT NOT NULL," +
                "kicker TEXT NOT NULL," +
                "created_at BIGINT NOT NULL" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            kicksTable = kicksTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT");
        }

        stmt.execute(kicksTable);

        // Mutes Tabelle
        String mutesTable = "CREATE TABLE IF NOT EXISTS mutes (" +
                "mute_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "reason TEXT NOT NULL," +
                "muter TEXT NOT NULL," +
                "expire_time BIGINT NOT NULL," +
                "created_at BIGINT NOT NULL," +
                "is_unmuted BOOLEAN DEFAULT 0," +
                "unmuted_by TEXT," +
                "unmuted_at BIGINT," +
                "unmute_reason TEXT" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            mutesTable = mutesTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("BOOLEAN", "TINYINT(1)");
        }

        stmt.execute(mutesTable);

        // Violations Tabelle
        String violationsTable = "CREATE TABLE IF NOT EXISTS violations (" +
                "violation_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "reason TEXT NOT NULL," +
                "punishment_type TEXT NOT NULL," +
                "executor TEXT NOT NULL," +
                "created_at BIGINT NOT NULL" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            violationsTable = violationsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT");
        }

        stmt.execute(violationsTable);

        // Punishment Points Tabelle
        String punishmentPointsTable = "CREATE TABLE IF NOT EXISTS punishment_points (" +
                "point_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "points INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "severity INTEGER NOT NULL," +
                "executor TEXT NOT NULL," +
                "created_at BIGINT NOT NULL," +
                "expires_at BIGINT NOT NULL," +
                "active BOOLEAN DEFAULT 1" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            punishmentPointsTable = punishmentPointsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER NOT NULL", "INT NOT NULL")
                    .replace("BOOLEAN", "TINYINT(1)");
        }

        stmt.execute(punishmentPointsTable);

        // Punishment Point Reductions Tabelle
        String pointReductionsTable = "CREATE TABLE IF NOT EXISTS punishment_point_reductions (" +
                "reduction_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "points_reduced INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "executor TEXT NOT NULL," +
                "created_at BIGINT NOT NULL" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            pointReductionsTable = pointReductionsTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER NOT NULL", "INT NOT NULL");
        }

        stmt.execute(pointReductionsTable);

        // Indices für Performance
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_violations_uuid_reason ON violations(uuid, reason)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishment_points_uuid ON punishment_points(uuid, active, expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mutes_active ON mutes(uuid, is_unmuted, expire_time)");
        } catch (SQLException e) {
            // Indices existieren bereits
        }

        stmt.close();
    }

    // ============================================
    // BAN SYSTEM
    // ============================================

    public int createBan(int userId, String minecraftUuid, String minecraftName, String reason,
                         int bannerId, String bannerName, long expireTime) throws SQLException {
        String insert = "INSERT INTO bans (user_id, minecraft_uuid, minecraft_name, reason, " +
                "banner_id, banner_name, expire_time, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
        if (userId > 0) {
            stmt.setInt(1, userId);
        } else {
            stmt.setNull(1, Types.INTEGER);
        }
        stmt.setString(2, minecraftUuid);
        stmt.setString(3, minecraftName);
        stmt.setString(4, reason);
        if (bannerId > 0) {
            stmt.setInt(5, bannerId);
        } else {
            stmt.setNull(5, Types.INTEGER);
        }
        stmt.setString(6, bannerName);
        stmt.setLong(7, expireTime);
        stmt.setLong(8, System.currentTimeMillis());

        stmt.executeUpdate();

        ResultSet generatedKeys = stmt.getGeneratedKeys();
        int banId = 0;
        if (generatedKeys.next()) {
            banId = generatedKeys.getInt(1);
        }

        generatedKeys.close();
        stmt.close();

        return banId;
    }

    public String getMinecraftUuidByName(String minecraftName) throws SQLException {
        String query = "SELECT minecraft_uuid FROM users WHERE minecraft_name = ? LIMIT 1";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftName);

        ResultSet rs = stmt.executeQuery();
        String uuid = null;

        if (rs.next()) {
            uuid = rs.getString("minecraft_uuid");
        }

        rs.close();
        stmt.close();

        return uuid;
    }

    public BanInfo getActiveBanByName(String minecraftName) throws SQLException {
        String query = "SELECT ban_id, reason, expire_time, banner_name, minecraft_uuid FROM bans " +
                "WHERE minecraft_name = ? AND is_pardoned = 0 AND (expire_time > ? OR expire_time = -1) " +
                "ORDER BY created_at DESC LIMIT 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftName);
        stmt.setLong(2, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        BanInfo banInfo = null;

        if (rs.next()) {
            banInfo = new BanInfo(
                    rs.getInt("ban_id"),
                    rs.getString("reason"),
                    rs.getLong("expire_time"),
                    rs.getString("banner_name")
            );
        }

        rs.close();
        stmt.close();

        return banInfo;
    }

    public boolean hasActiveBan(String minecraftUuid) throws SQLException {
        String query = "SELECT ban_id, expire_time FROM bans " +
                "WHERE minecraft_uuid = ? AND is_pardoned = 0 " +
                "ORDER BY created_at DESC LIMIT 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftUuid);

        ResultSet rs = stmt.executeQuery();

        if (!rs.next()) {
            rs.close();
            stmt.close();
            return false;
        }

        long expireTime = rs.getLong("expire_time");
        rs.close();
        stmt.close();

        return expireTime == -1 || expireTime > System.currentTimeMillis();
    }

    public BanInfo getActiveBan(String minecraftUuid) throws SQLException {
        String query = "SELECT ban_id, reason, expire_time, banner_name FROM bans " +
                "WHERE minecraft_uuid = ? AND is_pardoned = 0 " +
                "ORDER BY created_at DESC LIMIT 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftUuid);

        ResultSet rs = stmt.executeQuery();
        BanInfo banInfo = null;

        if (rs.next()) {
            long expireTime = rs.getLong("expire_time");

            if (expireTime == -1 || expireTime > System.currentTimeMillis()) {
                banInfo = new BanInfo(
                        rs.getInt("ban_id"),
                        rs.getString("reason"),
                        expireTime,
                        rs.getString("banner_name")
                );
            }
        }

        rs.close();
        stmt.close();

        return banInfo;
    }

    public void pardonBan(int banId, int pardonerId, String pardonReason) throws SQLException {
        String update = "UPDATE bans SET is_pardoned = 1, pardoned_by = ?, pardoned_at = ?, " +
                "pardon_reason = ? WHERE ban_id = ?";

        PreparedStatement stmt = connection.prepareStatement(update);
        if (pardonerId > 0) {
            stmt.setInt(1, pardonerId);
        } else {
            stmt.setNull(1, Types.INTEGER);
        }
        stmt.setLong(2, System.currentTimeMillis());
        stmt.setString(3, pardonReason);
        stmt.setInt(4, banId);

        stmt.executeUpdate();
        stmt.close();
    }

    public List<String> getBannedPlayerNames() throws SQLException {
        String query = "SELECT DISTINCT minecraft_name FROM bans " +
                "WHERE is_pardoned = 0 AND (expire_time > ? OR expire_time = -1) " +
                "ORDER BY minecraft_name";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setLong(1, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        List<String> bannedPlayers = new ArrayList<>();

        while (rs.next()) {
            bannedPlayers.add(rs.getString("minecraft_name"));
        }

        rs.close();
        stmt.close();

        return bannedPlayers;
    }

    // ============================================
    // MUTE SYSTEM
    // ============================================

    public int createMute(String minecraftUuid, String minecraftName, String reason,
                          String muterName, long expireTime) throws SQLException {
        String insert = "INSERT INTO mutes (uuid, name, reason, muter, expire_time, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, minecraftUuid);
        stmt.setString(2, minecraftName);
        stmt.setString(3, reason);
        stmt.setString(4, muterName);
        stmt.setLong(5, expireTime);
        stmt.setLong(6, System.currentTimeMillis());

        stmt.executeUpdate();

        ResultSet generatedKeys = stmt.getGeneratedKeys();
        int muteId = 0;
        if (generatedKeys.next()) {
            muteId = generatedKeys.getInt(1);
        }

        generatedKeys.close();
        stmt.close();

        return muteId;
    }

    public void unmutePlaye(String minecraftUuid, String unmuterName, String reason) throws SQLException {
        String update = "UPDATE mutes SET is_unmuted = 1, unmuted_by = ?, unmuted_at = ?, " +
                "unmute_reason = ? WHERE uuid = ? AND is_unmuted = 0 AND expire_time > ?";

        PreparedStatement stmt = connection.prepareStatement(update);
        stmt.setString(1, unmuterName);
        stmt.setLong(2, System.currentTimeMillis());
        stmt.setString(3, reason);
        stmt.setString(4, minecraftUuid);
        stmt.setLong(5, System.currentTimeMillis());

        stmt.executeUpdate();
        stmt.close();
    }

    public boolean hasActiveMute(String minecraftUuid) throws SQLException {
        String query = "SELECT mute_id FROM mutes WHERE uuid = ? AND is_unmuted = 0 " +
                "AND expire_time > ? ORDER BY created_at DESC LIMIT 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftUuid);
        stmt.setLong(2, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        boolean hasMute = rs.next();

        rs.close();
        stmt.close();

        return hasMute;
    }

    public MuteInfo getActiveMute(String minecraftUuid) throws SQLException {
        String query = "SELECT mute_id, reason, expire_time, muter FROM mutes " +
                "WHERE uuid = ? AND is_unmuted = 0 AND expire_time > ? " +
                "ORDER BY created_at DESC LIMIT 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftUuid);
        stmt.setLong(2, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        MuteInfo muteInfo = null;

        if (rs.next()) {
            muteInfo = new MuteInfo(
                    rs.getInt("mute_id"),
                    rs.getString("reason"),
                    rs.getLong("expire_time"),
                    rs.getString("muter")
            );
        }

        rs.close();
        stmt.close();

        return muteInfo;
    }

    public Map<UUID, Long> getActiveMutes() throws SQLException {
        String query = "SELECT uuid, expire_time FROM mutes " +
                "WHERE is_unmuted = 0 AND expire_time > ?";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setLong(1, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        Map<UUID, Long> mutes = new HashMap<>();

        while (rs.next()) {
            try {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                long expireTime = rs.getLong("expire_time");
                mutes.put(uuid, expireTime);
            } catch (IllegalArgumentException e) {
                // Ungültige UUID
            }
        }

        rs.close();
        stmt.close();

        return mutes;
    }

    public List<String> getMutedPlayerNames() throws SQLException {
        String query = "SELECT DISTINCT name FROM mutes " +
                "WHERE is_unmuted = 0 AND expire_time > ? " +
                "ORDER BY name";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setLong(1, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        List<String> mutedPlayers = new ArrayList<>();

        while (rs.next()) {
            mutedPlayers.add(rs.getString("name"));
        }

        rs.close();
        stmt.close();

        return mutedPlayers;
    }

    public int cleanupExpiredMutes() throws SQLException {
        String update = "UPDATE mutes SET is_unmuted = 1, unmuted_by = 'System', " +
                "unmuted_at = ?, unmute_reason = 'Automatisch abgelaufen' " +
                "WHERE is_unmuted = 0 AND expire_time <= ?";

        PreparedStatement stmt = connection.prepareStatement(update);
        long now = System.currentTimeMillis();
        stmt.setLong(1, now);
        stmt.setLong(2, now);

        int affected = stmt.executeUpdate();
        stmt.close();

        return affected;
    }

    // ============================================
    // PUNISHMENT REASONS
    // ============================================

    public static List<String> getPunishmentReasons(String type) throws SQLException {
        String query = "SELECT name FROM punishment_reasons WHERE type = ? AND enabled = 1 " +
                "ORDER BY sort_order";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, type);

        ResultSet rs = stmt.executeQuery();
        List<String> reasons = new ArrayList<>();

        while (rs.next()) {
            reasons.add(rs.getString("name"));
        }

        rs.close();
        stmt.close();

        return reasons;
    }

    public void addPunishmentReason(String type, String reason, String duration) throws SQLException {
        addPunishmentReason(type, reason, duration, "", 5);
    }

    public void addPunishmentReason(String type, String reason, String duration,
                                    String description, int severity) throws SQLException {
        String maxOrderQuery = "SELECT MAX(sort_order) as max_order FROM punishment_reasons WHERE type = ?";
        PreparedStatement maxStmt = connection.prepareStatement(maxOrderQuery);
        maxStmt.setString(1, type);
        ResultSet rs = maxStmt.executeQuery();

        int nextOrder = 0;
        if (rs.next()) {
            nextOrder = rs.getInt("max_order") + 1;
        }
        rs.close();
        maxStmt.close();

        String insert = "INSERT INTO punishment_reasons (type, name, duration, description, severity, enabled, sort_order, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
        PreparedStatement stmt = connection.prepareStatement(insert);
        stmt.setString(1, type);
        stmt.setString(2, reason);
        stmt.setString(3, duration);
        stmt.setString(4, description);
        stmt.setInt(5, severity);
        stmt.setInt(6, nextOrder);
        stmt.setLong(7, System.currentTimeMillis());

        stmt.executeUpdate();
        stmt.close();
    }

    public void removePunishmentReason(int reasonId) throws SQLException {
        String delete = "DELETE FROM punishment_reasons WHERE reason_id = ?";
        PreparedStatement stmt = connection.prepareStatement(delete);
        stmt.setInt(1, reasonId);
        stmt.executeUpdate();
        stmt.close();
    }

    public List<PunishmentManager.PunishmentReason> getPunishmentReasonsByType(String type)
            throws SQLException {
        String query = "SELECT reason_id, type, name, duration, severity FROM punishment_reasons " +
                "WHERE type = ? AND enabled = 1 ORDER BY sort_order";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, type);
        ResultSet rs = stmt.executeQuery();

        List<PunishmentManager.PunishmentReason> reasons = new ArrayList<>();
        while (rs.next()) {
            reasons.add(new PunishmentManager.PunishmentReason(
                    rs.getInt("reason_id"),
                    rs.getString("type"),
                    rs.getString("name"),
                    rs.getString("duration"),
                    rs.getInt("severity")
            ));
        }

        rs.close();
        stmt.close();

        return reasons;
    }

    public PunishmentManager.PunishmentReason getPunishmentReasonByName(String reasonName)
            throws SQLException {
        String query = "SELECT reason_id, type, name, duration, severity FROM punishment_reasons " +
                "WHERE name = ? AND enabled = 1 LIMIT 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, reasonName);
        ResultSet rs = stmt.executeQuery();

        PunishmentManager.PunishmentReason reason = null;
        if (rs.next()) {
            reason = new PunishmentManager.PunishmentReason(
                    rs.getInt("reason_id"),
                    rs.getString("type"),
                    rs.getString("name"),
                    rs.getString("duration"),
                    rs.getInt("severity")
            );
        }

        rs.close();
        stmt.close();

        return reason;
    }

    public int getPunishmentReasonCount() throws SQLException {
        String query = "SELECT COUNT(*) as count FROM punishment_reasons";
        PreparedStatement stmt = connection.prepareStatement(query);
        ResultSet rs = stmt.executeQuery();

        int count = 0;
        if (rs.next()) {
            count = rs.getInt("count");
        }

        rs.close();
        stmt.close();

        return count;
    }

    // ============================================
    // VIOLATIONS
    // ============================================

    public void recordViolation(String uuid, String playerName, String reason,
                                String punishmentType, String executor) throws SQLException {
        String insert = "INSERT INTO violations (uuid, player_name, reason, punishment_type, executor, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = connection.prepareStatement(insert);
        stmt.setString(1, uuid);
        stmt.setString(2, playerName);
        stmt.setString(3, reason);
        stmt.setString(4, punishmentType);
        stmt.setString(5, executor);
        stmt.setLong(6, System.currentTimeMillis());

        stmt.executeUpdate();
        stmt.close();
    }

    public int getViolationCount(String uuid, String reason) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM violations WHERE uuid = ? AND reason = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, uuid);
        stmt.setString(2, reason);

        ResultSet rs = stmt.executeQuery();
        int count = 0;
        if (rs.next()) {
            count = rs.getInt("count");
        }

        rs.close();
        stmt.close();

        return count;
    }

    public List<ViolationRecord> getViolationHistory(String uuid) throws SQLException {
        String query = "SELECT violation_id, reason, punishment_type, executor, created_at " +
                "FROM violations WHERE uuid = ? ORDER BY created_at DESC";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, uuid);
        ResultSet rs = stmt.executeQuery();

        List<ViolationRecord> violations = new ArrayList<>();
        while (rs.next()) {
            violations.add(new ViolationRecord(
                    rs.getInt("violation_id"),
                    rs.getString("reason"),
                    rs.getString("punishment_type"),
                    rs.getString("executor"),
                    rs.getLong("created_at")
            ));
        }

        rs.close();
        stmt.close();

        return violations;
    }

    // ============================================
    // PUNISHMENT POINTS SYSTEM
    // ============================================

    public void addPunishmentPoints(String uuid, String playerName, int points, String reason,
                                    int severity, String executor) throws SQLException {
        String insert = "INSERT INTO punishment_points (uuid, player_name, points, reason, " +
                "severity, executor, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = connection.prepareStatement(insert);
        stmt.setString(1, uuid);
        stmt.setString(2, playerName);
        stmt.setInt(3, points);
        stmt.setString(4, reason);
        stmt.setInt(5, severity);
        stmt.setString(6, executor);
        stmt.setLong(7, System.currentTimeMillis());

        long expiresAt = System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000);
        stmt.setLong(8, expiresAt);

        stmt.executeUpdate();
        stmt.close();
    }

    public int getTotalPunishmentPoints(String uuid) throws SQLException {
        String query = "SELECT SUM(points) as total FROM punishment_points " +
                "WHERE uuid = ? AND expires_at > ? AND active = 1";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, uuid);
        stmt.setLong(2, System.currentTimeMillis());

        ResultSet rs = stmt.executeQuery();
        int total = 0;
        if (rs.next()) {
            total = rs.getInt("total");
        }

        rs.close();
        stmt.close();

        return total;
    }

    public long getLastViolationTime(String uuid, String reason) throws SQLException {
        String query = "SELECT MAX(created_at) as last_time FROM punishment_points " +
                "WHERE uuid = ? AND reason = ?";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, uuid);
        stmt.setString(2, reason);

        ResultSet rs = stmt.executeQuery();
        long lastTime = 0;
        if (rs.next()) {
            lastTime = rs.getLong("last_time");
        }

        rs.close();
        stmt.close();

        return lastTime;
    }

    public boolean reducePunishmentPoints(String uuid, int pointsToReduce, String reason,
                                          String executor) throws SQLException {
        String query = "SELECT point_id, points FROM punishment_points " +
                "WHERE uuid = ? AND active = 1 AND expires_at > ? ORDER BY created_at ASC";

        PreparedStatement selectStmt = connection.prepareStatement(query);
        selectStmt.setString(1, uuid);
        selectStmt.setLong(2, System.currentTimeMillis());

        ResultSet rs = selectStmt.executeQuery();

        int remaining = pointsToReduce;
        List<Integer> toDeactivate = new ArrayList<>();

        while (rs.next() && remaining > 0) {
            int pointId = rs.getInt("point_id");
            int points = rs.getInt("points");

            if (points <= remaining) {
                toDeactivate.add(pointId);
                remaining -= points;
            } else {
                String update = "UPDATE punishment_points SET points = ? WHERE point_id = ?";
                PreparedStatement updateStmt = connection.prepareStatement(update);
                updateStmt.setInt(1, points - remaining);
                updateStmt.setInt(2, pointId);
                updateStmt.executeUpdate();
                updateStmt.close();
                remaining = 0;
            }
        }

        rs.close();
        selectStmt.close();

        if (!toDeactivate.isEmpty()) {
            String deactivate = "UPDATE punishment_points SET active = 0 WHERE point_id = ?";
            PreparedStatement deactivateStmt = connection.prepareStatement(deactivate);

            for (int pointId : toDeactivate) {
                deactivateStmt.setInt(1, pointId);
                deactivateStmt.addBatch();
            }

            deactivateStmt.executeBatch();
            deactivateStmt.close();
        }

        String log = "INSERT INTO punishment_point_reductions (uuid, points_reduced, reason, executor, created_at) " +
                "VALUES (?, ?, ?, ?, ?)";
        PreparedStatement logStmt = connection.prepareStatement(log);
        logStmt.setString(1, uuid);
        logStmt.setInt(2, pointsToReduce - remaining);
        logStmt.setString(3, reason);
        logStmt.setString(4, executor);
        logStmt.setLong(5, System.currentTimeMillis());
        logStmt.executeUpdate();
        logStmt.close();

        return true;
    }

    public List<PunishmentPointEntry> getPunishmentPointHistory(String uuid) throws SQLException {
        String query = "SELECT point_id, points, reason, severity, executor, created_at, " +
                "expires_at, active FROM punishment_points WHERE uuid = ? ORDER BY created_at DESC";

        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, uuid);

        ResultSet rs = stmt.executeQuery();
        List<PunishmentPointEntry> history = new ArrayList<>();

        while (rs.next()) {
            history.add(new PunishmentPointEntry(
                    rs.getInt("point_id"),
                    rs.getInt("points"),
                    rs.getString("reason"),
                    rs.getInt("severity"),
                    rs.getString("executor"),
                    rs.getLong("created_at"),
                    rs.getLong("expires_at"),
                    rs.getBoolean("active")
            ));
        }

        rs.close();
        stmt.close();

        return history;
    }

    public int cleanupExpiredPoints() throws SQLException {
        String update = "UPDATE punishment_points SET active = 0 " +
                "WHERE expires_at < ? AND active = 1";

        PreparedStatement stmt = connection.prepareStatement(update);
        stmt.setLong(1, System.currentTimeMillis());

        int affected = stmt.executeUpdate();
        stmt.close();

        return affected;
    }

    // ============================================
    // WARNS
    // ============================================

    public static int createWarn(String minecraftUuid, String minecraftName, String reason, String warnerName)
            throws SQLException {
        String insert = "INSERT INTO warns (uuid, name, reason, warner, created_at) VALUES (?, ?, ?, ?, ?)";

        PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, minecraftUuid);
        stmt.setString(2, minecraftName);
        stmt.setString(3, reason);
        stmt.setString(4, warnerName);
        stmt.setLong(5, System.currentTimeMillis());

        stmt.executeUpdate();

        ResultSet generatedKeys = stmt.getGeneratedKeys();
        int warnId = 0;
        if (generatedKeys.next()) {
            warnId = generatedKeys.getInt(1);
        }

        generatedKeys.close();
        stmt.close();

        return warnId;
    }

    public static int getWarnCount(String minecraftUuid) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM warns WHERE uuid = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftUuid);

        ResultSet rs = stmt.executeQuery();
        int count = 0;
        if (rs.next()) {
            count = rs.getInt("count");
        }

        rs.close();
        stmt.close();

        return count;
    }

    // ============================================
    // KICKS
    // ============================================

    public static int createKick(String minecraftUuid, String minecraftName, String reason, String kickerName)
            throws SQLException {
        String insert = "INSERT INTO kicks (uuid, name, reason, kicker, created_at) VALUES (?, ?, ?, ?, ?)";

        PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, minecraftUuid);
        stmt.setString(2, minecraftName);
        stmt.setString(3, reason);
        stmt.setString(4, kickerName);
        stmt.setLong(5, System.currentTimeMillis());

        stmt.executeUpdate();

        ResultSet generatedKeys = stmt.getGeneratedKeys();
        int kickId = 0;
        if (generatedKeys.next()) {
            kickId = generatedKeys.getInt(1);
        }

        generatedKeys.close();
        stmt.close();

        return kickId;
    }

    public int getKickCount(String minecraftUuid) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM kicks WHERE uuid = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, minecraftUuid);

        ResultSet rs = stmt.executeQuery();
        int count = 0;
        if (rs.next()) {
            count = rs.getInt("count");
        }

        rs.close();
        stmt.close();

        return count;
    }

    // ============================================
    // VANISH
    // ============================================

    public void setVanishStatus(int userId, boolean isVanished) throws SQLException {
        String check = "SELECT user_id FROM vanish WHERE user_id = ?";
        PreparedStatement checkStmt = connection.prepareStatement(check);
        checkStmt.setInt(1, userId);
        ResultSet rs = checkStmt.executeQuery();

        if (rs.next()) {
            String update = "UPDATE vanish SET is_vanished = ?, vanish_start_time = ? WHERE user_id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(update);
            updateStmt.setBoolean(1, isVanished);
            updateStmt.setLong(2, isVanished ? System.currentTimeMillis() : 0);
            updateStmt.setInt(3, userId);
            updateStmt.executeUpdate();
            updateStmt.close();
        } else {
            String insert = "INSERT INTO vanish (user_id, is_vanished, vanish_start_time) VALUES (?, ?, ?)";
            PreparedStatement insertStmt = connection.prepareStatement(insert);
            insertStmt.setInt(1, userId);
            insertStmt.setBoolean(2, isVanished);
            insertStmt.setLong(3, isVanished ? System.currentTimeMillis() : 0);
            insertStmt.executeUpdate();
            insertStmt.close();
        }

        rs.close();
        checkStmt.close();
    }

    public boolean getVanishStatus(int userId) throws SQLException {
        String query = "SELECT is_vanished FROM vanish WHERE user_id = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();

        boolean isVanished = false;
        if (rs.next()) {
            isVanished = rs.getBoolean("is_vanished");
        }

        rs.close();
        stmt.close();
        return isVanished;
    }

    public long getVanishStartTime(int userId) throws SQLException {
        String query = "SELECT vanish_start_time FROM vanish WHERE user_id = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();

        long startTime = 0;
        if (rs.next()) {
            startTime = rs.getLong("vanish_start_time");
        }

        rs.close();
        stmt.close();
        return startTime;
    }

    // ============================================
    // USERS
    // ============================================

    public Connection getConnection() {
        return connection;
    }

    public int createOrGetUser(String discordId) throws SQLException {
        String query = "SELECT internal_id FROM users WHERE discord_id = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, discordId);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            int id = rs.getInt("internal_id");
            rs.close();
            stmt.close();
            return id;
        }

        rs.close();
        stmt.close();

        String insert = "INSERT INTO users (discord_id, created_at) VALUES (?, ?)";
        PreparedStatement insertStmt = connection.prepareStatement(insert);
        insertStmt.setString(1, discordId);
        insertStmt.setLong(2, System.currentTimeMillis());
        insertStmt.executeUpdate();

        ResultSet generatedKeys = insertStmt.getGeneratedKeys();
        int newId = 0;
        if (generatedKeys.next()) {
            newId = generatedKeys.getInt(1);
        }

        generatedKeys.close();
        insertStmt.close();
        return newId;
    }

    public void updateMinecraftData(int userId, String minecraftName, UUID minecraftUUID) throws SQLException {
        String update = "UPDATE users SET minecraft_name = ?, minecraft_uuid = ? WHERE internal_id = ?";
        PreparedStatement stmt = connection.prepareStatement(update);
        stmt.setString(1, minecraftName);
        stmt.setString(2, minecraftUUID.toString());
        stmt.setInt(3, userId);
        stmt.executeUpdate();
        stmt.close();
    }

    public void updateWhitelistStatus(int userId, String status) throws SQLException {
        String update = "UPDATE users SET whitelist_status = ? WHERE internal_id = ?";
        PreparedStatement stmt = connection.prepareStatement(update);
        stmt.setString(1, status);
        stmt.setInt(2, userId);
        stmt.executeUpdate();
        stmt.close();
    }

    public void setUserOnServer(String discordId, boolean onServer) throws SQLException {
        String update = "UPDATE users SET is_on_server = ? WHERE discord_id = ?";
        PreparedStatement stmt = connection.prepareStatement(update);
        stmt.setBoolean(1, onServer);
        stmt.setString(2, discordId);
        stmt.executeUpdate();
        stmt.close();
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    public static class BanInfo {
        public final int banId;
        public final String reason;
        public final long expireTime;
        public final String bannerName;

        public BanInfo(int banId, String reason, long expireTime, String bannerName) {
            this.banId = banId;
            this.reason = reason;
            this.expireTime = expireTime;
            this.bannerName = bannerName;
        }
    }

    public static class MuteInfo {
        public final int muteId;
        public final String reason;
        public final long expireTime;
        public final String muterName;

        public MuteInfo(int muteId, String reason, long expireTime, String muterName) {
            this.muteId = muteId;
            this.reason = reason;
            this.expireTime = expireTime;
            this.muterName = muterName;
        }
    }

    public static class PunishmentPointEntry {
        public final int pointId;
        public final int points;
        public final String reason;
        public final int severity;
        public final String executor;
        public final long createdAt;
        public final long expiresAt;
        public final boolean active;

        public PunishmentPointEntry(int pointId, int points, String reason, int severity,
                                    String executor, long createdAt, long expiresAt, boolean active) {
            this.pointId = pointId;
            this.points = points;
            this.reason = reason;
            this.severity = severity;
            this.executor = executor;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.active = active;
        }
    }
}