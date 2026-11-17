package vn.edu.library.data;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class BootstrapService {
    private BootstrapService() { }

    public static void ensureAdministrator() throws Exception {
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM account_roles WHERE role = 'ADMIN'");
                 ResultSet rows = query.executeQuery()) {
                rows.next();
                if (rows.getLong(1) > 0) return;
            }

            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long staffId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO staff_profiles (staff_code, full_name, email, job_title) "
                                + "VALUES ('LIB-ADMIN-001','Quản trị viên hệ thống','admin@library.local','ADMIN')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) { keys.next(); staffId = keys.getLong(1); }
                }

                String username = env("LIBRARY_ADMIN_USERNAME", "admin");
                String password = env("LIBRARY_ADMIN_PASSWORD", "Admin@123");
                long accountId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO accounts (username, password_hash, staff_id) VALUES (?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, username);
                    insert.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(12)));
                    insert.setLong(3, staffId);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) { keys.next(); accountId = keys.getLong(1); }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO account_roles (account_id, role) VALUES (?, 'ADMIN')")) {
                    insert.setLong(1, accountId);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
