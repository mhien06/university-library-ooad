package vn.edu.library.ui;

import vn.edu.library.domain.RolePolicy;
import vn.edu.library.domain.StaffPermission;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LibraryManagementFrame extends JFrame {
    private UserSession session;
    private final JPanel content = new JPanel(new CardLayout());
    private final Map<String, JButton> navigation = new LinkedHashMap<>();
    private String activePage = "Tổng quan";
    private JLabel avatarLabel;
    private JLabel userNameLabel;
    private DashboardPanel dashboardPanel;

    public LibraryManagementFrame(UserSession session) {
        super("Quản lý thư viện · " + session.displayName());
        this.session = session;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 730));
        setSize(1440, 900);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        content.setBackground(UiStyle.CANVAS);
        buildPages();
        add(content, BorderLayout.CENTER);
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(UiStyle.NAVY);
        sidebar.setPreferredSize(new Dimension(255, 0));
        sidebar.setBorder(new EmptyBorder(20, 13, 16, 13));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("▤  LIBRARY");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        JLabel subtitle = new JLabel("  UNIVERSITY MANAGEMENT");
        subtitle.setForeground(new Color(174, 190, 212));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 9f));
        brand.add(title);
        brand.add(Box.createVerticalStrut(6));
        brand.add(subtitle);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(24, 0, 12, 0));
        addNav(nav, "Tổng quan", StaffPermission.DASHBOARD, "Tổng quan", "Bảng điều khiển hoạt động");
        if (RolePolicy.allows(session, StaffPermission.MANAGE_SCHOOL_MEMBERS)) {
            nav.add(sectionLabel("THƯ VIỆN"));
            addNav(nav, "Thành viên trường", StaffPermission.MANAGE_SCHOOL_MEMBERS, "school_members", "Sinh viên · học viên · giảng viên");
            addNav(nav, "Thẻ thư viện", StaffPermission.MANAGE_LIBRARY_CARDS, "cards", "Đăng ký, cấp và quản lý thẻ");
            addNav(nav, "Sách in", StaffPermission.VIEW_BOOKS, "books", "Đầu sách và bản sách");
            addNav(nav, "Đặt trước", StaffPermission.MANAGE_RESERVATIONS, "reservations", "Yêu cầu đặt sách trực tuyến");
            addNav(nav, "Mượn – trả", StaffPermission.MANAGE_LOANS, "loans", "Phiếu mượn và trả sách");
            addNav(nav, "Vi phạm", StaffPermission.MANAGE_VIOLATIONS, "violations", "Tiền phạt và bồi thường");
            addNav(nav, "Tài liệu điện tử", StaffPermission.MANAGE_DOCUMENTS, "documents", "Tài liệu và tệp đọc trực tuyến");
            addNav(nav, "Báo cáo – thống kê", StaffPermission.VIEW_REPORTS, "reports", "Sách, giao dịch, độc giả, vi phạm");
        }
        if (RolePolicy.allows(session, StaffPermission.MANAGE_STOCK)) {
            nav.add(sectionLabel("KHO SÁCH"));
            addNav(nav, "Nhập sách", StaffPermission.MANAGE_STOCK, "stock", "Phiếu tiếp nhận và kiểm kê");
            if (!RolePolicy.allows(session, StaffPermission.MANAGE_SCHOOL_MEMBERS)) {
                addNav(nav, "Danh mục sách", StaffPermission.VIEW_BOOKS, "books", "Tra cứu đầu sách và tồn kho");
            }
        }
        if (RolePolicy.allows(session, StaffPermission.MANAGE_STAFF)
                || RolePolicy.allows(session, StaffPermission.MANAGE_ACCOUNTS)
                || RolePolicy.allows(session, StaffPermission.MANAGE_ASSIGNMENTS)) {
            nav.add(sectionLabel("QUẢN TRỊ"));
            addNav(nav, "Nhân viên", StaffPermission.MANAGE_STAFF, "staff", "Hồ sơ và phân công nhiệm vụ");
            addNav(nav, "Tài khoản & quyền", StaffPermission.MANAGE_ACCOUNTS, "accounts", "Vai trò và trạng thái tài khoản");
            addNav(nav, "Phân công nhiệm vụ", StaffPermission.MANAGE_ASSIGNMENTS, "assignments", "Giao việc và theo dõi tiến độ nhân viên");
        }
        JScrollPane navScroll = new JScrollPane(nav);
        navScroll.setBorder(null);
        navScroll.getViewport().setOpaque(false);
        navScroll.setOpaque(false);

        JPanel user = new JPanel(new BorderLayout(8, 0));
        user.setOpaque(false);
        user.setBorder(new EmptyBorder(12, 4, 4, 4));
        avatarLabel = new JLabel(initials(session.displayName()), SwingConstants.CENTER);
        avatarLabel.setOpaque(true);
        avatarLabel.setBackground(new Color(64, 86, 119));
        avatarLabel.setForeground(Color.WHITE);
        avatarLabel.setPreferredSize(new Dimension(38, 38));
        userNameLabel = new JLabel(formatUserLabel(session));
        userNameLabel.setForeground(Color.WHITE);
        JButton logout = new JButton("Thoát");
        logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        JPanel userInfo = new JPanel(new BorderLayout(8, 0));
        userInfo.setOpaque(false);
        userInfo.add(avatarLabel, BorderLayout.WEST);
        userInfo.add(userNameLabel, BorderLayout.CENTER);
        user.add(userInfo, BorderLayout.CENTER);
        JButton account = new JButton("Tài khoản");
        account.addActionListener(e -> new AccountSettingsDialog(this, session, this::refreshDisplayName).setVisible(true));
        JPanel actions = new JPanel(new GridLayout(2, 1, 0, 4));
        actions.setOpaque(false);
        actions.add(account);
        actions.add(logout);
        user.add(actions, BorderLayout.EAST);

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(navScroll, BorderLayout.CENTER);
        sidebar.add(user, BorderLayout.SOUTH);
        return sidebar;
    }

    private void buildPages() {
        dashboardPanel = new DashboardPanel(session);
        addPage("Tổng quan", dashboardPanel);
        if (RolePolicy.allows(session, StaffPermission.MANAGE_SCHOOL_MEMBERS)) addPage("school_members", new SchoolMembersPanel());
        if (RolePolicy.allows(session, StaffPermission.MANAGE_LIBRARY_CARDS)) addPage("cards", new CardsPanel(session));
        if (RolePolicy.allows(session, StaffPermission.VIEW_BOOKS)) {
            addPage("books", new BooksPanel(RolePolicy.allows(session, StaffPermission.MANAGE_BOOKS),
                    RolePolicy.allows(session, StaffPermission.MANAGE_CATALOG_LOOKUPS)));
        }
        if (RolePolicy.allows(session, StaffPermission.MANAGE_RESERVATIONS)) addPage("reservations", new ReservationsPanel(session));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_LOANS)) addPage("loans", new LoansPanel(session));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_VIOLATIONS)) addPage("violations", new ViolationsPanel(session));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_DOCUMENTS)) addPage("documents", new DocumentsPanel(session));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_STOCK)) addPage("stock", new StockPanel(session));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_STAFF)) addPage("staff", new StaffAccountsPanel(session, this::refreshDisplayName));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_ACCOUNTS)) addPage("accounts", new AccountsPanel(session));
        if (RolePolicy.allows(session, StaffPermission.MANAGE_ASSIGNMENTS)) addPage("assignments", new AssignmentsPanel(session));
        if (RolePolicy.allows(session, StaffPermission.VIEW_REPORTS)) addPage("reports", new ReportsPanel(session));
    }

    private void refreshDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) return;
        session = new UserSession(session.accountId(), session.username(), displayName, session.roles());
        setTitle("Quản lý thư viện · " + displayName);
        if (avatarLabel != null) avatarLabel.setText(initials(displayName));
        if (userNameLabel != null) userNameLabel.setText(formatUserLabel(session));
        if (dashboardPanel != null) dashboardPanel.refreshDisplayName(displayName);
    }

    private String formatUserLabel(UserSession userSession) {
        return "<html><b>" + escape(userSession.displayName()) + "</b><br><font color='#b2bfd1'>"
                + String.join(", ", userSession.roles()) + "</font></html>";
    }

    private RecordBrowserPanel browser(String title, String subtitle, String sql, List<String> searchColumns) {
        return new RecordBrowserPanel(new RecordBrowserPanel.Definition(title, subtitle, sql, searchColumns));
    }

    private void addPage(String key, JComponent page) { content.add(page, key); }

    private void addNav(JPanel nav, String label, StaffPermission permission, String key, String tooltip) {
        if (!RolePolicy.allows(session, permission)) return;
        JButton button = new JButton("  " + label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(new Color(223, 231, 243));
        button.setBackground(UiStyle.NAVY);
        button.setBorder(new EmptyBorder(10, 9, 10, 8));
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.addActionListener(e -> showPage(label, key));
        navigation.put(label, button);
        nav.add(button);
        nav.add(Box.createVerticalStrut(3));
    }

    private void showPage(String label, String key) {
        activePage = label;
        ((CardLayout) content.getLayout()).show(content, key);
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            boolean active = entry.getKey().equals(activePage);
            entry.getValue().setBackground(active ? new Color(49, 108, 220) : UiStyle.NAVY);
        }
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(139, 159, 187));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setBorder(new EmptyBorder(19, 9, 8, 4));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private String initials(String value) {
        if (value == null || value.isBlank()) return "?";
        String[] parts = value.trim().split("\\s+");
        return parts.length == 1 ? parts[0].substring(0, 1).toUpperCase()
                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
    private String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
