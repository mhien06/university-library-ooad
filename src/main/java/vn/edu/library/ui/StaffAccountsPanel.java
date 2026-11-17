package vn.edu.library.ui;

import vn.edu.library.data.AuthService;
import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Arrays;
import java.util.function.Consumer;

public final class StaffAccountsPanel extends JPanel {
    private final UserSession session;
    private final Consumer<String> onDisplayNameChanged;
    private final AuthService auth = new AuthService();
    private final JTextField search = new JTextField(22);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public StaffAccountsPanel(UserSession session) {
        this(session, null);
    }

    public StaffAccountsPanel(UserSession session, Consumer<String> onDisplayNameChanged) {
        this.session = session;
        this.onDisplayNameChanged = onDisplayNameChanged;
        setLayout(new BorderLayout(0, 16));
        setBackground(UiStyle.CANVAS);
        setBorder(new EmptyBorder(27, 30, 24, 30));
        add(header(), BorderLayout.NORTH);
        table.setRowHeight(33);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        JScrollPane sc = new JScrollPane(table);
        sc.setBorder(BorderFactory.createLineBorder(new Color(229, 233, 240)));
        add(sc, BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);
        add(status, BorderLayout.SOUTH);
        load();
    }

    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout(0, 16));
        h.setOpaque(false);
        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Nhân viên và tài khoản");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel sub = new JLabel("Tạo nhân viên thư viện, cấp vai trò và khóa/mở tài khoản.");
        sub.setForeground(UiStyle.MUTED);
        t.add(title);
        t.add(Box.createVerticalStrut(5));
        t.add(sub);
        h.add(t, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Mã nhân viên, tên, email, tài khoản");
        JButton find = new JButton("Tìm kiếm"), refresh = new JButton("Làm mới"),
                add = new JButton("Tạo tài khoản nhân viên"), edit = new JButton("Sửa hồ sơ / quyền"),
                toggle = new JButton("Khóa / mở tài khoản");
        add.setBackground(UiStyle.BLUE);
        add.setForeground(Color.WHITE);
        find.addActionListener(e -> load());
        refresh.addActionListener(e -> { search.setText(""); load(); });
        search.addActionListener(e -> load());
        add.addActionListener(e -> createStaff());
        edit.addActionListener(e -> editSelected());
        toggle.addActionListener(e -> toggleAccount());
        JPanel c = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        c.setOpaque(false);
        for (JComponent x : new JComponent[]{search, find, refresh, add, edit, toggle}) c.add(x);
        h.add(c, BorderLayout.SOUTH);
        return h;
    }

    private void load() {
        String term = search.getText().trim();
        String sql = "SELECT a.account_id,s.staff_id,s.staff_code AS 'Mã nhân viên',a.username AS 'Tài khoản',s.full_name AS 'Họ tên',s.email AS 'Email',s.phone AS 'Điện thoại',s.job_title AS 'Chức vụ',GROUP_CONCAT(ar.role ORDER BY ar.role SEPARATOR ', ') AS 'Vai trò',a.account_status AS 'Tài khoản',s.employment_status AS 'Nhân sự' FROM accounts a JOIN staff_profiles s ON s.staff_id=a.staff_id LEFT JOIN account_roles ar ON ar.account_id=a.account_id WHERE (?='' OR s.staff_code LIKE ? OR a.username LIKE ? OR s.full_name LIKE ? OR s.email LIKE ?) GROUP BY a.account_id ORDER BY s.full_name";
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
            String p = "%" + term + "%";
            q.setString(1, term);
            for (int i = 2; i <= 5; i++) q.setString(i, p);
            try (ResultSet rs = q.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                model.setRowCount(0);
                model.setColumnCount(0);
                for (int i = 1; i <= md.getColumnCount(); i++) model.addColumn(md.getColumnLabel(i));
                while (rs.next()) {
                    Object[] a = new Object[md.getColumnCount()];
                    for (int i = 1; i <= md.getColumnCount(); i++) a[i - 1] = rs.getObject(i);
                    model.addRow(a);
                }
            }
            status.setText("Đang hiển thị " + model.getRowCount() + " tài khoản nhân viên.");
        } catch (Exception ex) {
            model.setRowCount(0);
            status.setText("Không tải được nhân viên: " + ex.getMessage());
        }
    }

    private void createStaff() {
        JTextField name = new JTextField(21), email = new JTextField(21), phone = new JTextField(21), username = new JTextField(21);
        JPasswordField password = new JPasswordField(21);
        JComboBox<String> job = new JComboBox<>(new String[]{"LIBRARIAN", "WAREHOUSE", "ADMIN"});
        JPanel p = form("Họ tên", name, "Email", email, "Điện thoại", phone, "Chức vụ", job, "Tên đăng nhập", username, "Mật khẩu khởi tạo", password);
        if (JOptionPane.showConfirmDialog(this, p, "Tạo tài khoản nhân viên", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        char[] rawPassword = password.getPassword();
        try {
            String role = (String) job.getSelectedItem();
            String staffCode = auth.createStaffAccount(name.getText(), email.getText(), phone.getText(), role, username.getText(), rawPassword);
            load();
            message("Đã tạo nhân viên " + staffCode + " và tài khoản " + username.getText().trim() + ".", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            message(ex.getMessage(), JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            message("Không tạo được tài khoản nhân viên: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    private void editSelected() {
        int view = table.getSelectedRow();
        if (view < 0) {
            message("Chọn nhân viên trước.", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = table.convertRowIndexToModel(view);
        long staffId = Long.parseLong(model.getValueAt(row, 1).toString());
        long accountId = Long.parseLong(model.getValueAt(row, 0).toString());
        JTextField name = new JTextField(model.getValueAt(row, 4).toString(), 21);
        JTextField email = new JTextField(model.getValueAt(row, 5).toString(), 21);
        JTextField phone = new JTextField(model.getValueAt(row, 6) == null ? "" : model.getValueAt(row, 6).toString(), 21);
        JComboBox<String> job = new JComboBox<>(new String[]{"LIBRARIAN", "WAREHOUSE", "ADMIN"});
        job.setSelectedItem(model.getValueAt(row, 7).toString());
        JComboBox<String> employment = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
        employment.setSelectedItem(model.getValueAt(row, 10).toString());
        JPanel p = form("Họ tên", name, "Email", email, "Điện thoại", phone, "Chức vụ / vai trò", job, "Tình trạng làm việc", employment);
        if (JOptionPane.showConfirmDialog(this, p, "Cập nhật nhân viên", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        String nextRole = (String) job.getSelectedItem();
        String nextEmployment = (String) employment.getSelectedItem();
        try {
            String updatedName = auth.updateStaffAccount(
                    staffId, accountId, session.accountId(),
                    name.getText(), email.getText(), phone.getText(), nextRole, nextEmployment);
            load();
            if (accountId == session.accountId() && onDisplayNameChanged != null) {
                onDisplayNameChanged.accept(updatedName);
            }
        } catch (IllegalArgumentException ex) {
            message(ex.getMessage(), JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            message("Không cập nhật được: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleAccount() {
        int view = table.getSelectedRow();
        if (view < 0) {
            message("Chọn tài khoản trước.", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = table.convertRowIndexToModel(view);
        long account = Long.parseLong(model.getValueAt(row, 0).toString());
        if (account == session.accountId()) {
            message("Không thể khóa tài khoản đang đăng nhập.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String current = model.getValueAt(row, 9).toString();
        boolean locking = "ACTIVE".equals(current);
        String reason = null;
        if (locking) {
            reason = JOptionPane.showInputDialog(this, "Lý do khóa tài khoản:", "Khóa tài khoản", JOptionPane.PLAIN_MESSAGE);
            if (reason == null) return;
            if (reason.isBlank()) {
                message("Cần nhập lý do khóa.", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (JOptionPane.showConfirmDialog(this, "Xác nhận " + (locking ? "khóa" : "mở khóa") + " tài khoản?",
                "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            auth.setAccountLocked(account, session.accountId(), locking, reason, true);
            load();
        } catch (Exception ex) {
            message("Không đổi được trạng thái tài khoản: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel form(Object... pairs) {
        JPanel p = new JPanel(new GridBagLayout());
        for (int i = 0; i < pairs.length / 2; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(5, 5, 5, 5);
            c.gridy = i;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            p.add(new JLabel((String) pairs[i * 2]), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            p.add((JComponent) pairs[i * 2 + 1], c);
        }
        return p;
    }

    private void message(String s, int t) {
        JOptionPane.showMessageDialog(this, s, "Nhân viên và tài khoản", t);
    }
}
