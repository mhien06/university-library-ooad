package vn.edu.library;

import com.formdev.flatlaf.FlatLightLaf;
import vn.edu.library.data.BootstrapService;
import vn.edu.library.data.Database;
import vn.edu.library.ui.LoginFrame;

import javax.swing.*;

public final class App {
    private App() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            UIManager.put("defaultFont", new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
            try {
                Database.checkConnection();
                BootstrapService.ensureAdministrator();
                new LoginFrame().setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Không kết nối được cơ sở dữ liệu.\n" + ex.getMessage()
                                + "\n\nKhởi động MySQL bằng docker compose up -d rồi mở lại ứng dụng.",
                        "Không thể khởi động", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
