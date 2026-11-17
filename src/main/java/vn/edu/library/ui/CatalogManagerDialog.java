package vn.edu.library.ui;

import vn.edu.library.data.Database;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Librarian maintenance for book lookup tables; linked lookup records cannot be deleted. */
public final class CatalogManagerDialog extends JDialog {
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<CatalogTab> catalogs = new ArrayList<>();

    public CatalogManagerDialog(Window owner) {
        super(owner, "Quản lý danh mục sách", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(0, 10));
        setMinimumSize(new Dimension(860, 500));
        setSize(980, 590);
        setLocationRelativeTo(owner);
        add(tabs, BorderLayout.CENTER);
        add(new JLabel("  Danh mục đang được đầu sách sử dụng sẽ không thể xóa."), BorderLayout.NORTH);
        catalogs.add(new CatalogTab("Thể loại", "categories", "category_id", "category_name",
                new String[]{"category_id", "category_name", "description", "is_active"},
                new String[]{"Mã", "Tên thể loại", "Mô tả", "Đang hoạt động"}, new String[]{"Tên thể loại", "Mô tả"}, true));
        catalogs.add(new CatalogTab("Tác giả", "authors", "author_id", "author_name",
                new String[]{"author_id", "author_name", "biography"},
                new String[]{"Mã", "Tác giả", "Tiểu sử"}, new String[]{"Tên tác giả", "Tiểu sử"}, false));
        catalogs.add(new CatalogTab("Nhà xuất bản", "publishers", "publisher_id", "publisher_name",
                new String[]{"publisher_id", "publisher_name", "address", "phone", "email"},
                new String[]{"Mã", "Nhà xuất bản", "Địa chỉ", "Điện thoại", "Email"},
                new String[]{"Tên NXB", "Địa chỉ", "Điện thoại", "Email"}, false));
        for (CatalogTab catalog : catalogs) tabs.addTab(catalog.title, catalog);
        catalogs.forEach(CatalogTab::load);
    }

    private final class CatalogTab extends JPanel {
        private final String title, tableName, idColumn, nameColumn;
        private final String[] selectColumns, headers, fields;
        private final boolean hasActive;
        private final DefaultTableModel model = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        private final JTable table = new JTable(model);

        CatalogTab(String title, String tableName, String idColumn, String nameColumn,
                   String[] selectColumns, String[] headers, String[] fields, boolean hasActive) {
            this.title = title; this.tableName = tableName; this.idColumn = idColumn; this.nameColumn = nameColumn;
            this.selectColumns = selectColumns; this.headers = headers; this.fields = fields; this.hasActive = hasActive;
            setLayout(new BorderLayout(0, 8));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            table.setRowHeight(28);
            table.setAutoCreateRowSorter(true);
            add(new JScrollPane(table), BorderLayout.CENTER);
            JButton add = new JButton("Thêm"), edit = new JButton("Sửa"), delete = new JButton("Xóa");
            add.addActionListener(e -> editRecord(-1));
            edit.addActionListener(e -> editSelected());
            delete.addActionListener(e -> deleteSelected());
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            actions.add(add); actions.add(edit); actions.add(delete);
            add(actions, BorderLayout.SOUTH);
        }

        void load() {
            String sql = "SELECT " + String.join(",", selectColumns) + " FROM " + tableName + " ORDER BY " + nameColumn;
            try (Connection connection = Database.getConnection(); Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(sql)) {
                ResultSetMetaData metadata = rows.getMetaData();
                model.setRowCount(0); model.setColumnCount(0);
                for (String header : headers) model.addColumn(header);
                while (rows.next()) {
                    Object[] values = new Object[metadata.getColumnCount()];
                    for (int i = 1; i <= values.length; i++) values[i - 1] = rows.getObject(i);
                    model.addRow(values);
                }
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Không tải được " + title + ": " + ex.getMessage(), "Danh mục sách", JOptionPane.ERROR_MESSAGE); }
        }

        private void editSelected() {
            int view = table.getSelectedRow();
            if (view < 0) { JOptionPane.showMessageDialog(this, "Chọn một dòng trước."); return; }
            editRecord(table.convertRowIndexToModel(view));
        }

        private void editRecord(int row) {
            boolean create = row < 0;
            JTextField[] inputs = new JTextField[fields.length];
            JPanel form = new JPanel(new GridBagLayout());
            for (int i = 0; i < fields.length; i++) {
                inputs[i] = new JTextField(create || model.getValueAt(row, i + 1) == null ? "" : model.getValueAt(row, i + 1).toString(), 27);
                GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(5, 5, 5, 5); c.gridy = i; c.gridx = 0; c.anchor = GridBagConstraints.WEST;
                form.add(new JLabel(fields[i] + (i == 0 ? " *" : "")), c);
                c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(inputs[i], c);
            }
            if (JOptionPane.showConfirmDialog(this, form, (create ? "Thêm " : "Sửa ") + title, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            String name = inputs[0].getText().trim();
            if (name.isBlank()) { JOptionPane.showMessageDialog(this, fields[0] + " là bắt buộc."); return; }
            try (Connection connection = Database.getConnection()) {
                String sql;
                if (create) {
                    List<String> columns = new ArrayList<>(); columns.add(nameColumn);
                    for (int i = 1; i < fields.length; i++) columns.add(switch (tableName) {
                        case "categories" -> "description";
                        case "authors" -> "biography";
                        default -> new String[]{"", "address", "phone", "email"}[i];
                    });
                    if (hasActive) { columns.add("is_active"); }
                    sql = "INSERT INTO " + tableName + "(" + String.join(",", columns) + ") VALUES(" + "?,".repeat(columns.size());
                    sql = sql.substring(0, sql.length() - 1) + ")";
                } else {
                    List<String> assignments = new ArrayList<>();
                    for (int i = 0; i < fields.length; i++) assignments.add((i == 0 ? nameColumn : switch (tableName) {
                        case "categories" -> "description";
                        case "authors" -> "biography";
                        default -> new String[]{"", "address", "phone", "email"}[i];
                    }) + "=?");
                    sql = "UPDATE " + tableName + " SET " + String.join(",", assignments) + " WHERE " + idColumn + "=?";
                }
                try (PreparedStatement update = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (JTextField input : inputs) update.setString(index++, blankToNull(input.getText()));
                    if (create && hasActive) update.setBoolean(index++, true);
                    if (!create) update.setLong(index, Long.parseLong(model.getValueAt(row, 0).toString()));
                    update.executeUpdate();
                }
                load();
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Không lưu được " + title + ": " + ex.getMessage(), "Danh mục sách", JOptionPane.ERROR_MESSAGE); }
        }

        private void deleteSelected() {
            int view = table.getSelectedRow();
            if (view < 0) { JOptionPane.showMessageDialog(this, "Chọn một dòng trước."); return; }
            int row = table.convertRowIndexToModel(view);
            long id = Long.parseLong(model.getValueAt(row, 0).toString());
            String label = model.getValueAt(row, 1).toString();
            if (JOptionPane.showConfirmDialog(this, "Xóa '" + label + "'?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            String relation = switch (tableName) {
                case "categories" -> "SELECT COUNT(*) FROM books WHERE category_id=?";
                case "authors" -> "SELECT COUNT(*) FROM book_authors WHERE author_id=?";
                default -> "SELECT COUNT(*) FROM books WHERE publisher_id=?";
            };
            try (Connection connection = Database.getConnection(); PreparedStatement check = connection.prepareStatement(relation)) {
                check.setLong(1, id);
                try (ResultSet result = check.executeQuery()) {
                    result.next();
                    if (result.getInt(1) > 0) throw new IllegalArgumentException(title + " đang được đầu sách sử dụng, không thể xóa.");
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE " + idColumn + "=?")) {
                    delete.setLong(1, id); delete.executeUpdate();
                }
                load();
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Không xóa được: " + ex.getMessage(), "Danh mục sách", JOptionPane.ERROR_MESSAGE); }
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
