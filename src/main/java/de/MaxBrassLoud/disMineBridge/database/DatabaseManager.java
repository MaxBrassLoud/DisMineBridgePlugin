package de.MaxBrassLoud.disMineBridge.database;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import java.sql.*;
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

        stmt.close();
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