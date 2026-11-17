package vn.edu.library.ui;

import vn.edu.library.data.Database;
import vn.edu.library.domain.RolePolicy;
import vn.edu.library.domain.StaffPermission;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class DashboardPanel extends JPanel {
    private enum Mode { LIBRARY, WAREHOUSE, ADMIN }

    private final Mode mode;
    private final JLabel heading;
    private final JLabel[] metricValues = {new JLabel("—"), new JLabel("—"), new JLabel("—"), new JLabel("—")};
    private final JLabel status = new JLabel(" ");

    public DashboardPanel(UserSession session) {
        this.mode = resolveMode(session);
        setLayout(new BorderLayout(0, 24));
        setBackground(UiStyle.CANVAS);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        heading = new JLabel("Xin chào, " + session.displayName());
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
        JLabel subtitle = new JLabel(subtitleText());
        subtitle.setForeground(UiStyle.MUTED);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(heading);
        titles.add(Box.createVerticalStrut(5));
        titles.add(subtitle);
        JButton refresh = new JButton("Làm mới số liệu");
        refresh.addActionListener(e -> loadMetrics());
        top.add(titles, BorderLayout.WEST);
        top.add(refresh, BorderLayout.EAST);

        JPanel metrics = new JPanel(new GridLayout(1, 4, 16, 16));
        metrics.setOpaque(false);
        String[][] metricMeta = metricSpecs();
        for (int i = 0; i < 4; i++) {
            metrics.add(metric(metricMeta[i][0], metricValues[i], metricMeta[i][1]));
        }

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        JLabel section = new JLabel("Phạm vi quản lý");
        section.setFont(section.getFont().deriveFont(Font.BOLD, 18f));
        content.add(section, BorderLayout.NORTH);
        String[][] cardSpecs = actionCardSpecs();
        int cols = cardSpecs.length == 4 ? 2 : 3;
        JPanel actions = new JPanel(new GridLayout(0, cols, 14, 14));
        actions.setOpaque(false);
        for (String[] spec : cardSpecs) {
            actions.add(actionCard(spec[0], spec[1]));
        }
        content.add(actions, BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout());
        lower.setOpaque(false);
        lower.add(content, BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);
        lower.add(status, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(metrics, BorderLayout.CENTER);
        add(lower, BorderLayout.SOUTH);
        loadMetrics();
    }

    public void refreshDisplayName(String displayName) {
        heading.setText("Xin chào, " + displayName);
    }

    private static Mode resolveMode(UserSession session) {
        if (RolePolicy.allows(session, StaffPermission.MANAGE_LOANS)
                || RolePolicy.allows(session, StaffPermission.MANAGE_SCHOOL_MEMBERS)) {
            return Mode.LIBRARY;
        }
        if (RolePolicy.allows(session, StaffPermission.MANAGE_STOCK)) {
            return Mode.WAREHOUSE;
        }
        return Mode.ADMIN;
    }

    private String subtitleText() {
        return switch (mode) {
            case LIBRARY -> "Tổng quan hoạt động thư viện hôm nay";
            case WAREHOUSE -> "Tổng quan kho sách và tiếp nhận nhập hàng";
            case ADMIN -> "Tổng quan quản trị tài khoản và phân quyền";
        };
    }

    private String[][] metricSpecs() {
        return switch (mode) {
            case LIBRARY -> new String[][]{
                    {"Độc giả trong trường", "Sinh viên · học viên · giảng viên"},
                    {"Bản sách sẵn sàng", "Có thể cho mượn tại quầy"},
                    {"Lượt mượn đang mở", "Phiếu chưa hoàn tất trả sách"},
                    {"Sách quá hạn", "Cần theo dõi xử lý"}
            };
            case WAREHOUSE -> new String[][]{
                    {"Đầu sách hoạt động", "Đầu sách đang lưu hành"},
                    {"Bản sách sẵn sàng", "Bản sách trong kho sẵn sàng phục vụ"},
                    {"Phiếu nhập nháp", "Chờ hoàn tất kiểm nhận kho"},
                    {"Yêu cầu NCC đã gửi", "Đơn đề nghị nhập sách"}
            };
            case ADMIN -> new String[][]{
                    {"Tài khoản hoạt động", "Nhân viên và độc giả đang ACTIVE"},
                    {"Tài khoản bị khóa", "Đang ở trạng thái LOCKED"},
                    {"Nhân viên làm việc", "Hồ sơ nhân sự đang ACTIVE"},
                    {"Nhiệm vụ đang mở", "Công việc OPEN hoặc IN_PROGRESS"}
            };
        };
    }

    private String[][] actionCardSpecs() {
        return switch (mode) {
            case LIBRARY -> new String[][]{
                    {"Quản lý thành viên trường", "Hồ sơ sinh viên, học viên và giảng viên"},
                    {"Danh mục sách", "Đầu sách, tác giả, thể loại, NXB và bản sách"},
                    {"Mượn – trả", "Lập phiếu, nhận trả và gia hạn"},
                    {"Đặt trước", "Duyệt yêu cầu đặt sách trực tuyến"},
                    {"Thẻ & vi phạm", "Theo dõi thẻ, tiền phạt và bồi thường"},
                    {"Báo cáo", "Tổng hợp số liệu hoạt động thư viện"}
            };
            case WAREHOUSE -> new String[][]{
                    {"Nhập sách", "Tạo yêu cầu nhà cung cấp và lập phiếu tiếp nhận kho"},
                    {"Danh mục sách", "Tra cứu đầu sách và số lượng tồn theo tình trạng"},
                    {"Tài khoản cá nhân", "Cập nhật thông tin liên hệ và đổi mật khẩu"}
            };
            case ADMIN -> new String[][]{
                    {"Nhân viên", "Tạo tài khoản nhân viên, sửa hồ sơ và trạng thái làm việc"},
                    {"Tài khoản & phân quyền", "Đổi vai trò nhân viên và khóa/mở khóa tài khoản"},
                    {"Phân công nhiệm vụ", "Giao nhiệm vụ và theo dõi tiến độ nhân viên"},
                    {"Tài khoản cá nhân", "Cập nhật thông tin cá nhân và đổi mật khẩu"}
            };
        };
    }

    private String metricsSql() {
        return switch (mode) {
            case LIBRARY -> "SELECT "
                    + "(SELECT COUNT(*) FROM school_members WHERE institution_status='ACTIVE') AS m0, "
                    + "(SELECT COUNT(*) FROM book_copies WHERE copy_status='AVAILABLE') AS m1, "
                    + "(SELECT COUNT(*) FROM loans WHERE loan_status IN ('OPEN','PARTIALLY_RETURNED')) AS m2, "
                    + "(SELECT COUNT(*) FROM loan_items li JOIN loans l ON l.loan_id=li.loan_id "
                    + "WHERE l.loan_status IN ('OPEN','PARTIALLY_RETURNED') AND li.returned_at IS NULL AND li.due_at < CURRENT_DATE()) AS m3";
            case WAREHOUSE -> "SELECT "
                    + "(SELECT COUNT(*) FROM books WHERE record_status='ACTIVE') AS m0, "
                    + "(SELECT COUNT(*) FROM book_copies WHERE copy_status='AVAILABLE') AS m1, "
                    + "(SELECT COUNT(*) FROM stock_receipts WHERE receipt_status='DRAFT') AS m2, "
                    + "(SELECT COUNT(*) FROM supplier_requests WHERE request_status='SENT') AS m3";
            case ADMIN -> "SELECT "
                    + "(SELECT COUNT(*) FROM accounts WHERE account_status='ACTIVE') AS m0, "
                    + "(SELECT COUNT(*) FROM accounts WHERE account_status='LOCKED') AS m1, "
                    + "(SELECT COUNT(*) FROM staff_profiles WHERE employment_status='ACTIVE') AS m2, "
                    + "(SELECT COUNT(*) FROM employee_assignments WHERE assignment_status IN ('OPEN','IN_PROGRESS')) AS m3";
        };
    }

    private JPanel metric(String title, JLabel value, String detail) {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(230, 234, 240)),
                new EmptyBorder(18, 18, 16, 18)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setForeground(UiStyle.MUTED);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 32f));
        value.setForeground(UiStyle.NAVY);
        JLabel caption = new JLabel(detail);
        caption.setForeground(UiStyle.MUTED);
        caption.setFont(caption.getFont().deriveFont(11f));
        panel.add(name);
        panel.add(Box.createVerticalStrut(15));
        panel.add(value);
        panel.add(Box.createVerticalStrut(7));
        panel.add(caption);
        return panel;
    }

    private JPanel actionCard(String title, String detail) {
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        JLabel description = new JLabel("<html><div style='width:190px'>" + detail + "</div></html>");
        description.setForeground(UiStyle.MUTED);
        card.add(label);
        card.add(Box.createVerticalStrut(8));
        card.add(description);
        return card;
    }

    private void loadMetrics() {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(metricsSql());
             ResultSet row = statement.executeQuery()) {
            if (row.next()) {
                for (int i = 0; i < 4; i++) {
                    metricValues[i].setText(row.getString("m" + i));
                }
            }
            status.setText("Số liệu được tổng hợp trực tiếp từ cơ sở dữ liệu.");
        } catch (Exception ex) {
            status.setText("Không tải được số liệu: " + ex.getMessage());
        }
    }
}
