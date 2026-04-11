package storage;

// Manages a single shared JDBC connection to the icarus_db MySQL database.
// Credentials are read from config.properties — never hardcoded.

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    private static Connection connection = null;

    private DBConnection() {}

    // Returns the shared connection, creating or rebuilding it if needed.
    public static Connection get() throws SQLException {
        try {
            if (connection == null || connection.isClosed()) {
                connection = createConnection();
            }
        } catch (SQLException e) {
            connection = createConnection();
        }
        return connection;
    }

    private static Connection createConnection() throws SQLException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
        } catch (IOException e) {
            throw new SQLException("Could not read config.properties: " + e.getMessage());
        }

        String url      = props.getProperty("db.url");
        String user     = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        if (url == null || user == null || password == null)
            throw new SQLException("config.properties is missing db.url, db.user, or db.password");

        return DriverManager.getConnection(url, user, password);
    }

    // Call on application shutdown to close cleanly.
    public static void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Warning: error closing DB connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }
}