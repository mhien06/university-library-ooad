package vn.edu.library.ui;

import vn.edu.library.data.Database;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;

public final class RecordBrowserPanel extends JPanel {
    public record Definition(String title, String subtitle, String sql, List<String> searchColumns) { }

    private final Definition definition;
    private final JTextField search = new JTextField(28);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public RecordBrowserPanel(Definition definition) {
        this.definition = definition;
        setLayout(new BorderLayout(0, 18));
        setBackground(UiStyle.CANVAS);
        setBorder(new EmptyBorder(28, 32, 24, 32));
        add(buildHeader(), BorderLayout.NORTH);
        table.setRowHeight(34);
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
        JLabel title = new JLabel(definition.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel(definition.subtitle());
        subtitle.setForeground(UiStyle.MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(5));
        titles.add(subtitle);
        header.add(titles, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText", "Nhập từ khóa để tìm kiếm");
        JButton find = new JButton("Tìm kiếm");
        JButton refresh = new JButton("Làm mới");
        find.addActionListener(e -> load());
        refresh.addActionListener(e -> { search.setText(""); load(); });
        search.addActionListener(e -> load());
        controls.add(search);
        controls.add(find);
        controls.add(refresh);
        header.add(controls, BorderLayout.SOUTH);
        return header;
    }

    private void load() {
        String term = search.getText().trim();
        String sql = definition.sql();
        if (!term.isBlank() && !definition.searchColumns().isEmpty()) {
            StringBuilder filter = new StringBuilder(" WHERE ");
            for (int i = 0; i < definition.searchColumns().size(); i++) {
                if (i > 0) filter.append(" OR ");
                filter.append(definition.searchColumns().get(i)).append(" LIKE ?");
            }
            int orderIndex = sql.toUpperCase().lastIndexOf(" ORDER BY ");
            sql = orderIndex < 0 ? sql + filter : sql.substring(0, orderIndex) + filter + sql.substring(orderIndex);
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!term.isBlank()) {
                for (int i = 1; i <= definition.searchColumns().size(); i++) statement.setString(i, "%" + term + "%");
            }
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData metadata = rows.getMetaData();
                model.setRowCount(0);
                model.setColumnCount(0);
                for (int i = 1; i <= metadata.getColumnCount(); i++) model.addColumn(metadata.getColumnLabel(i));
                while (rows.next()) {
                    Object[] values = new Object[metadata.getColumnCount()];
                    for (int i = 1; i <= metadata.getColumnCount(); i++) values[i - 1] = rows.getObject(i);
                    model.addRow(values);
                }
            }
            status.setText("Đang hiển thị " + model.getRowCount() + " dòng.");
        } catch (Exception ex) {
            model.setRowCount(0);
            model.setColumnCount(0);
            status.setText("Không tải được dữ liệu: " + ex.getMessage());
        }
    }
}
