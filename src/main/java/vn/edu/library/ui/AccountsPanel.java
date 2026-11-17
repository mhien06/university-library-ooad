package vn.edu.library.ui;

import vn.edu.library.data.AuthService;
import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public final class AccountsPanel extends JPanel {
    private final UserSession session;
    private final AuthService auth = new AuthService();
    private final JTextField search = new JTextField(24);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public AccountsPanel(UserSession session) {
        this.session = session;
        setLayout(new BorderLayout(0, 16));
        setBackground(UiStyle.CANVAS);
        setBorder(new EmptyBorder(27, 30, 24, 30));
        add(header(), BorderLayout.NORTH);
        table.setRowHeight(33);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);
        add(status, BorderLayout.SOUTH);
        load();
    }

    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout(0, 16));
        h.setOpaque(false);
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Tài khoản và phân quyền");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Tra cứu tài khoản độc giả và nhân viên; thay đổi vai trò, khóa hoặc mở khóa theo điều kiện.");
        subtitle.setForeground(UiStyle.MUTED);
        title.add(heading);
        title.add(Box.createVerticalStrut(5));
        title.add(subtitle);
        h.add(title, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Mã thành viên, mã nhân viên, tên, email, tên đăng nhập");
        JButton find = new JButton("Tìm kiếm"), refresh = new JButton("Làm mới"),
                role = new JButton("Đổi vai trò"), lock = new JButton("Khóa / mở khóa");
        find.addActionListener(e -> load());
        search.addActionListener(e -> load());
        refresh.addActionListener(e -> { search.setText(""); load(); });
        role.addActionListener(e -> changeRole());
        lock.addActionListener(e -> toggleStatus());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        controls.setOpaque(false);
        for (JComponent c : new JComponent[]{search, find, refresh, role, lock}) controls.add(c);
        h.add(controls, BorderLayout.SOUTH);
        return h;
    }

    private void load() {
        String term = search.getText().trim(), like = "%" + term + "%";
        String sql = "SELECT a.account_id AS 'ID',CASE WHEN a.member_id IS NULL THEN 'NHÂN VIÊN' ELSE 'ĐỘC GIẢ' END AS 'Loại',"
                + "COALESCE(s.staff_code,m.member_code) AS 'Mã hồ sơ',a.username AS 'Tên đăng nhập',COALESCE(s.full_name,m.full_name) AS 'Họ tên',"
                + "COALESCE(s.email,m.email) AS 'Email',COALESCE(s.phone,m.phone) AS 'Điện thoại',"
                + "GROUP_CONCAT(ar.role ORDER BY ar.role SEPARATOR ', ') AS 'Vai trò',a.account_status AS 'Trạng thái',a.lock_reason AS 'Lý do khóa',"
                + "COALESCE(s.employment_status,m.institution_status) AS 'Tình trạng hồ sơ' FROM accounts a "
                + "LEFT JOIN staff_profiles s ON s.staff_id=a.staff_id LEFT JOIN school_members m ON m.member_id=a.member_id "
                + "LEFT JOIN account_roles ar ON ar.account_id=a.account_id WHERE (?='' OR a.username LIKE ? OR s.staff_code LIKE ? OR m.member_code LIKE ? OR s.full_name LIKE ? OR m.full_name LIKE ? OR s.email LIKE ? OR m.email LIKE ?) "
                + "GROUP BY a.account_id ORDER BY COALESCE(s.full_name,m.full_name)";
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, term);
            for (int i = 2; i <= 8; i++) q.setString(i, like);
            try (ResultSet r = q.executeQuery()) {
                ResultSetMetaData md = r.getMetaData();
                model.setRowCount(0);
                model.setColumnCount(0);
                for (int i = 1; i <= md.getColumnCount(); i++) model.addColumn(md.getColumnLabel(i));
                while (r.next()) {
                    Object[] a = new Object[md.getColumnCount()];
                    for (int i = 1; i <= a.length; i++) a[i - 1] = r.getObject(i);
                    model.addRow(a);
                }
            }
            status.setText("Đang hiển thị " + model.getRowCount() + " tài khoản.");
        } catch (Exception ex) {
            model.setRowCount(0);
            status.setText("Không tải được tài khoản: " + ex.getMessage());
        }
    }

    private int selectedRow() {
        int view = table.getSelectedRow();
        if (view < 0) {
            message("Chọn một tài khoản trước.", JOptionPane.INFORMATION_MESSAGE);
            return -1;
        }
        return table.convertRowIndexToModel(view);
    }

    private void changeRole() {
        int row = selectedRow();
        if (row < 0) return;
        long accountId = Long.parseLong(model.getValueAt(row, 0).toString());
        boolean member = "ĐỘC GIẢ".equals(model.getValueAt(row, 1).toString());
        if (member) {
            message("Tài khoản độc giả chỉ có vai trò READER; không thể chuyển sang vai trò nhân viên.", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] roles = new String[]{"LIBRARIAN", "WAREHOUSE", "ADMIN"};
        String current = model.getValueAt(row, 7) == null ? "" : model.getValueAt(row, 7).toString();
        String initial = current.contains(",") ? roles[0] : current;
        if (!java.util.Arrays.asList(roles).contains(initial)) initial = roles[0];
        String chosen = (String) JOptionPane.showInputDialog(this, "Vai trò mới cho " + model.getValueAt(row, 3) + ":",
                "Phân quyền", JOptionPane.PLAIN_MESSAGE, null, roles, initial);
        if (chosen == null) return;
        try {
            auth.changeStaffRole(accountId, session.accountId(), chosen);
            load();
        } catch (Exception ex) {
            message("Không cập nhật được vai trò: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleStatus() {
        int row = selectedRow();
        if (row < 0) return;
        long id = Long.parseLong(model.getValueAt(row, 0).toString());
        String current = model.getValueAt(row, 8).toString();
        boolean locking = "ACTIVE".equals(current);
        if (locking && id == session.accountId()) {
            message("Không thể khóa tài khoản đang đăng nhập.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String reason = null;
        if (locking) {
            reason = JOptionPane.showInputDialog(this, "Lý do khóa tài khoản:", "Khóa tài khoản", JOptionPane.PLAIN_MESSAGE);
            if (reason == null) return;
            if (reason.isBlank()) {
                message("Cần nhập lý do khóa.", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (JOptionPane.showConfirmDialog(this, "Xác nhận " + (locking ? "khóa" : "mở khóa") + " tài khoản " + model.getValueAt(row, 3) + "?",
                "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            auth.setAccountLocked(id, session.accountId(), locking, reason, false);
            load();
        } catch (Exception ex) {
            message("Không đổi được trạng thái: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void message(String text, int type) {
        JOptionPane.showMessageDialog(this, text, "Tài khoản và phân quyền", type);
    }
}
