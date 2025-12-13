package de.MaxBrassLoud.disMineBridgePlugin.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {

    private static DatabaseManager instance;
    private final JavaPlugin plugin;
    private Connection connection;
    private DatabaseType dbType;
    private String host, database, username, password;
    private int port;

    public enum DatabaseType {
        SQLITE, MYSQL
    }

    private DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new DatabaseManager(plugin);
            instance.loadConfig();
            instance.connect();
            instance.createTables();
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DatabaseManager wurde nicht initialisiert!");
        }
        return instance;
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        String type = config.getString("database.type", "sqlite").toLowerCase();
        dbType = type.equals("mysql") ? DatabaseType.MYSQL : DatabaseType.SQLITE;

        if (dbType == DatabaseType.MYSQL) {
            host = config.getString("database.mysql.host", "localhost");
            port = config.getInt("database.mysql.port", 3306);
            database = config.getString("database.mysql.database", "disminebridge");
            username = config.getString("database.mysql.username", "root");
            password = config.getString("database.mysql.password", "");
        }

        plugin.getLogger().info("Datenbank-Typ: " + dbType.name());
    }

    private void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            if (dbType == DatabaseType.MYSQL) {
                connectMySQL();
            } else {
                connectSQLite();
            }

            plugin.getLogger().info("✔ Datenbankverbindung hergestellt (" + dbType.name() + ")");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Fehler beim Verbinden zur Datenbank:", e);
        }
    }

    private void connectSQLite() throws SQLException, ClassNotFoundException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File dbFile = new File(dataFolder, "database.db");
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private void connectMySQL() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
        connection = DriverManager.getConnection(url, username, password);
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
            if (dbType == DatabaseType.MYSQL && !connection.isValid(3)) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Datenbankverbindung verloren, versuche neu zu verbinden...", e);
            connect();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("✔ Datenbankverbindung geschlossen.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Schließen der Datenbank:", e);
        }
    }

    public DatabaseType getType() {
        return dbType;
    }

    public boolean isMySQL() {
        return dbType == DatabaseType.MYSQL;
    }

    // ============================================================
    //  TABLE CREATION - ERWEITERT
    // ============================================================

    private void createTables() {
        try (Statement stmt = getConnection().createStatement()) {

            stmt.execute(getBansTableSQL());
            stmt.execute(getWarnsTableSQL());
            stmt.execute(getKicksTableSQL());
            stmt.execute(getMutesTableSQL());
            stmt.execute(getUsersTableSQL());
            stmt.execute(getWhitelistTableSQL());
            stmt.execute(getAdminModeTableSQL());

            // NEU: Player Data Tabelle für Inventar-Speicherung
            stmt.execute(getPlayerDataTableSQL());

            // NEU: Offline Punishments Tabelle
            stmt.execute(getOfflinePunishmentsTableSQL());

            // NEU: Punishment Reasons Tabelle
            stmt.execute(getPunishmentReasonsTableSQL());

            // Füge Standard-Gründe ein (nur einmal)
            insertDefaultPunishmentReasons();

            plugin.getLogger().info("✔ Alle Tabellen erfolgreich erstellt/geladen.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Fehler beim Erstellen der Tabellen:", e);
        }
    }

    // ===== NEUE TABELLEN =====

    private String getPlayerDataTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16),
                    inventory MEDIUMTEXT,
                    armor TEXT,
                    enderchest MEDIUMTEXT,
                    location_world VARCHAR(100),
                    location_x DOUBLE,
                    location_y DOUBLE,
                    location_z DOUBLE,
                    location_yaw FLOAT,
                    location_pitch FLOAT,
                    gamemode VARCHAR(20),
                    health DOUBLE,
                    food_level INT,
                    exp FLOAT,
                    level INT,
                    first_join BIGINT,
                    last_seen BIGINT,
                    last_updated BIGINT
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid TEXT PRIMARY KEY,
                name TEXT,
                inventory TEXT,
                armor TEXT,
                enderchest TEXT,
                location_world TEXT,
                location_x REAL,
                location_y REAL,
                location_z REAL,
                location_yaw REAL,
                location_pitch REAL,
                gamemode TEXT,
                health REAL,
                food_level INTEGER,
                exp REAL,
                level INTEGER,
                first_join INTEGER,
                last_seen INTEGER,
                last_updated INTEGER
            )
        """;
    }

    private String getOfflinePunishmentsTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS offline_punishments (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36),
                    name VARCHAR(16),
                    type VARCHAR(20),
                    reason TEXT,
                    punisher VARCHAR(16),
                    duration BIGINT,
                    notified BOOLEAN DEFAULT FALSE,
                    created_at BIGINT
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS offline_punishments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                name TEXT,
                type TEXT,
                reason TEXT,
                punisher TEXT,
                duration INTEGER,
                notified INTEGER DEFAULT 0,
                created_at INTEGER
            )
        """;
    }

    // ===== BESTEHENDE TABELLEN =====

    private String getBansTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS bans (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36),
                    name VARCHAR(16),
                    reason TEXT,
                    banner VARCHAR(16),
                    expire BIGINT,
                    pardon BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS bans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                name TEXT,
                reason TEXT,
                banner TEXT,
                expire INTEGER,
                pardon INTEGER DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
    }

    private String getWarnsTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS warns (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36),
                    name VARCHAR(16),
                    reason TEXT,
                    warner VARCHAR(16),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS warns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                name TEXT,
                reason TEXT,
                warner TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
    }

    private String getKicksTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS kicks (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36),
                    name VARCHAR(16),
                    reason TEXT,
                    kicker VARCHAR(16),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS kicks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                name TEXT,
                reason TEXT,
                kicker TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
    }

    private String getMutesTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS mutes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36),
                    reason TEXT,
                    expire BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS mutes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                reason TEXT,
                expire INTEGER,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
    }

    private String getUsersTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS users (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16),
                    vanish BOOLEAN DEFAULT FALSE,
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS users (
                uuid TEXT PRIMARY KEY,
                name TEXT,
                vanish INTEGER DEFAULT 0,
                first_join DATETIME DEFAULT CURRENT_TIMESTAMP,
                last_seen DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
    }

    private String getWhitelistTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS whitelist (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(16) NOT NULL UNIQUE,
                    uuid VARCHAR(36) UNIQUE,
                    discord_id VARCHAR(20),
                    bypass_maintenance BOOLEAN DEFAULT FALSE,
                    added_by VARCHAR(16),
                    added_at BIGINT,
                    last_updated BIGINT
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS whitelist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                uuid TEXT UNIQUE,
                discord_id TEXT,
                bypass_maintenance INTEGER DEFAULT 0,
                added_by TEXT,
                added_at INTEGER,
                last_updated INTEGER
            )
        """;
    }

    private String getAdminModeTableSQL() {
        if (isMySQL()) {
            return """
                CREATE TABLE IF NOT EXISTS adminmode_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    gamemode VARCHAR(20),
                    health DOUBLE,
                    food_level INT,
                    saturation FLOAT,
                    exhaustion FLOAT,
                    exp FLOAT,
                    level INT,
                    fly_speed FLOAT,
                    walk_speed FLOAT,
                    world VARCHAR(100),
                    loc_x DOUBLE,
                    loc_y DOUBLE,
                    loc_z DOUBLE,
                    loc_yaw FLOAT,
                    loc_pitch FLOAT,
                    inventory MEDIUMTEXT,
                    armor TEXT,
                    enderchest MEDIUMTEXT,
                    potion_effects TEXT,
                    timestamp BIGINT
                )
            """;
        }
        return """
            CREATE TABLE IF NOT EXISTS adminmode_data (
                uuid TEXT PRIMARY KEY,
                gamemode TEXT,
                health REAL,
                food_level INTEGER,
                saturation REAL,
                exhaustion REAL,
                exp REAL,
                level INTEGER,
                fly_speed REAL,
                walk_speed REAL,
                world TEXT,
                loc_x REAL,
                loc_y REAL,
                loc_z REAL,
                loc_yaw REAL,
                loc_pitch REAL,
                inventory TEXT,
                armor TEXT,
                enderchest TEXT,
                potion_effects TEXT,
                timestamp INTEGER
            )
        """;
    }

    private String getPunishmentReasonsTableSQL() {
        if (isMySQL()) {
            return """
            CREATE TABLE IF NOT EXISTS punishment_reasons (
                id INT AUTO_INCREMENT PRIMARY KEY,
                type VARCHAR(20) NOT NULL,
                name VARCHAR(100) NOT NULL,
                description TEXT,
                default_duration BIGINT NOT NULL,
                severity INT DEFAULT 1,
                enabled BOOLEAN DEFAULT TRUE,
                sort_order INT DEFAULT 0,
                created_at BIGINT,
                created_by VARCHAR(16)
            )
        """;
        }
        return """
        CREATE TABLE IF NOT EXISTS punishment_reasons (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            name TEXT NOT NULL,
            description TEXT,
            default_duration INTEGER NOT NULL,
            severity INTEGER DEFAULT 1,
            enabled INTEGER DEFAULT 1,
            sort_order INTEGER DEFAULT 0,
            created_at INTEGER,
            created_by TEXT
        )
    """;
    }

    private void insertDefaultPunishmentReasons() {
        try {
            // Prüfe ob schon Gründe existieren
            String checkSql = "SELECT COUNT(*) as count FROM punishment_reasons";
            PreparedStatement ps = prepareStatement(checkSql);
            ResultSet rs = ps.executeQuery();

            if (rs.next() && rs.getInt("count") > 0) {
                rs.close();
                ps.close();
                return; // Bereits Daten vorhanden
            }
            rs.close();
            ps.close();

            // Füge Standard-Gründe ein (siehe SQL-Datei)
            String sql = "INSERT INTO punishment_reasons (type, name, description, default_duration, severity, sort_order) VALUES (?, ?, ?, ?, ?, ?)";

            // BAN Gründe
            executeUpdate(sql, "BAN", "Hacking", "Verwendung von Hacking-Clients", 604800000L, 5, 1);
            executeUpdate(sql, "BAN", "Griefing", "Absichtliche Zerstörung", 259200000L, 4, 2);
            // ... weitere Gründe

            plugin.getLogger().info("✔ Standard-Gründe wurden eingefügt.");

        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Einfügen der Standard-Gründe: " + e.getMessage());
        }
    }

    // ============================================================
    //  UTILITY METHODS
    // ============================================================

    public int executeUpdate(String sql, Object... params) {
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setParameters(stmt, params);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQL Fehler: " + sql, e);
            return -1;
        }
    }

    public PreparedStatement prepareStatement(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = getConnection().prepareStatement(sql);
        setParameters(stmt, params);
        return stmt;
    }

    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (param instanceof String s) {
                stmt.setString(i + 1, s);
            } else if (param instanceof Integer n) {
                stmt.setInt(i + 1, n);
            } else if (param instanceof Long l) {
                stmt.setLong(i + 1, l);
            } else if (param instanceof Double d) {
                stmt.setDouble(i + 1, d);
            } else if (param instanceof Float f) {
                stmt.setFloat(i + 1, f);
            } else if (param instanceof Boolean b) {
                stmt.setBoolean(i + 1, b);
            } else {
                stmt.setObject(i + 1, param);
            }
        }
    }

    public String getUpsertSQL(String table, String[] columns, String primaryKey) {
        StringBuilder sb = new StringBuilder();
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.length, "?"));
        String columnList = String.join(", ", columns);

        if (isMySQL()) {
            sb.append("INSERT INTO ").append(table).append(" (").append(columnList).append(") ");
            sb.append("VALUES (").append(placeholders).append(") ");
            sb.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < columns.length; i++) {
                if (!columns[i].equals(primaryKey)) {
                    if (i > 0) sb.append(", ");
                    sb.append(columns[i]).append(" = VALUES(").append(columns[i]).append(")");
                }
            }
        } else {
            sb.append("INSERT INTO ").append(table).append(" (").append(columnList).append(") ");
            sb.append("VALUES (").append(placeholders).append(") ");
            sb.append("ON CONFLICT(").append(primaryKey).append(") DO UPDATE SET ");
            boolean first = true;
            for (String col : columns) {
                if (!col.equals(primaryKey)) {
                    if (!first) sb.append(", ");
                    sb.append(col).append(" = excluded.").append(col);
                    first = false;
                }
            }
        }

        return sb.toString();
    }
}