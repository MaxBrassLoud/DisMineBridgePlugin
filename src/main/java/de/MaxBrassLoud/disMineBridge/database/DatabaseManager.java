package de.MaxBrassLoud.disMineBridge.database;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private final DisMineBridge plugin;
    private Connection connection;

    public DatabaseManager(DisMineBridge plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            boolean useLocal = plugin.getConfig().getBoolean("database.use-local", true);

            if (useLocal) {
                // SQLite Datenbank
                String file = plugin.getConfig().getString("database.local.file", "disminebridge.db");
                String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/" + file;
                connection = DriverManager.getConnection(url);
            } else {
                // MySQL Datenbank
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

        Statement stmt = connection.createStatement();
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

        // Persistente Komponenten (für Buttons/Modals)
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
        // Füge diese Methode zur createTables() Methode in DatabaseManager.java hinzu
// Direkt nach der setupTable vor stmt.close();

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

        String bansTable = "CREATE TABLE IF NOT EXISTS bans (" +
                "ban_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
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
                "pardon_reason TEXT," +
                "FOREIGN KEY (user_id) REFERENCES users(internal_id)," +
                "FOREIGN KEY (banner_id) REFERENCES users(internal_id)," +
                "FOREIGN KEY (pardoned_by) REFERENCES users(internal_id)" +
                ")";

        if (!plugin.getConfig().getBoolean("database.use-local")) {
            bansTable = bansTable.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT")
                    .replace("INTEGER NOT NULL", "INT NOT NULL")
                    .replace("INTEGER,", "INT,")
                    .replace("BOOLEAN", "TINYINT(1)");
        }

        stmt.execute(bansTable);

// Punishment Reasons Tabelle (für vordefinierte Gründe)
        String reasonsTable = "CREATE TABLE IF NOT EXISTS punishment_reasons (" +
                "reason_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "default_duration TEXT," +
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
    }

// Zusätzliche Ban-Methoden für DatabaseManager am Ende der Klasse:

        public int createBan(int userId, String minecraftUuid, String minecraftName, String reason,
        int bannerId, String bannerName, long expireTime) throws SQLException {
            String insert = "INSERT INTO bans (user_id, minecraft_uuid, minecraft_name, reason, " +
                    "banner_id, banner_name, expire_time, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setString(2, minecraftUuid);
            stmt.setString(3, minecraftName);
            stmt.setString(4, reason);
            stmt.setInt(5, bannerId);
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
            String query = "SELECT ban_id FROM bans WHERE minecraft_uuid = ? AND is_pardoned = 0 " +
                    "AND expire_time > ? ORDER BY expire_time DESC LIMIT 1";

            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, minecraftUuid);
            stmt.setLong(2, System.currentTimeMillis());

            ResultSet rs = stmt.executeQuery();
            boolean hasBan = rs.next();

            rs.close();
            stmt.close();

            return hasBan;
        }

        public BanInfo getActiveBan(String minecraftUuid) throws SQLException {
            String query = "SELECT ban_id, reason, expire_time, banner_name FROM bans " +
                    "WHERE minecraft_uuid = ? AND is_pardoned = 0 AND expire_time > ? " +
                    "ORDER BY expire_time DESC LIMIT 1";

            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, minecraftUuid);
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

        public void pardonBan(int banId, int pardonerId, String pardonReason) throws SQLException {
            String update = "UPDATE bans SET is_pardoned = 1, pardoned_by = ?, pardoned_at = ?, " +
                    "pardon_reason = ? WHERE ban_id = ?";

            PreparedStatement stmt = connection.prepareStatement(update);
            stmt.setInt(1, pardonerId);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, pardonReason);
            stmt.setInt(4, banId);

            stmt.executeUpdate();
            stmt.close();
        }

        public List<String> getPunishmentReasons(String type) throws SQLException {
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

// BanInfo inner class (füge diese Klasse am Ende der DatabaseManager Klasse hinzu)
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





// Zusätzliche Methoden für VanishManager am Ende der DatabaseManager Klasse:

    public void setVanishStatus(int userId, boolean isVanished) throws SQLException {
        String check = "SELECT user_id FROM vanish WHERE user_id = ?";
        PreparedStatement checkStmt = connection.prepareStatement(check);
        checkStmt.setInt(1, userId);
        ResultSet rs = checkStmt.executeQuery();

        if (rs.next()) {
            // Update existing entry
            String update = "UPDATE vanish SET is_vanished = ?, vanish_start_time = ? WHERE user_id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(update);
            updateStmt.setBoolean(1, isVanished);
            updateStmt.setLong(2, isVanished ? System.currentTimeMillis() : 0);
            updateStmt.setInt(3, userId);
            updateStmt.executeUpdate();
            updateStmt.close();
        } else {
            // Insert new entry
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




    public Connection getConnection() {
        return connection;
    }

    // User Methoden
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
}