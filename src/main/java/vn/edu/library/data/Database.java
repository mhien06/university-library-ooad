package vn.edu.library.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private static final String HOST = env("DB_HOST", "localhost");
    private static final String PORT = env("DB_PORT", "3307");
    private static final String NAME = env("DB_NAME", "university_library");
    private static final String USER = env("DB_USER", "library_app");
    private static final String PASSWORD = env("DB_PASSWORD", "change-this-app-password");
    private static final String URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + NAME
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh";

    private Database() { }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void checkConnection() throws SQLException {
        try (Connection ignored = getConnection()) { }
    }
}
