package vn.edu.library.ui;

import vn.edu.library.data.AuthService;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public final class AccountSettingsDialog extends JDialog {
    private final AuthService auth = new AuthService();
    private final UserSession session;
    private final Consumer<String> onProfileUpdated;
    private final JTextField fullName = new JTextField(24);
    private final JTextField email = new JTextField(24);
    private final JTextField phone = new JTextField(24);
    private final JPasswordField currentPassword = new JPasswordField(24);
    private final JPasswordField newPassword = new JPasswordField(24);
    private final JPasswordField confirmPassword = new JPasswordField(24);

    public AccountSettingsDialog(Window owner, UserSession session) {
        this(owner, session, null);
    }

    public AccountSettingsDialog(Window owner, UserSession session, Consumer<String> onProfileUpdated) {
        super(owner, "Tài khoản cá nhân", ModalityType.APPLICATION_MODAL);
        this.session = session;
        this.onProfileUpdated = onProfileUpdated;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 450);
        setLocationRelativeTo(owner);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Thông tin", profilePanel());
        tabs.addTab("Đổi mật khẩu", passwordPanel());
        add(tabs);
        loadProfile();
    }

    private JPanel profilePanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 16));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        JPanel form = new JPanel(new GridBagLayout());
        row(form, 0, "Tên đăng nhập", new JLabel(session.username()));
        row(form, 1, "Họ và tên", fullName);
        row(form, 2, "Email", email);
        row(form, 3, "Điện thoại", phone);
        JButton save = new JButton("Lưu thông tin");
        save.addActionListener(e -> saveProfile());
        JLabel note = new JLabel("Mã thành viên và thông tin đào tạo do nhà trường quản lý.");
        note.setForeground(UiStyle.MUTED);
        panel.add(form, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(note, BorderLayout.CENTER);
        bottom.add(save, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel passwordPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 16));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        JPanel form = new JPanel(new GridBagLayout());
        row(form, 0, "Mật khẩu hiện tại", currentPassword);
        row(form, 1, "Mật khẩu mới (tối thiểu 8 ký tự)", newPassword);
        row(form, 2, "Xác nhận mật khẩu mới", confirmPassword);
        JButton save = new JButton("Đổi mật khẩu");
        save.addActionListener(e -> changePassword());
        panel.add(form, BorderLayout.CENTER);
        panel.add(save, BorderLayout.SOUTH);
        return panel;
    }

    private void loadProfile() {
        try {
            AuthService.AccountProfile profile = auth.profile(session.accountId());
            fullName.setText(profile.fullName() == null ? "" : profile.fullName());
            email.setText(profile.email() == null ? "" : profile.email());
            phone.setText(profile.phone() == null ? "" : profile.phone());
            if (!profile.staff()) fullName.setEditable(false);
        } catch (Exception ex) {
            showError("Không tải được hồ sơ tài khoản: " + ex.getMessage());
        }
    }

    private void saveProfile() {
        try {
            String updatedName = auth.updateProfile(session.accountId(), fullName.getText(), email.getText(), phone.getText());
            if (onProfileUpdated != null && updatedName != null) {
                onProfileUpdated.accept(updatedName);
            }
            JOptionPane.showMessageDialog(this, "Đã cập nhật thông tin tài khoản.", "Tài khoản", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) { showError(ex.getMessage()); }
    }

    private void changePassword() {
        char[] current = currentPassword.getPassword();
        char[] next = newPassword.getPassword();
        char[] confirm = confirmPassword.getPassword();
        try {
            auth.changePassword(session.accountId(), current, next, confirm);
            currentPassword.setText(""); newPassword.setText(""); confirmPassword.setText("");
            JOptionPane.showMessageDialog(this, "Đã đổi mật khẩu.", "Tài khoản", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) { showError(ex.getMessage()); }
        finally { java.util.Arrays.fill(current, '\0'); java.util.Arrays.fill(next, '\0'); java.util.Arrays.fill(confirm, '\0'); }
    }

    private void row(JPanel panel, int y, String label, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 5, 7, 5); c.gridy = y; c.gridx = 0; c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, c);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Tài khoản", JOptionPane.ERROR_MESSAGE);
    }
}
