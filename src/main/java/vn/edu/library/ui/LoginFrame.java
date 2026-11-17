package vn.edu.library.ui;

import vn.edu.library.data.AuthService;
import vn.edu.library.domain.RolePolicy;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public final class LoginFrame extends JFrame {
    private final JTextField username = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final JLabel feedback = new JLabel(" ");

    public LoginFrame() {
        super("Đăng nhập · Thư viện Đại học");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 560));
        setSize(980, 620);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new GridLayout(1, 2));
        root.setBackground(new Color(234, 239, 247));

        JPanel welcome = new JPanel();
        welcome.setBackground(UiStyle.NAVY);
        welcome.setBorder(new EmptyBorder(58, 58, 52, 52));
        welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));

        JLabel product = new JLabel("THƯ VIỆN ĐẠI HỌC");
        product.setForeground(new Color(171, 196, 239));
        product.setFont(new Font("Segoe UI", Font.BOLD, 12));
        product.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel welcomeTitle = new JLabel("Bảng điều hành\nthư viện");
        welcomeTitle.setForeground(Color.WHITE);
        welcomeTitle.setFont(new Font("Segoe UI", Font.BOLD, 36));
        welcomeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel welcomeText = new JLabel("<html><div style='width:330px'>Quản lý thành viên, kho sách, giao dịch mượn trả và vận hành thư viện trên một hệ thống.</div></html>");
        welcomeText.setForeground(new Color(206, 218, 236));
        welcomeText.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        welcomeText.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel roleStrip = new JPanel(new GridLayout(3, 1, 0, 8));
        roleStrip.setOpaque(false);
        roleStrip.setBorder(new EmptyBorder(34, 0, 0, 0));
        roleStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        roleStrip.add(roleLabel("QUẢN TRỊ", "Tài khoản, quyền và phân công"));
        roleStrip.add(roleLabel("THỦ THƯ", "Bạn đọc, thẻ, mượn trả và báo cáo"));
        roleStrip.add(roleLabel("THỦ KHO", "Nhập kho, nhà cung cấp và tồn sách"));
        JLabel version = new JLabel("HỆ THỐNG NỘI BỘ · ĐĂNG NHẬP AN TOÀN");
        version.setForeground(new Color(139, 159, 187));
        version.setFont(new Font("Segoe UI", Font.BOLD, 10));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        welcome.add(product);
        welcome.add(Box.createVerticalStrut(19));
        welcome.add(welcomeTitle);
        welcome.add(Box.createVerticalStrut(17));
        welcome.add(welcomeText);
        welcome.add(roleStrip);
        welcome.add(Box.createVerticalGlue());
        welcome.add(version);

        JPanel signInArea = new JPanel(new GridBagLayout());
        signInArea.setBackground(new Color(247, 249, 252));
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(new LineBorder(new Color(223, 229, 239)), new EmptyBorder(34, 42, 32, 42)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(380, 475));

        JLabel icon = new JLabel("LIB", SwingConstants.CENTER);
        icon.setOpaque(true);
        icon.setBackground(UiStyle.BLUE);
        icon.setForeground(Color.WHITE);
        icon.setFont(new Font("Segoe UI", Font.BOLD, 15));
        icon.setPreferredSize(new Dimension(52, 34));
        icon.setMaximumSize(new Dimension(52, 34));
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel eyebrow = new JLabel("KHU VỰC NHÂN VIÊN");
        eyebrow.setForeground(UiStyle.BLUE);
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 11));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("Đăng nhập quản lí");
        title.setFont(new Font("Segoe UI", Font.BOLD, 27));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Dùng tài khoản được quản trị viên cấp.");
        subtitle.setForeground(UiStyle.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(icon);
        card.add(Box.createVerticalStrut(18));
        card.add(eyebrow);
        card.add(Box.createVerticalStrut(8));
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(28));

        card.add(label("Tên đăng nhập"));
        card.add(Box.createVerticalStrut(6));
        styleField(username);
        card.add(username);
        card.add(Box.createVerticalStrut(16));
        card.add(label("Mật khẩu"));
        card.add(Box.createVerticalStrut(6));
        styleField(password);
        card.add(password);
        card.add(Box.createVerticalStrut(9));
        feedback.setForeground(new Color(184, 43, 52));
        feedback.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(feedback);
        card.add(Box.createVerticalStrut(7));

        JButton login = new JButton("Đăng nhập");
        login.setBackground(UiStyle.BLUE);
        login.setForeground(Color.WHITE);
        login.setFocusPainted(false);
        login.setFont(login.getFont().deriveFont(Font.BOLD, 15f));
        login.setAlignmentX(Component.LEFT_ALIGNMENT);
        login.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        login.addActionListener(e -> authenticate(login));
        password.addActionListener(e -> authenticate(login));
        card.add(login);
        card.add(Box.createVerticalStrut(18));
        JLabel help = new JLabel("Liên hệ quản trị viên khi cần cấp lại mật khẩu.");
        help.setFont(help.getFont().deriveFont(11f));
        help.setForeground(UiStyle.MUTED);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(help);

        signInArea.add(card);
        root.add(welcome);
        root.add(signInArea);
        setContentPane(root);
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void styleField(JComponent field) {
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setBorder(new CompoundBorder(new LineBorder(new Color(202, 212, 226)), new EmptyBorder(7, 10, 7, 10)));
    }

    private JComponent roleLabel(String role, String detail) {
        JPanel line = new JPanel();
        line.setOpaque(false);
        line.setLayout(new BoxLayout(line, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(role);
        name.setForeground(new Color(238, 244, 255));
        name.setFont(new Font("Segoe UI", Font.BOLD, 11));
        JLabel description = new JLabel(detail);
        description.setForeground(new Color(171, 190, 219));
        description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        line.add(name);
        line.add(Box.createVerticalStrut(2));
        line.add(description);
        return line;
    }

    private void authenticate(JButton button) {
        button.setEnabled(false);
        char[] rawPassword = password.getPassword();
        try {
            if (username.getText().isBlank() || rawPassword.length == 0) {
                feedback.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
                return;
            }
            UserSession session = new AuthService().authenticate(username.getText(), rawPassword);
            if (session == null) {
                feedback.setText("Thông tin đăng nhập không đúng hoặc tài khoản đã bị khóa.");
                return;
            }
            if (!RolePolicy.isStaff(session)) {
                feedback.setText("Tài khoản độc giả chỉ dùng trên cổng web, không dùng ứng dụng quản lí.");
                password.setText("");
                return;
            }
            dispose();
            new LibraryManagementFrame(session).setVisible(true);
        } catch (Exception ex) {
            feedback.setText("Không thể đăng nhập: " + ex.getMessage());
        } finally {
            java.util.Arrays.fill(rawPassword, '\0');
            button.setEnabled(true);
        }
    }
}
