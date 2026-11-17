package vn.edu.library.reader.service;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ReaderAuthService {
    private final JdbcTemplate jdbc;
    public ReaderAuthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ReaderIdentity(long accountId, long memberId, String fullName, String username) { }

    public ReaderIdentity authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) return null;
        String normalizedUsername = username.trim();
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT a.account_id,a.member_id,a.password_hash,a.account_status,m.full_name,"
                        + "m.institution_status,m.valid_until "
                        + "FROM accounts a JOIN school_members m ON m.member_id=a.member_id WHERE a.username=?", normalizedUsername);
        if (rows.isEmpty()) return null;
        Map<String,Object> row = rows.getFirst();
        if (!"ACTIVE".equals(row.get("account_status")) || !eligible(row)) return null;
        String hash = (String) row.get("password_hash");
        if (hash == null || !BCrypt.checkpw(password, hash)) return null;
        Long accountId = ((Number)row.get("account_id")).longValue();
        List<String> roles = jdbc.queryForList("SELECT role FROM account_roles WHERE account_id=?", String.class, accountId);
        if (!roles.contains("READER")) return null;
        jdbc.update("UPDATE accounts SET last_login_at=CURRENT_TIMESTAMP WHERE account_id=?", accountId);
        return new ReaderIdentity(accountId, ((Number)row.get("member_id")).longValue(), (String)row.get("full_name"), normalizedUsername);
    }

    @Transactional
    public ReaderIdentity register(String memberCode, String email, String username, String password) {
        if (memberCode == null || memberCode.isBlank() || email == null || email.isBlank() || username == null || username.isBlank())
            throw new IllegalArgumentException("Mã trường, email và tên đăng nhập là bắt buộc.");
        String normalizedCode = memberCode.trim();
        String normalizedEmail = email.trim();
        String normalizedUsername = username.trim();
        if (normalizedEmail.length() > 190 || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Vui lòng nhập email hợp lệ.");
        if (!normalizedUsername.matches("[A-Za-z0-9._-]{4,50}"))
            throw new IllegalArgumentException("Tên đăng nhập gồm 4–50 ký tự chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        if (password == null || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Mật khẩu cần từ 8 đến 72 ký tự.");
        Integer duplicateUser = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE LOWER(username)=LOWER(?)", Integer.class, normalizedUsername);
        if (duplicateUser != null && duplicateUser > 0)
            throw new IllegalArgumentException("Tên đăng nhập này đã được sử dụng.");
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT member_id,full_name FROM school_members WHERE member_code=? AND LOWER(TRIM(email))=LOWER(TRIM(?)) "
                        + "AND member_type IN ('STUDENT','LEARNER','LECTURER') AND institution_status='ACTIVE' "
                        + "AND (valid_until IS NULL OR valid_until>=CURRENT_DATE()) FOR UPDATE", normalizedCode, normalizedEmail);
        if (rows.isEmpty()) throw new IllegalArgumentException("Không xác minh được hồ sơ trường còn hiệu lực bằng mã và email này.");
        Map<String,Object> member = rows.getFirst();
        long memberId = ((Number)member.get("member_id")).longValue();
        if (!jdbc.queryForList("SELECT account_id FROM accounts WHERE member_id=? FOR UPDATE", Long.class, memberId).isEmpty())
            throw new IllegalArgumentException("Hồ sơ này đã có tài khoản. Hãy đăng nhập hoặc liên hệ thư viện.");
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        jdbc.update("INSERT INTO accounts(username,password_hash,member_id) VALUES(?,?,?)", normalizedUsername, hash, memberId);
        Long accountId = jdbc.queryForObject("SELECT account_id FROM accounts WHERE username=?", Long.class, normalizedUsername);
        jdbc.update("INSERT INTO account_roles(account_id,role) VALUES(?,'READER')", accountId);
        return new ReaderIdentity(accountId, memberId, (String)member.get("full_name"), normalizedUsername);
    }

    public Map<String,Object> profile(long memberId) {
        return jdbc.queryForMap("SELECT m.member_code,m.member_type,m.full_name,m.email,m.phone,m.institution_status,"
                + "m.valid_until,d.department_name FROM school_members m LEFT JOIN departments d ON d.department_id=m.department_id "
                + "WHERE m.member_id=?", memberId);
    }

    public void updateContact(long memberId, String email, String phone) {
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedEmail.isBlank() || normalizedEmail.length() > 190
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Vui lòng nhập email hợp lệ.");
        }
        String normalizedPhone = phone == null ? "" : phone.trim();
        if (normalizedPhone.length() > 30 || !normalizedPhone.matches("[+0-9(). -]*")) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        }
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM school_members WHERE LOWER(email)=LOWER(?) AND member_id<>?",
                Integer.class, normalizedEmail, memberId);
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("Email này đã được hồ sơ khác sử dụng.");
        Integer staffDuplicate = jdbc.queryForObject("SELECT COUNT(*) FROM staff_profiles WHERE LOWER(email)=LOWER(?)",
                Integer.class, normalizedEmail);
        if (staffDuplicate != null && staffDuplicate > 0) throw new IllegalArgumentException("Email này đã được tài khoản nhân viên sử dụng.");
        jdbc.update("UPDATE school_members SET email=?,phone=? WHERE member_id=?", normalizedEmail, normalizedPhone.isBlank()?null:normalizedPhone, memberId);
    }

    public void changePassword(long accountId, String currentPassword, String newPassword, String confirmation) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Mật khẩu mới cần từ 8 đến 72 ký tự.");
        if (!newPassword.equals(confirmation)) throw new IllegalArgumentException("Hai mật khẩu mới chưa khớp.");
        List<String> hashes = jdbc.query("SELECT password_hash FROM accounts a JOIN account_roles r ON r.account_id=a.account_id "
                + "WHERE a.account_id=? AND a.account_status='ACTIVE' AND r.role='READER'", (rs, rowNum) -> rs.getString(1), accountId);
        if (hashes.isEmpty() || !BCrypt.checkpw(currentPassword == null ? "" : currentPassword, hashes.getFirst()))
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác.");
        if (BCrypt.checkpw(newPassword, hashes.getFirst())) throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        jdbc.update("UPDATE accounts SET password_hash=? WHERE account_id=?", BCrypt.hashpw(newPassword, BCrypt.gensalt(12)), accountId);
    }

    public boolean isReaderSessionActive(long accountId) {
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT a.account_status,m.institution_status,m.valid_until "
                        + "FROM accounts a JOIN school_members m ON m.member_id=a.member_id "
                        + "JOIN account_roles r ON r.account_id=a.account_id AND r.role='READER' WHERE a.account_id=?", accountId);
        return !rows.isEmpty() && eligible(rows.getFirst()) && "ACTIVE".equals(rows.getFirst().get("account_status"));
    }

    private boolean eligible(Map<String,Object> row) {
        if (!"ACTIVE".equals(row.get("institution_status"))) return false;
        Object until = row.get("valid_until");
        return until == null || !((java.sql.Date)until).toLocalDate().isBefore(java.time.LocalDate.now());
    }
}
