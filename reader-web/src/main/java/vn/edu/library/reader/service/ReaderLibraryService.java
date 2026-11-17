package vn.edu.library.reader.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReaderLibraryService {
    private final JdbcTemplate jdbc;
    public ReaderLibraryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> findBooks(String term) {
        String q = term == null ? "" : term.trim(); String like = "%" + q + "%";
        return jdbc.queryForList("SELECT b.book_id,b.isbn,b.title,c.category_name,p.publisher_name,b.publication_year,b.description, "
                + "GROUP_CONCAT(DISTINCT a.author_name ORDER BY a.author_name SEPARATOR ', ') AS authors, "
                + "SUM(CASE WHEN cp.copy_status='AVAILABLE' THEN 1 ELSE 0 END) AS available_count "
                + "FROM books b LEFT JOIN categories c ON c.category_id=b.category_id LEFT JOIN publishers p ON p.publisher_id=b.publisher_id "
                + "LEFT JOIN book_authors ba ON ba.book_id=b.book_id LEFT JOIN authors a ON a.author_id=ba.author_id "
                + "LEFT JOIN book_copies cp ON cp.book_id=b.book_id WHERE b.record_status='ACTIVE' AND (?='' OR b.title LIKE ? OR b.isbn LIKE ? OR a.author_name LIKE ? OR c.category_name LIKE ? OR p.publisher_name LIKE ?) "
                + "GROUP BY b.book_id ORDER BY b.title LIMIT 200", q, like, like, like, like, like);
    }

    @Transactional
    public void reserve(long memberId, long bookId) {
        List<Long> members = jdbc.queryForList("SELECT member_id FROM school_members WHERE member_id=? AND institution_status='ACTIVE' "
                + "AND (valid_until IS NULL OR valid_until>=CURRENT_DATE()) FOR UPDATE", Long.class, memberId);
        if (members.isEmpty()) throw new IllegalArgumentException("Hồ sơ trường không còn đủ điều kiện sử dụng dịch vụ.");
        if (jdbc.queryForList("SELECT book_id FROM books WHERE book_id=? AND record_status='ACTIVE'", Long.class, bookId).isEmpty())
            throw new IllegalArgumentException("Không tìm thấy đầu sách đang hoạt động.");
        int pending = jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE member_id=? AND book_id=? "
                + "AND reservation_status IN ('PENDING','APPROVED','READY_FOR_PICKUP')", Integer.class, memberId, bookId);
        if (pending > 0) throw new IllegalArgumentException("Bạn đã có yêu cầu đặt trước đang xử lý cho đầu sách này.");
        jdbc.update("INSERT INTO reservations(member_id,book_id) VALUES(?,?)", memberId, bookId);
    }

    public List<Map<String,Object>> reservations(long memberId) {
        return jdbc.queryForList("SELECT r.reservation_id,b.title,r.requested_at,r.reservation_status,r.expires_at,r.note,r.reserved_copy_id, "
                + "CASE r.reservation_status WHEN 'PENDING' THEN 'Chờ duyệt' WHEN 'APPROVED' THEN 'Đã duyệt' "
                + "WHEN 'REJECTED' THEN 'Bị từ chối' WHEN 'READY_FOR_PICKUP' THEN 'Chờ nhận sách' "
                + "WHEN 'FULFILLED' THEN 'Đã nhận sách' WHEN 'CANCELLED' THEN 'Đã hủy' WHEN 'EXPIRED' THEN 'Hết hạn' END AS status_label "
                + "FROM reservations r JOIN books b ON b.book_id=r.book_id WHERE r.member_id=? ORDER BY r.requested_at DESC", memberId);
    }

    @Transactional
    public void cancelReservation(long memberId, long reservationId) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT reservation_status,reserved_copy_id FROM reservations "
                + "WHERE reservation_id=? AND member_id=? FOR UPDATE", reservationId, memberId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Không tìm thấy yêu cầu đặt trước của bạn.");
        Map<String,Object> row = rows.getFirst();
        String status = (String)row.get("reservation_status");
        if (!List.of("PENDING","APPROVED","READY_FOR_PICKUP").contains(status))
            throw new IllegalArgumentException("Yêu cầu này không thể hủy ở trạng thái hiện tại.");
        Object copy = row.get("reserved_copy_id");
        if (copy instanceof Number copyId) jdbc.update("UPDATE book_copies SET copy_status='AVAILABLE' WHERE copy_id=? AND copy_status='RESERVED'", copyId.longValue());
        jdbc.update("UPDATE reservations SET reservation_status='CANCELLED',note=LEFT(CONCAT_WS(CHAR(10),NULLIF(note,''),'Độc giả hủy yêu cầu.'),500) WHERE reservation_id=?",
                reservationId);
    }

    public List<Map<String,Object>> loans(long memberId) {
        return jdbc.queryForList("SELECT li.loan_item_id,b.title,cp.barcode,l.borrowed_at,li.due_at,li.returned_at,li.renew_count, "
                + "CASE WHEN li.returned_at IS NOT NULL THEN 'Đã trả' WHEN li.due_at<CURRENT_DATE() THEN 'Quá hạn' ELSE 'Đang mượn' END AS item_status, "
                + "(li.returned_at IS NULL AND li.due_at>=CURRENT_DATE() AND li.renew_count < "
                + "CAST((SELECT setting_value FROM system_settings WHERE setting_key='LOAN_RENEWAL_MAX_COUNT') AS UNSIGNED)) AS can_renew "
                + "FROM loans l JOIN loan_items li ON li.loan_id=l.loan_id JOIN book_copies cp ON cp.copy_id=li.copy_id "
                + "JOIN books b ON b.book_id=cp.book_id WHERE l.member_id=? AND l.loan_status IN ('OPEN','PARTIALLY_RETURNED','CLOSED') ORDER BY l.borrowed_at DESC", memberId);
    }

    @Transactional
    public LocalDate renew(long memberId, long loanItemId) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT li.due_at,li.returned_at,li.renew_count,cp.book_id FROM loan_items li "
                + "JOIN loans l ON l.loan_id=li.loan_id JOIN book_copies cp ON cp.copy_id=li.copy_id "
                + "WHERE li.loan_item_id=? AND l.member_id=? AND l.loan_status IN ('OPEN','PARTIALLY_RETURNED') FOR UPDATE", loanItemId, memberId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Không tìm thấy lượt mượn thuộc tài khoản của bạn.");
        Map<String,Object> item = rows.getFirst();
        if (item.get("returned_at") != null) throw new IllegalArgumentException("Sách đã được trả nên không thể gia hạn.");
        LocalDate dueAt = ((java.sql.Date)item.get("due_at")).toLocalDate();
        if (dueAt.isBefore(LocalDate.now())) throw new IllegalArgumentException("Sách đã quá hạn; không thể gia hạn trực tuyến.");
        int renewCount = ((Number)item.get("renew_count")).intValue();
        int maxCount = setting("LOAN_RENEWAL_MAX_COUNT", 1);
        if (renewCount >= maxCount) throw new IllegalArgumentException("Lượt mượn này đã hết số lần gia hạn.");
        int days = setting("LOAN_RENEWAL_MAX_DAYS", 7);
        if (days < 1 || days > 7) days = 7;
        LocalDate newDueAt = dueAt.plusDays(days);
        jdbc.update("UPDATE loan_items SET due_at=?,renew_count=renew_count+1 WHERE loan_item_id=?", java.sql.Date.valueOf(newDueAt), loanItemId);
        return newDueAt;
    }
    public List<Map<String,Object>> violations(long memberId) {
        return jdbc.queryForList("SELECT v.violation_id,b.title,v.violation_type,v.description,v.assessed_amount,v.paid_amount, "
                + "CASE WHEN v.violation_status='WAIVED' THEN 0 ELSE GREATEST(v.assessed_amount-v.paid_amount,0) END AS due_amount, "
                + "v.violation_status,v.assessed_at,CASE v.violation_status WHEN 'UNPAID' THEN 'Chưa thanh toán' "
                + "WHEN 'PARTIALLY_PAID' THEN 'Thanh toán một phần' WHEN 'PAID' THEN 'Đã thanh toán' ELSE 'Đã miễn' END AS status_label "
                + "FROM violations v JOIN loan_items li ON li.loan_item_id=v.loan_item_id JOIN loans l ON l.loan_id=li.loan_id "
                + "JOIN book_copies cp ON cp.copy_id=li.copy_id JOIN books b ON b.book_id=cp.book_id WHERE l.member_id=? ORDER BY v.assessed_at DESC", memberId);
    }

    @Transactional
    public String payViolationBySimulatedQr(long memberId, long violationId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT v.assessed_amount,v.paid_amount,v.violation_status FROM violations v "
                + "JOIN loan_items li ON li.loan_item_id=v.loan_item_id JOIN loans l ON l.loan_id=li.loan_id "
                + "WHERE v.violation_id=? AND l.member_id=? FOR UPDATE", violationId, memberId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Không tìm thấy khoản vi phạm thuộc tài khoản của bạn.");
        Map<String,Object> row = rows.getFirst();
        if ("WAIVED".equals(row.get("violation_status")) || "PAID".equals(row.get("violation_status")))
            throw new IllegalArgumentException("Khoản này đã được xử lý.");
        BigDecimal due = ((BigDecimal)row.get("assessed_amount")).subtract((BigDecimal)row.get("paid_amount"));
        if (amount.compareTo(due) > 0) throw new IllegalArgumentException("Số tiền vượt khoản còn nợ " + due.toPlainString() + " đồng.");
        String receipt = "VP-SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        jdbc.update("INSERT INTO violation_payments(violation_id,amount,payment_method,receipt_number,received_by) VALUES(?,?,'QR_SIMULATED',?,NULL)",
                violationId, amount, receipt);
        String nextStatus = amount.compareTo(due) == 0 ? "PAID" : "PARTIALLY_PAID";
        jdbc.update("UPDATE violations SET paid_amount=paid_amount+?,violation_status=? WHERE violation_id=?", amount, nextStatus, violationId);
        return receipt;
    }
    public List<Map<String,Object>> documents(String term) {
        String q=term==null?"":term.trim(),like="%"+q+"%";
        if(q.isBlank())return List.of();
        return jdbc.queryForList("SELECT document_id,title,author_label,category_label,description,uploaded_at FROM electronic_documents "
                + "WHERE document_status='ACTIVE' AND (?='' OR title LIKE ? OR author_label LIKE ? OR category_label LIKE ?) ORDER BY title LIMIT 200",q,like,like,like);
    }

    public List<Map<String,Object>> cards(long memberId) {
        return jdbc.queryForList("SELECT c.card_id,c.card_number,c.issue_type,c.request_reason,c.fee,c.card_status,c.requested_at,c.handed_over_at, "
                + "COALESCE(SUM(p.amount),0) AS paid_amount,GREATEST(c.fee-COALESCE(SUM(p.amount),0),0) AS due_amount, "
                + "GROUP_CONCAT(DISTINCT p.payment_method ORDER BY p.payment_method SEPARATOR ', ') AS payment_methods, "
                + "CASE c.card_status WHEN 'PENDING_PAYMENT' THEN 'Chờ thanh toán' WHEN 'PENDING_HANDOVER' THEN 'Chờ nhận thẻ' "
                + "WHEN 'ACTIVE' THEN 'Đang hoạt động' WHEN 'LOCKED' THEN 'Đã khóa' WHEN 'EXPIRED' THEN 'Hết hạn' WHEN 'CANCELLED' THEN 'Đã hủy' END AS status_label "
                + "FROM library_cards c LEFT JOIN card_payments p ON p.card_id=c.card_id WHERE c.member_id=? "
                + "GROUP BY c.card_id ORDER BY c.requested_at DESC", memberId);
    }

    public boolean canRequestCard(long memberId) {
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM library_cards WHERE member_id=? "
                + "AND card_status IN ('PENDING_PAYMENT','PENDING_HANDOVER')", Integer.class, memberId);
        return pending == null || pending == 0;
    }

    @Transactional
    public String requestCard(long memberId, String paymentMethod, String requestReason) {
        String method = paymentMethod == null ? "" : paymentMethod.trim();
        if (!List.of("CASH","QR_SIMULATED").contains(method)) throw new IllegalArgumentException("Chọn tiền mặt tại quầy hoặc QR giả lập.");
        List<Long> members = jdbc.queryForList("SELECT member_id FROM school_members WHERE member_id=? AND member_type IN ('STUDENT','LEARNER','LECTURER') "
                + "AND institution_status='ACTIVE' AND (valid_until IS NULL OR valid_until>=CURRENT_DATE()) FOR UPDATE", Long.class, memberId);
        if (members.isEmpty()) throw new IllegalArgumentException("Hồ sơ trường đã hết hiệu lực hoặc không đủ điều kiện đăng ký thẻ.");
        if (!canRequestCard(memberId)) throw new IllegalArgumentException("Bạn đang có yêu cầu cấp thẻ chờ thanh toán hoặc chờ nhận thẻ.");
        Integer previous = jdbc.queryForObject("SELECT COUNT(*) FROM library_cards WHERE member_id=?", Integer.class, memberId);
        boolean firstIssue = previous == null || previous == 0;
        String reason=requestReason==null?"":requestReason.trim();
        if(!firstIssue&&reason.isBlank())throw new IllegalArgumentException("Khi cấp lại thẻ, hãy ghi rõ thẻ cũ bị mất hoặc hư hỏng.");
        if(reason.length()>500)throw new IllegalArgumentException("Lý do cấp lại không được vượt quá 500 ký tự.");
        BigDecimal fee = BigDecimal.valueOf(setting(firstIssue ? "CARD_FIRST_ISSUE_FEE" : "CARD_REISSUE_FEE", firstIssue ? 50000 : 20000));
        String number = "LC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbc.update("INSERT INTO library_cards(card_number,member_id,issue_type,request_reason,fee) VALUES(?,?,?,?,?)", number, memberId,
                firstIssue ? "FIRST_ISSUE" : "REISSUE", reason.isBlank()?null:reason, fee);
        if ("QR_SIMULATED".equals(method)) {
            Long cardId = jdbc.queryForObject("SELECT card_id FROM library_cards WHERE card_number=?", Long.class, number);
            String receipt = "CP-SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            jdbc.update("INSERT INTO card_payments(card_id,amount,payment_method,receipt_number,received_by) VALUES(?,?,'QR_SIMULATED',?,NULL)", cardId, fee, receipt);
            jdbc.update("UPDATE library_cards SET card_status='PENDING_HANDOVER' WHERE card_id=?", cardId);
        }
        return number;
    }

    private int setting(String key, int fallback) {
        List<String> values = jdbc.query("SELECT setting_value FROM system_settings WHERE setting_key=?", (rs, row) -> rs.getString(1), key);
        if (values.isEmpty()) return fallback;
        try { return Integer.parseInt(values.getFirst()); } catch (NumberFormatException ignored) { return fallback; }
    }
    public Path documentPath(long id, Path storageRoot) {
        List<String> files=jdbc.queryForList("SELECT file_path FROM electronic_documents WHERE document_id=? AND document_status='ACTIVE'",String.class,id);
        if(files.isEmpty()) return null;
        Path root=storageRoot.toAbsolutePath().normalize(),path=root.resolve(files.getFirst()).normalize();
        return path.startsWith(root)?path:null;
    }
}
