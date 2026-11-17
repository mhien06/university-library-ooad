package vn.edu.library.data;

import org.mindrot.jbcrypt.BCrypt;
import vn.edu.library.domain.UserSession;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("[+0-9(). -]*");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{4,50}");
    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "LIBRARIAN", "WAREHOUSE");
    private static final Set<String> EMPLOYMENT_STATUSES = Set.of("ACTIVE", "INACTIVE");

    public UserSession authenticate(String username, char[] password) throws Exception {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return null;
        }
        String normalizedUsername = username.trim();
        String sql = "SELECT a.account_id, a.password_hash, a.account_status, s.employment_status, m.institution_status, m.valid_until, "
                + "COALESCE(s.full_name, m.full_name) AS display_name "
                + "FROM accounts a LEFT JOIN staff_profiles s ON s.staff_id=a.staff_id "
                + "LEFT JOIN school_members m ON m.member_id=a.member_id WHERE a.username=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !"ACTIVE".equals(row.getString("account_status"))) return null;
                String employmentStatus = row.getString("employment_status");
                String institutionStatus = row.getString("institution_status");
                java.sql.Date validUntil = row.getDate("valid_until");
                if (employmentStatus != null && !"ACTIVE".equals(employmentStatus)) return null;
                if (institutionStatus != null && !"ACTIVE".equals(institutionStatus)) return null;
                if (validUntil != null && validUntil.toLocalDate().isBefore(LocalDate.now())) return null;
                String hash = row.getString("password_hash");
                if (hash == null || !BCrypt.checkpw(new String(password), hash)) return null;
                long accountId = row.getLong("account_id");
                Set<String> roles = loadRoles(connection, accountId);
                if (roles.isEmpty()) return null;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE accounts SET last_login_at=? WHERE account_id=?")) {
                    update.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                    update.setLong(2, accountId);
                    update.executeUpdate();
                }
                return new UserSession(accountId, normalizedUsername, row.getString("display_name"), Set.copyOf(roles));
            }
        }
    }

    public record AccountProfile(String username, String fullName, String email, String phone, boolean staff) { }

    public AccountProfile profile(long accountId) throws Exception {
        String sql = "SELECT a.username, s.staff_id, s.full_name staff_name, s.email staff_email, s.phone staff_phone, "
                + "m.member_id, m.full_name member_name, m.email member_email, m.phone member_phone "
                + "FROM accounts a LEFT JOIN staff_profiles s ON s.staff_id=a.staff_id "
                + "LEFT JOIN school_members m ON m.member_id=a.member_id WHERE a.account_id=?";
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
            q.setLong(1, accountId);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Tài khoản không còn tồn tại.");
                boolean staff = r.getObject("staff_id") != null;
                return new AccountProfile(
                        r.getString("username"),
                        staff ? r.getString("staff_name") : r.getString("member_name"),
                        staff ? r.getString("staff_email") : r.getString("member_email"),
                        staff ? r.getString("staff_phone") : r.getString("member_phone"),
                        staff
                );
            }
        }
    }

    public String updateProfile(long accountId, String fullName, String email, String phone) throws Exception {
        String validEmail = validateEmail(email);
        String validPhone = validatePhone(phone);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                Long staffId = null;
                Long memberId = null;
                String currentMemberName = null;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT a.staff_id, a.member_id, m.full_name FROM accounts a "
                                + "LEFT JOIN school_members m ON m.member_id=a.member_id WHERE a.account_id=? FOR UPDATE")) {
                    q.setLong(1, accountId);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Tài khoản không còn tồn tại.");
                        long id = r.getLong(1);
                        if (!r.wasNull()) staffId = id;
                        id = r.getLong(2);
                        if (!r.wasNull()) memberId = id;
                        currentMemberName = r.getString(3);
                    }
                }
                ensureUniqueEmail(c, validEmail, staffId, memberId);
                if (staffId != null) {
                    String validName = validateFullName(fullName);
                    try (PreparedStatement q = c.prepareStatement(
                            "UPDATE staff_profiles SET full_name=?, email=?, phone=? WHERE staff_id=?")) {
                        q.setString(1, validName);
                        q.setString(2, validEmail);
                        q.setString(3, validPhone);
                        q.setLong(4, staffId);
                        q.executeUpdate();
                    }
                    c.commit();
                    return validName;
                }
                try (PreparedStatement q = c.prepareStatement(
                        "UPDATE school_members SET email=?, phone=? WHERE member_id=?")) {
                    q.setString(1, validEmail);
                    q.setString(2, validPhone);
                    q.setLong(3, memberId);
                    q.executeUpdate();
                }
                c.commit();
                return currentMemberName;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void changePassword(long accountId, char[] current, char[] next, char[] confirmation) throws Exception {
        validatePassword(next);
        if (confirmation == null || !Arrays.equals(next, confirmation)) {
            throw new IllegalArgumentException("Hai mật khẩu mới chưa khớp.");
        }
        try (Connection c = Database.getConnection()) {
            String hash;
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT password_hash FROM accounts WHERE account_id=? AND account_status='ACTIVE' FOR UPDATE")) {
                q.setLong(1, accountId);
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Tài khoản không còn hoạt động.");
                    hash = r.getString(1);
                }
            }
            if (current == null || hash == null || !BCrypt.checkpw(new String(current), hash)) {
                throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác.");
            }
            if (BCrypt.checkpw(new String(next), hash)) {
                throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
            }
            try (PreparedStatement q = c.prepareStatement("UPDATE accounts SET password_hash=? WHERE account_id=?")) {
                q.setString(1, BCrypt.hashpw(new String(next), BCrypt.gensalt(12)));
                q.setLong(2, accountId);
                q.executeUpdate();
            }
        }
    }

    public String createStaffAccount(String fullName, String email, String phone, String role,
                                     String username, char[] password) throws Exception {
        String validName = validateFullName(fullName);
        String validEmail = validateEmail(email);
        String validPhone = validatePhone(phone);
        String validRole = validateStaffRole(role);
        String validUsername = validateUsername(username);
        validatePassword(password);

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureUniqueEmail(c, validEmail, null, null);
                ensureUniqueUsername(c, validUsername);
                String staffCode = nextUniqueStaffCode(c);
                long staffId;
                try (PreparedStatement q = c.prepareStatement(
                        "INSERT INTO staff_profiles(staff_code, full_name, email, phone, job_title) VALUES(?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, staffCode);
                    q.setString(2, validName);
                    q.setString(3, validEmail);
                    q.setString(4, validPhone);
                    q.setString(5, validRole);
                    q.executeUpdate();
                    try (ResultSet r = q.getGeneratedKeys()) {
                        r.next();
                        staffId = r.getLong(1);
                    }
                }
                long accountId;
                try (PreparedStatement q = c.prepareStatement(
                        "INSERT INTO accounts(username, password_hash, staff_id) VALUES(?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, validUsername);
                    q.setString(2, BCrypt.hashpw(new String(password), BCrypt.gensalt(12)));
                    q.setLong(3, staffId);
                    q.executeUpdate();
                    try (ResultSet r = q.getGeneratedKeys()) {
                        r.next();
                        accountId = r.getLong(1);
                    }
                }
                try (PreparedStatement q = c.prepareStatement(
                        "INSERT INTO account_roles(account_id, role) VALUES(?,?)")) {
                    q.setLong(1, accountId);
                    q.setString(2, validRole);
                    q.executeUpdate();
                }
                c.commit();
                return staffCode;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public String updateStaffAccount(long staffId, long accountId, long actorAccountId, String fullName,
                                     String email, String phone, String nextRole, String nextEmployment) throws Exception {
        String validName = validateFullName(fullName);
        String validEmail = validateEmail(email);
        String validPhone = validatePhone(phone);
        String validRole = validateStaffRole(nextRole);
        String validEmployment = validateEmploymentStatus(nextEmployment);
        if (accountId == actorAccountId && "INACTIVE".equals(validEmployment)) {
            throw new IllegalArgumentException("Không thể tự ngừng hoạt động tài khoản đang đăng nhập.");
        }

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT staff_id FROM accounts WHERE account_id=? FOR UPDATE")) {
                    q.setLong(1, accountId);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next() || r.getLong(1) != staffId) {
                            throw new IllegalArgumentException("Tài khoản nhân viên không còn tồn tại.");
                        }
                    }
                }
                Set<String> currentRoles = loadRolesForUpdate(c, accountId);
                boolean wasAdmin = currentRoles.contains("ADMIN");
                boolean removingAdmin = wasAdmin && (!"ADMIN".equals(validRole) || "INACTIVE".equals(validEmployment));
                if (removingAdmin) {
                    if (accountId == actorAccountId) {
                        throw new IllegalArgumentException("Không thể gỡ quyền quản trị của chính mình.");
                    }
                    if (isActiveAdmin(c, accountId) && activeAdminCount(c) <= 1) {
                        throw new IllegalArgumentException("Không thể gỡ quyền hoặc ngừng hoạt động quản trị viên đang hoạt động cuối cùng.");
                    }
                }
                ensureUniqueEmail(c, validEmail, staffId, null);

                try (PreparedStatement q = c.prepareStatement(
                        "UPDATE staff_profiles SET full_name=?, email=?, phone=?, job_title=?, employment_status=? WHERE staff_id=?")) {
                    q.setString(1, validName);
                    q.setString(2, validEmail);
                    q.setString(3, validPhone);
                    q.setString(4, validRole);
                    q.setString(5, validEmployment);
                    q.setLong(6, staffId);
                    q.executeUpdate();
                }
                replaceRole(c, accountId, validRole);
                if ("INACTIVE".equals(validEmployment)) {
                    try (PreparedStatement q = c.prepareStatement(
                            "UPDATE accounts SET account_status='LOCKED', lock_reason='Nhân viên đã ngừng làm việc' WHERE account_id=?")) {
                        q.setLong(1, accountId);
                        q.executeUpdate();
                    }
                } else {
                    try (PreparedStatement q = c.prepareStatement(
                            "UPDATE accounts SET account_status='ACTIVE', lock_reason=NULL "
                                    + "WHERE account_id=? AND account_status='LOCKED' AND lock_reason='Nhân viên đã ngừng làm việc'")) {
                        q.setLong(1, accountId);
                        q.executeUpdate();
                    }
                }
                c.commit();
                return validName;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void changeStaffRole(long accountId, long actorAccountId, String nextRole) throws Exception {
        String validRole = validateStaffRole(nextRole);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long staffId;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT staff_id FROM accounts WHERE account_id=? FOR UPDATE")) {
                    q.setLong(1, accountId);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Tài khoản không còn tồn tại.");
                        long id = r.getLong(1);
                        if (r.wasNull() || id <= 0) {
                            throw new IllegalArgumentException("Tài khoản độc giả chỉ có vai trò READER; không thể chuyển sang vai trò nhân viên.");
                        }
                        staffId = id;
                    }
                }
                Set<String> currentRoles = loadRolesForUpdate(c, accountId);
                if (currentRoles.contains("ADMIN") && !"ADMIN".equals(validRole)) {
                    if (accountId == actorAccountId) {
                        throw new IllegalArgumentException("Không thể tự gỡ quyền ADMIN của chính mình.");
                    }
                    if (isActiveAdmin(c, accountId) && activeAdminCount(c) <= 1) {
                        throw new IllegalArgumentException("Không thể gỡ quyền ADMIN cuối cùng.");
                    }
                }
                replaceRole(c, accountId, validRole);
                try (PreparedStatement q = c.prepareStatement(
                        "UPDATE staff_profiles SET job_title=? WHERE staff_id=?")) {
                    q.setString(1, validRole);
                    q.setLong(2, staffId);
                    q.executeUpdate();
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void setAccountLocked(long accountId, long actorAccountId, boolean lock,
                                 String reason, boolean requireStaff) throws Exception {
        if (lock && accountId == actorAccountId) {
            throw new IllegalArgumentException("Không thể khóa tài khoản đang đăng nhập.");
        }
        String validReason = lock ? validateLockReason(reason) : null;
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String currentStatus;
                boolean hasStaff;
                String staffStatus;
                String memberStatus;
                java.sql.Date validUntil;
                String sql = "SELECT a.account_status, a.staff_id, s.employment_status, m.institution_status, m.valid_until "
                        + "FROM accounts a LEFT JOIN staff_profiles s ON s.staff_id=a.staff_id "
                        + "LEFT JOIN school_members m ON m.member_id=a.member_id WHERE a.account_id=? FOR UPDATE";
                try (PreparedStatement q = c.prepareStatement(sql)) {
                    q.setLong(1, accountId);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Tài khoản không còn tồn tại.");
                        currentStatus = r.getString("account_status");
                        hasStaff = r.getObject("staff_id") != null;
                        staffStatus = r.getString("employment_status");
                        memberStatus = r.getString("institution_status");
                        validUntil = r.getDate("valid_until");
                    }
                }
                if (requireStaff && !hasStaff) {
                    throw new IllegalArgumentException("Tài khoản không phải tài khoản nhân viên.");
                }
                String expectedStatus = lock ? "ACTIVE" : "LOCKED";
                if (!expectedStatus.equals(currentStatus)) {
                    throw new IllegalArgumentException("Trạng thái tài khoản vừa thay đổi; hãy tải lại.");
                }
                if (lock && isActiveAdmin(c, accountId) && activeAdminCount(c) <= 1) {
                    throw new IllegalArgumentException("Không thể khóa quản trị viên đang hoạt động cuối cùng.");
                }
                if (!lock) {
                    if (staffStatus != null && !"ACTIVE".equals(staffStatus)) {
                        throw new IllegalArgumentException("Nhân viên đã ngừng làm việc; không thể mở tài khoản.");
                    }
                    if (memberStatus != null && (!"ACTIVE".equals(memberStatus)
                            || (validUntil != null && validUntil.toLocalDate().isBefore(LocalDate.now())))) {
                        throw new IllegalArgumentException("Hồ sơ trường không còn hiệu lực; không thể mở tài khoản.");
                    }
                }
                try (PreparedStatement q = c.prepareStatement(
                        "UPDATE accounts SET account_status=?, lock_reason=? WHERE account_id=? AND account_status=?")) {
                    q.setString(1, lock ? "LOCKED" : "ACTIVE");
                    q.setString(2, validReason);
                    q.setLong(3, accountId);
                    q.setString(4, expectedStatus);
                    if (q.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Trạng thái tài khoản vừa thay đổi; hãy tải lại.");
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public static String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 160) {
            throw new IllegalArgumentException("Họ tên là bắt buộc và không vượt quá 160 ký tự.");
        }
        return fullName.trim();
    }

    public static String validateEmail(String email) {
        if (email == null || email.isBlank() || email.trim().length() > 190 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }
        return email.trim();
    }

    public static String validatePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (normalized.length() > 30 || !PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        }
        return normalized.isBlank() ? null : normalized;
    }

    public static String validateUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Tên đăng nhập gồm 4–50 ký tự chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        }
        return normalized;
    }

    public static void validatePassword(char[] password) {
        if (password == null || password.length < 8
                || new String(password).getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu cần từ 8 đến 72 ký tự.");
        }
    }

    public static String validateLockReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhập lý do khóa.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Lý do khóa không vượt quá 500 ký tự.");
        }
        return trimmed;
    }

    public static String validateStaffRole(String role) {
        String normalized = role == null ? "" : role.trim();
        if (!STAFF_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Vai trò nhân viên không hợp lệ.");
        }
        return normalized;
    }

    private static String validateEmploymentStatus(String status) {
        String normalized = status == null ? "" : status.trim();
        if (!EMPLOYMENT_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Tình trạng làm việc không hợp lệ.");
        }
        return normalized;
    }

    private static Set<String> loadRoles(Connection c, long accountId) throws SQLException {
        Set<String> roles = new LinkedHashSet<>();
        try (PreparedStatement roleQuery = c.prepareStatement("SELECT role FROM account_roles WHERE account_id=?")) {
            roleQuery.setLong(1, accountId);
            try (ResultSet roleRows = roleQuery.executeQuery()) {
                while (roleRows.next()) roles.add(roleRows.getString(1));
            }
        }
        return roles;
    }

    private static Set<String> loadRolesForUpdate(Connection c, long accountId) throws SQLException {
        Set<String> roles = new LinkedHashSet<>();
        try (PreparedStatement q = c.prepareStatement("SELECT role FROM account_roles WHERE account_id=? FOR UPDATE")) {
            q.setLong(1, accountId);
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) roles.add(r.getString(1));
            }
        }
        return roles;
    }

    private static void replaceRole(Connection c, long accountId, String role) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("DELETE FROM account_roles WHERE account_id=?")) {
            q.setLong(1, accountId);
            q.executeUpdate();
        }
        try (PreparedStatement q = c.prepareStatement("INSERT INTO account_roles(account_id, role) VALUES(?,?)")) {
            q.setLong(1, accountId);
            q.setString(2, role);
            q.executeUpdate();
        }
    }

    private static void ensureUniqueUsername(Connection c, String username) throws SQLException {
        try (PreparedStatement q = c.prepareStatement(
                "SELECT COUNT(*) FROM accounts WHERE LOWER(username)=LOWER(?)")) {
            q.setString(1, username);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                if (r.getInt(1) > 0) {
                    throw new IllegalArgumentException("Tên đăng nhập này đã tồn tại.");
                }
            }
        }
    }

    private static void ensureUniqueEmail(Connection c, String email, Long excludeStaffId, Long excludeMemberId) throws SQLException {
        String staffSql = excludeStaffId == null
                ? "SELECT COUNT(*) FROM staff_profiles WHERE LOWER(email)=LOWER(?)"
                : "SELECT COUNT(*) FROM staff_profiles WHERE LOWER(email)=LOWER(?) AND staff_id<>?";
        try (PreparedStatement q = c.prepareStatement(staffSql)) {
            q.setString(1, email);
            if (excludeStaffId != null) q.setLong(2, excludeStaffId);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                if (r.getInt(1) > 0) {
                    throw new IllegalArgumentException("Email này đã được nhân viên khác sử dụng.");
                }
            }
        }
        String memberSql = excludeMemberId == null
                ? "SELECT COUNT(*) FROM school_members WHERE LOWER(email)=LOWER(?)"
                : "SELECT COUNT(*) FROM school_members WHERE LOWER(email)=LOWER(?) AND member_id<>?";
        try (PreparedStatement q = c.prepareStatement(memberSql)) {
            q.setString(1, email);
            if (excludeMemberId != null) q.setLong(2, excludeMemberId);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                if (r.getInt(1) > 0) {
                    throw new IllegalArgumentException("Email này đã được hồ sơ thành viên trường sử dụng.");
                }
            }
        }
    }

    private static boolean isActiveAdmin(Connection c, long accountId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM account_roles ar JOIN accounts a ON a.account_id=ar.account_id "
                + "LEFT JOIN staff_profiles s ON s.staff_id=a.staff_id "
                + "WHERE ar.account_id=? AND ar.role='ADMIN' AND a.account_status='ACTIVE' "
                + "AND (s.staff_id IS NULL OR s.employment_status='ACTIVE')";
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setLong(1, accountId);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                return r.getInt(1) > 0;
            }
        }
    }

    private static int activeAdminCount(Connection c) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT a.account_id) FROM account_roles ar "
                + "JOIN accounts a ON a.account_id=ar.account_id "
                + "LEFT JOIN staff_profiles s ON s.staff_id=a.staff_id "
                + "WHERE ar.role='ADMIN' AND a.account_status='ACTIVE' "
                + "AND (s.staff_id IS NULL OR s.employment_status='ACTIVE')";
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static String nextUniqueStaffCode(Connection c) throws SQLException {
        String base = "LIB-" + System.currentTimeMillis();
        String candidate = base;
        int attempt = 1;
        while (staffCodeExists(c, candidate)) {
            candidate = base + "-" + attempt++;
        }
        return candidate;
    }

    private static boolean staffCodeExists(Connection c, String staffCode) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM staff_profiles WHERE staff_code=?")) {
            q.setString(1, staffCode);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                return r.getInt(1) > 0;
            }
        }
    }
}
