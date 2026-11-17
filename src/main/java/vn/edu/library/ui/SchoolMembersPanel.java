package vn.edu.library.ui;

import vn.edu.library.data.Database;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public final class SchoolMembersPanel extends JPanel {
    private final JTextField search = new JTextField(25);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public SchoolMembersPanel() {
        setLayout(new BorderLayout(0, 16));
        setBackground(UiStyle.CANVAS);
        setBorder(new EmptyBorder(27, 30, 24, 30));
        add(buildHeader(), BorderLayout.NORTH);
        table.setRowHeight(33);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(229, 233, 240)));
        add(scroll, BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);
        add(status, BorderLayout.SOUTH);
        load();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 16));
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Thành viên trường");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Quản lý hồ sơ sinh viên, học viên và giảng viên được phép đăng ký thẻ thư viện.");
        subtitle.setForeground(UiStyle.MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(5));
        titles.add(subtitle);
        header.add(titles, BorderLayout.NORTH);

        search.putClientProperty("JTextField.placeholderText", "Mã trường, họ tên, email hoặc khoa/viện");
        JButton find = new JButton("Tìm kiếm");
        JButton refresh = new JButton("Làm mới");
        JButton add = new JButton("Thêm thành viên");
        JButton edit = new JButton("Sửa hồ sơ");
        JButton statusButton = new JButton("Đổi trạng thái");
        JButton deleteButton = new JButton("Xóa hồ sơ");
        JButton loanHistory = new JButton("Lịch sử mượn–trả");
        JButton violationHistory = new JButton("Lịch sử vi phạm");
        add.setBackground(UiStyle.BLUE);
        add.setForeground(Color.WHITE);
        find.addActionListener(e -> load());
        refresh.addActionListener(e -> { search.setText(""); load(); });
        add.addActionListener(e -> editMember(null));
        edit.addActionListener(e -> editSelected());
        statusButton.addActionListener(e -> toggleStatus());
        deleteButton.addActionListener(e -> deleteSelected());
        loanHistory.addActionListener(e -> showHistory(false));
        violationHistory.addActionListener(e -> showHistory(true));
        search.addActionListener(e -> load());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        controls.setOpaque(false);
        controls.add(search);
        controls.add(find);
        controls.add(refresh);
        controls.add(add);
        controls.add(edit);
        controls.add(statusButton);
        controls.add(deleteButton);
        controls.add(loanHistory);
        controls.add(violationHistory);
        header.add(controls, BorderLayout.SOUTH);
        return header;
    }

    private void load() {
        String term = search.getText().trim();
        String sql = "SELECT m.member_id, m.member_code AS 'Mã trường', "
                + "CASE m.member_type WHEN 'STUDENT' THEN 'Sinh viên' WHEN 'LEARNER' THEN 'Học viên' ELSE 'Giảng viên' END AS 'Nhóm', "
                + "m.full_name AS 'Họ tên', d.department_name AS 'Khoa/viện', m.email AS 'Email', m.phone AS 'Điện thoại', "
                + "m.institution_status AS 'Trạng thái', COALESCE(st.program_name, le.program_name, lc.specialization) AS 'Chương trình/chuyên môn' "
                + "FROM school_members m LEFT JOIN departments d ON d.department_id=m.department_id "
                + "LEFT JOIN students st ON st.member_id=m.member_id LEFT JOIN learners le ON le.member_id=m.member_id "
                + "LEFT JOIN lecturers lc ON lc.member_id=m.member_id "
                + "WHERE (?='' OR m.member_code LIKE ? OR m.full_name LIKE ? OR m.email LIKE ? OR m.phone LIKE ? OR d.department_name LIKE ?) "
                + "ORDER BY m.full_name";
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            String pattern = "%" + term + "%";
            query.setString(1, term);
            for (int i = 2; i <= 6; i++) query.setString(i, pattern);
            try (ResultSet rows = query.executeQuery()) {
                ResultSetMetaData meta = rows.getMetaData();
                model.setRowCount(0);
                model.setColumnCount(0);
                for (int i = 2; i <= meta.getColumnCount(); i++) model.addColumn(meta.getColumnLabel(i));
                while (rows.next()) {
                    Object[] values = new Object[meta.getColumnCount() - 1];
                    for (int i = 2; i <= meta.getColumnCount(); i++) values[i - 2] = rows.getObject(i);
                    model.addRow(values);
                }
            }
            table.getColumnModel().getColumn(0).setPreferredWidth(105);
            table.getColumnModel().getColumn(2).setPreferredWidth(165);
            status.setText("Đang hiển thị " + model.getRowCount() + " hồ sơ. Hồ sơ có giao dịch thư viện nên được ngừng hoạt động thay vì xóa.");
        } catch (Exception ex) {
            model.setRowCount(0);
            status.setText("Không tải được hồ sơ: " + ex.getMessage());
        }
    }

    private void editSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { message("Chọn một hồ sơ trước.", JOptionPane.INFORMATION_MESSAGE); return; }
        int row = table.convertRowIndexToModel(viewRow);
        String code = model.getValueAt(row, 0).toString();
        try (Connection connection = Database.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT m.*, d.department_code, COALESCE(st.program_name,le.program_name) program_name, "
                             + "st.class_code, st.academic_year, le.cohort_code, le.training_level, "
                             + "lc.academic_title, lc.specialization FROM school_members m "
                             + "LEFT JOIN departments d ON d.department_id=m.department_id "
                             + "LEFT JOIN students st ON st.member_id=m.member_id "
                             + "LEFT JOIN learners le ON le.member_id=m.member_id "
                             + "LEFT JOIN lecturers lc ON lc.member_id=m.member_id WHERE m.member_code=?")) {
            query.setString(1, code);
            try (ResultSet rs = query.executeQuery()) {
                if (!rs.next()) { message("Không tìm thấy hồ sơ.", JOptionPane.WARNING_MESSAGE); return; }
                editMember(new MemberForm(rs));
            }
        } catch (Exception ex) { message("Không mở được hồ sơ: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private void editMember(MemberForm existing) {
        boolean isNew = existing == null;
        MemberForm form = isNew ? new MemberForm() : existing;
        int result = JOptionPane.showConfirmDialog(this, form.panel, isNew ? "Thêm thành viên trường" : "Cập nhật hồ sơ",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try {
            save(form, isNew);
            load();
            message("Đã lưu hồ sơ thành viên.", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) { message("Không lưu được hồ sơ: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private void save(MemberForm form, boolean isNew) throws Exception {
        String code = form.code.getText().trim();
        String name = form.name.getText().trim();
        String email = form.email.getText().trim();
        String phone = form.phone.getText().trim();
        String departmentCode = form.department.getText().trim();
        String type = (String) form.type.getSelectedItem();
        String program = form.program.getText().trim();
        if (code.isBlank() || name.isBlank()) throw new IllegalArgumentException("Mã trường và họ tên là bắt buộc.");
        if (!email.isBlank() && (email.length()>190 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
            throw new IllegalArgumentException("Email không hợp lệ.");
        if (!phone.isBlank() && (phone.length()>30 || !phone.matches("[+0-9(). -]+")))
            throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        if (("STUDENT".equals(type) || "LEARNER".equals(type)) && program.isBlank())
            throw new IllegalArgumentException("Sinh viên và học viên cần có chương trình đào tạo.");
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long departmentId = null;
                if (!departmentCode.isBlank()) {
                    try (PreparedStatement d = connection.prepareStatement("SELECT department_id FROM departments WHERE department_code=? AND is_active=TRUE")) {
                        d.setString(1, departmentCode);
                        try (ResultSet rs = d.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy mã khoa/viện " + departmentCode + ".");
                            departmentId = rs.getLong(1);
                        }
                    }
                }
                long memberId;
                if (isNew) {
                    String sql = "INSERT INTO school_members (member_code,member_type,full_name,date_of_birth,email,phone,department_id,institution_status,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        bindMember(insert, form, departmentId, code, type, name, email, phone, 1);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) { keys.next(); memberId = keys.getLong(1); }
                    }
                } else {
                    memberId = form.memberId;
                    String sql = "UPDATE school_members SET full_name=?,date_of_birth=?,email=?,phone=?,department_id=?,institution_status=?,valid_from=?,valid_until=? WHERE member_id=?";
                    try (PreparedStatement update = connection.prepareStatement(sql)) {
                        update.setString(1, name);
                        update.setDate(2, parseDate(form.dateOfBirth.getText()));
                        update.setString(3, emptyToNull(email));
                        update.setString(4, emptyToNull(phone));
                        if (departmentId == null) update.setNull(5, Types.BIGINT); else update.setLong(5, departmentId);
                        update.setString(6, (String) form.status.getSelectedItem());
                        update.setDate(7, parseDate(form.validFrom.getText()));
                        update.setDate(8, parseDate(form.validUntil.getText()));
                        update.setLong(9, memberId);
                        update.executeUpdate();
                    }
                }
                saveSubtype(connection, form, type, memberId);
                connection.commit();
            } catch (Exception ex) { connection.rollback(); throw ex; }
        }
    }

    private void bindMember(PreparedStatement insert, MemberForm form, Long departmentId,
                            String code, String type, String name, String email, String phone, int offset) throws SQLException {
        insert.setString(1, code);
        insert.setString(2, type);
        insert.setString(3, name);
        insert.setDate(4, parseDate(form.dateOfBirth.getText()));
        insert.setString(5, emptyToNull(email));
        insert.setString(6, emptyToNull(phone));
        if (departmentId == null) insert.setNull(7, Types.BIGINT); else insert.setLong(7, departmentId);
        insert.setString(8, (String) form.status.getSelectedItem());
        insert.setDate(9, parseDate(form.validFrom.getText()));
        insert.setDate(10, parseDate(form.validUntil.getText()));
    }

    private void saveSubtype(Connection connection, MemberForm form, String type, long memberId) throws SQLException {
        for (String table : new String[]{"students", "learners", "lecturers"}) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE member_id=?")) {
                delete.setLong(1, memberId);
                delete.executeUpdate();
            }
        }
        String sql;
        if ("STUDENT".equals(type)) {
            sql = "INSERT INTO students(member_id,program_name,class_code,academic_year,expected_graduation) VALUES(?,?,?,?,?)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setLong(1, memberId); insert.setString(2, form.program.getText().trim());
                insert.setString(3, emptyToNull(form.classOrCohort.getText()));
                insert.setString(4, emptyToNull(form.academicYearOrLevel.getText()));
                insert.setDate(5, parseDate(form.expectedEnd.getText())); insert.executeUpdate();
            }
        } else if ("LEARNER".equals(type)) {
            sql = "INSERT INTO learners(member_id,program_name,cohort_code,training_level,expected_completion) VALUES(?,?,?,?,?)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setLong(1, memberId); insert.setString(2, form.program.getText().trim());
                insert.setString(3, emptyToNull(form.classOrCohort.getText()));
                insert.setString(4, emptyToNull(form.academicYearOrLevel.getText()));
                insert.setDate(5, parseDate(form.expectedEnd.getText())); insert.executeUpdate();
            }
        } else {
            sql = "INSERT INTO lecturers(member_id,academic_title,specialization,employment_start,employment_end) VALUES(?,?,?,?,?)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setLong(1, memberId); insert.setString(2, emptyToNull(form.titleOrSpecialty.getText()));
                insert.setString(3, emptyToNull(form.program.getText()));
                insert.setDate(4, parseDate(form.validFrom.getText())); insert.setDate(5, parseDate(form.validUntil.getText()));
                insert.executeUpdate();
            }
        }
    }

    private void toggleStatus() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { message("Chọn một hồ sơ trước.", JOptionPane.INFORMATION_MESSAGE); return; }
        int row = table.convertRowIndexToModel(viewRow);
        String code = model.getValueAt(row, 0).toString();
        String current = model.getValueAt(row, 6).toString();
        if (!"ACTIVE".equals(current) && !"INACTIVE".equals(current)) {
            message("Hồ sơ đang ở trạng thái "+current+". Hãy cập nhật trạng thái cụ thể trong chức năng Sửa hồ sơ.",JOptionPane.WARNING_MESSAGE);return;
        }
        String next = "ACTIVE".equals(current) ? "INACTIVE" : "ACTIVE";
        int confirm = JOptionPane.showConfirmDialog(this, "Chuyển trạng thái hồ sơ " + code + " thành " + next + "?",
                "Xác nhận đổi trạng thái", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection connection = Database.getConnection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE school_members SET institution_status=? WHERE member_code=?")) {
            update.setString(1, next); update.setString(2, code); update.executeUpdate(); load();
        } catch (Exception ex) { message("Không đổi được trạng thái: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { message("Chọn hồ sơ cần xóa trước.", JOptionPane.INFORMATION_MESSAGE); return; }
        int row = table.convertRowIndexToModel(viewRow);
        String code = model.getValueAt(row, 0).toString();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Chỉ xóa được hồ sơ chưa phát sinh thẻ, lượt mượn, đặt trước hoặc nghĩa vụ thư viện. Xóa hồ sơ " + code + "?",
                "Xác nhận xóa hồ sơ", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long memberId;
                try (PreparedStatement query = connection.prepareStatement("SELECT member_id FROM school_members WHERE member_code=? FOR UPDATE")) {
                    query.setString(1, code);
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) throw new IllegalArgumentException("Không tìm thấy hồ sơ.");
                        memberId = result.getLong(1);
                    }
                }
                int activeLoans = count(connection, "SELECT COUNT(*) FROM loan_items li JOIN loans l ON l.loan_id=li.loan_id WHERE l.member_id=? AND li.returned_at IS NULL", memberId);
                int unpaid = count(connection, "SELECT COUNT(*) FROM violations v JOIN loan_items li ON li.loan_item_id=v.loan_item_id JOIN loans l ON l.loan_id=li.loan_id WHERE l.member_id=? AND v.violation_status IN ('UNPAID','PARTIALLY_PAID')", memberId);
                int loanHistory = count(connection, "SELECT COUNT(*) FROM loans WHERE member_id=?", memberId);
                int reservations = count(connection, "SELECT COUNT(*) FROM reservations WHERE member_id=?", memberId);
                int cards = count(connection, "SELECT COUNT(*) FROM library_cards WHERE member_id=?", memberId);
                if (activeLoans > 0) throw new IllegalArgumentException("Độc giả còn sách chưa trả; không thể xóa hồ sơ.");
                if (unpaid > 0) throw new IllegalArgumentException("Độc giả còn khoản phạt hoặc bồi thường chưa thanh toán.");
                if (loanHistory + reservations + cards > 0) throw new IllegalArgumentException("Hồ sơ có lịch sử mượn, đặt trước hoặc thẻ thư viện liên quan. Hãy đổi trạng thái thành ngừng hoạt động để giữ lịch sử.");
                try (PreparedStatement query = connection.prepareStatement("SELECT account_id FROM accounts WHERE member_id=?")) {
                    query.setLong(1, memberId);
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            long accountId = result.getLong(1);
                            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM account_roles WHERE account_id=?")) { delete.setLong(1, accountId); delete.executeUpdate(); }
                            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM accounts WHERE account_id=?")) { delete.setLong(1, accountId); delete.executeUpdate(); }
                        }
                    }
                }
                for (String tableName : new String[]{"students", "learners", "lecturers"}) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE member_id=?")) {
                        delete.setLong(1, memberId); delete.executeUpdate();
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM school_members WHERE member_id=?")) {
                    delete.setLong(1, memberId);
                    if (delete.executeUpdate() != 1) throw new IllegalArgumentException("Hồ sơ vừa thay đổi; hãy tải lại.");
                }
                connection.commit();
                load();
                message("Đã xóa hồ sơ thành viên.", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        } catch (Exception ex) { message("Không xóa được hồ sơ: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private void showHistory(boolean violations) {
        int view = table.getSelectedRow();
        if (view < 0) { message("Chọn hồ sơ độc giả trước.", JOptionPane.INFORMATION_MESSAGE); return; }
        int row = table.convertRowIndexToModel(view);
        String memberCode = model.getValueAt(row, 0).toString();
        String sql = violations
                ? "SELECT b.title AS 'Sách',v.violation_type AS 'Loại vi phạm',v.description AS 'Nội dung',v.assessed_amount AS 'Phải thu (VND)',v.paid_amount AS 'Đã thu (VND)',v.violation_status AS 'Thanh toán',v.assessed_at AS 'Ngày ghi nhận' FROM violations v JOIN loan_items li ON li.loan_item_id=v.loan_item_id JOIN loans l ON l.loan_id=li.loan_id JOIN book_copies cp ON cp.copy_id=li.copy_id JOIN books b ON b.book_id=cp.book_id JOIN school_members m ON m.member_id=l.member_id WHERE m.member_code=? ORDER BY v.assessed_at DESC"
                : "SELECT l.loan_number AS 'Mã phiếu',b.title AS 'Sách',cp.barcode AS 'Mã bản sách',l.borrowed_at AS 'Ngày mượn',li.due_at AS 'Hạn trả',li.returned_at AS 'Ngày trả',CASE WHEN l.loan_status='DRAFT' THEN 'Chờ bàn giao' WHEN li.returned_at IS NOT NULL THEN 'Đã trả' WHEN li.due_at<CURRENT_DATE() THEN 'Quá hạn' ELSE 'Đang mượn' END AS 'Trạng thái' FROM loans l JOIN loan_items li ON li.loan_id=l.loan_id JOIN book_copies cp ON cp.copy_id=li.copy_id JOIN books b ON b.book_id=cp.book_id JOIN school_members m ON m.member_id=l.member_id WHERE m.member_code=? ORDER BY l.borrowed_at DESC";
        DefaultTableModel history = new DefaultTableModel() { @Override public boolean isCellEditable(int r, int c) { return false; } };
        String fullName = model.getValueAt(row, 2).toString();
        String dialogTitle = (violations ? "Lịch sử vi phạm · " : "Lịch sử mượn–trả · ") + fullName;
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, memberCode);
            try (ResultSet rs = q.executeQuery()) {
                ResultSetMetaData metadata = rs.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) history.addColumn(metadata.getColumnLabel(i));
                while (rs.next()) {
                    Object[] values = new Object[metadata.getColumnCount()];
                    for (int i = 1; i <= values.length; i++) values[i - 1] = rs.getObject(i);
                    history.addRow(values);
                }
            }
            JTable result = new JTable(history);
            result.setRowHeight(28);
            result.setAutoCreateRowSorter(true);
            result.setFillsViewportHeight(true);
            JLabel hint = new JLabel(history.getRowCount() == 0 ? "Chưa có dữ liệu lịch sử." : "Đang hiển thị " + history.getRowCount() + " dòng.");
            hint.setBorder(new EmptyBorder(5, 2, 2, 2));
            hint.setForeground(UiStyle.MUTED);
            JPanel content = new JPanel(new BorderLayout(0, 6));
            content.add(new JScrollPane(result), BorderLayout.CENTER);
            content.add(hint, BorderLayout.SOUTH);
            content.setPreferredSize(new Dimension(930, 390));
            JOptionPane.showMessageDialog(this, content, dialogTitle, JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { message("Không tải được lịch sử: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private int count(Connection connection, String sql, long memberId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, memberId);
            try (ResultSet result = query.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private java.sql.Date parseDate(String value) throws SQLException {
        String date = value == null ? "" : value.trim();
        if (date.isEmpty()) return null;
        try { return java.sql.Date.valueOf(date); }
        catch (IllegalArgumentException ex) { throw new SQLException("Ngày phải theo định dạng YYYY-MM-DD."); }
    }

    private String emptyToNull(String value) { String clean = value == null ? "" : value.trim(); return clean.isEmpty() ? null : clean; }
    private void message(String text, int type) { JOptionPane.showMessageDialog(this, text, "Thành viên trường", type); }

    private final class MemberForm {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JTextField code = new JTextField(20), name = new JTextField(20), dateOfBirth = new JTextField(20), email = new JTextField(20), phone = new JTextField(20);
        final JTextField department = new JTextField(20), program = new JTextField(20), classOrCohort = new JTextField(20);
        final JTextField academicYearOrLevel = new JTextField(20), titleOrSpecialty = new JTextField(20);
        final JTextField validFrom = new JTextField(20), validUntil = new JTextField(20), expectedEnd = new JTextField(20);
        final JComboBox<String> type = new JComboBox<>(new String[]{"STUDENT", "LEARNER", "LECTURER"});
        final JComboBox<String> status = new JComboBox<>(new String[]{"ACTIVE", "ON_LEAVE", "GRADUATED", "RETIRED", "INACTIVE"});
        long memberId;

        MemberForm() { build(); }
        MemberForm(ResultSet row) throws SQLException {
            memberId = row.getLong("member_id"); code.setText(row.getString("member_code")); code.setEnabled(false);
            type.setSelectedItem(row.getString("member_type")); type.setEnabled(false);
            name.setText(row.getString("full_name")); dateOfBirth.setText(value(row.getDate("date_of_birth"))); email.setText(row.getString("email")); phone.setText(row.getString("phone"));
            department.setText(row.getString("department_code")); status.setSelectedItem(row.getString("institution_status"));
            program.setText(row.getString("program_name")); classOrCohort.setText(row.getString("class_code") != null ? row.getString("class_code") : row.getString("cohort_code"));
            academicYearOrLevel.setText(row.getString("academic_year") != null ? row.getString("academic_year") : row.getString("training_level"));
            titleOrSpecialty.setText(row.getString("academic_title") != null ? row.getString("academic_title") : row.getString("specialization"));
            validFrom.setText(value(row.getDate("valid_from"))); validUntil.setText(value(row.getDate("valid_until")));
            expectedEnd.setText(value(row.getDate("expected_graduation") != null ? row.getDate("expected_graduation") : row.getDate("expected_completion")));
            build();
        }
        void build() {
            panel.setBorder(new EmptyBorder(7, 4, 7, 4));
            int y = 0;
            y = addRow(y, "Mã thành viên trường *", code); y = addRow(y, "Loại thành viên", type);
            y = addRow(y, "Họ và tên *", name); y = addRow(y, "Ngày sinh (YYYY-MM-DD)", dateOfBirth); y = addRow(y, "Email", email); y = addRow(y, "Điện thoại", phone);
            y = addRow(y, "Mã khoa/viện (để trống nếu chưa có)", department); y = addRow(y, "Trạng thái tại trường", status);
            y = addRow(y, "Chương trình / chuyên môn", program); y = addRow(y, "Lớp / khóa", classOrCohort);
            y = addRow(y, "Năm học / bậc đào tạo", academicYearOrLevel); y = addRow(y, "Học hàm / chức danh", titleOrSpecialty);
            y = addRow(y, "Ngày bắt đầu (YYYY-MM-DD)", validFrom); y = addRow(y, "Ngày hết hạn (YYYY-MM-DD)", validUntil);
            addRow(y, "Ngày tốt nghiệp/kết thúc dự kiến", expectedEnd);
        }
        int addRow(int y, String label, JComponent input) {
            GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(4, 5, 4, 5); c.gridy = y; c.anchor = GridBagConstraints.WEST;
            c.gridx = 0; c.weightx = 0; panel.add(new JLabel(label), c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; panel.add(input, c); return y + 1;
        }
        String value(java.util.Date date) { return date == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(date); }
    }
}
