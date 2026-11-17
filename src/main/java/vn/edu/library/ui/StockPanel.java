package vn.edu.library.ui;

import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StockPanel extends JPanel {
    private final UserSession session;
    private final JTextField search = new JTextField(25);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public StockPanel(UserSession session) {
        this.session = session;
        setLayout(new BorderLayout(0, 16));
        setBackground(UiStyle.CANVAS);
        setBorder(new EmptyBorder(27, 30, 24, 30));
        add(header(), BorderLayout.NORTH);
        table.setRowHeight(33);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(229, 233, 240)));
        add(scroll, BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);
        add(status, BorderLayout.SOUTH);
        load();
    }

    private JComponent header() {
        JPanel header = new JPanel(new BorderLayout(0, 16));
        header.setOpaque(false);
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Nhập sách");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Lập phiếu nhiều đầu sách, kiểm đếm thực nhận và lưu sai lệch trước khi cập nhật kho.");
        subtitle.setForeground(UiStyle.MUTED);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitle);
        header.add(titlePanel, BorderLayout.NORTH);

        search.putClientProperty("JTextField.placeholderText", "Mã phiếu, nhà cung cấp, thủ kho");
        JButton find = new JButton("Tìm kiếm");
        JButton refresh = new JButton("Làm mới");
        JButton receive = new JButton("Lập phiếu tiếp nhận");
        JButton requestSupplier = new JButton("Yêu cầu nhập NCC");
        receive.setBackground(UiStyle.BLUE);
        receive.setForeground(Color.WHITE);
        requestSupplier.addActionListener(e -> new SupplierRequestsDialog(SwingUtilities.getWindowAncestor(this), session).setVisible(true));
        find.addActionListener(e -> load());
        refresh.addActionListener(e -> { search.setText(""); load(); });
        search.addActionListener(e -> load());
        receive.addActionListener(e -> receiveStock());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        controls.setOpaque(false);
        controls.add(search);
        controls.add(find);
        controls.add(refresh);
        controls.add(receive);
        controls.add(requestSupplier);
        header.add(controls, BorderLayout.SOUTH);
        return header;
    }

    private void load() {
        String term = search.getText().trim();
        String sql = "SELECT r.receipt_number AS 'Mã phiếu',sp.supplier_name AS 'Nhà cung cấp',s.full_name AS 'Thủ kho',"
                + "r.received_at AS 'Ngày nhận',r.receipt_status AS 'Trạng thái',COUNT(rl.receipt_line_id) AS 'Đầu sách',"
                + "COALESCE(SUM(rl.quantity),0) AS 'Số bản khai báo',COALESCE(SUM(rl.accepted_quantity),0) AS 'Số bản đạt',"
                + "COALESCE(SUM((rl.quantity-rl.accepted_quantity)),0) AS 'Số bản loại',"
                + "COALESCE(SUM(rl.unit_cost*rl.accepted_quantity),0) AS 'Tổng giá trị đạt (VND)' "
                + "FROM stock_receipts r LEFT JOIN suppliers sp ON sp.supplier_id=r.supplier_id "
                + "JOIN staff_profiles s ON s.staff_id=r.received_by LEFT JOIN stock_receipt_lines rl ON rl.receipt_id=r.receipt_id "
                + "WHERE (?='' OR r.receipt_number LIKE ? OR sp.supplier_name LIKE ? OR s.full_name LIKE ?) "
                + "GROUP BY r.receipt_id ORDER BY r.received_at DESC";
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            String pattern = "%" + term + "%";
            query.setString(1, term);
            for (int i = 2; i <= 4; i++) query.setString(i, pattern);
            try (ResultSet result = query.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                model.setRowCount(0);
                model.setColumnCount(0);
                for (int i = 1; i <= metadata.getColumnCount(); i++) model.addColumn(metadata.getColumnLabel(i));
                while (result.next()) {
                    Object[] row = new Object[metadata.getColumnCount()];
                    for (int i = 1; i <= row.length; i++) row[i - 1] = result.getObject(i);
                    model.addRow(row);
                }
            }
            status.setText("Đang hiển thị " + model.getRowCount() + " phiếu nhập.");
        } catch (Exception ex) {
            model.setRowCount(0);
            status.setText("Không tải được phiếu nhập: " + ex.getMessage());
        }
    }

    private void receiveStock() {
        try {
            List<SupplierOption> suppliers = suppliers();
            List<BookOption> books = books();
            if (books.isEmpty()) {
                message("Chưa có đầu sách đang hoạt động. Hãy tạo đầu sách trước khi lập phiếu nhập.", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JComboBox<SupplierOption> supplierBox = new JComboBox<>(suppliers.toArray(SupplierOption[]::new));
            JButton addSupplier = new JButton("Thêm nhà cung cấp");
            JLabel supplierHint = new JLabel(suppliers.isEmpty() ? "Chưa có nhà cung cấp; hãy thêm trước khi lưu." : " ");
            supplierHint.setForeground(UiStyle.MUTED);
            addSupplier.addActionListener(e -> {
                SupplierOption created = createSupplier();
                if (created != null) {
                    supplierBox.addItem(created);
                    supplierBox.setSelectedItem(created);
                    supplierHint.setText("Đã thêm nhà cung cấp.");
                }
            });

            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
            dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "dd/MM/yyyy"));
            DefaultTableModel lines = new DefaultTableModel(new Object[]{"Đầu sách", "SL chứng từ", "SL đạt", "Đơn giá (VND)", "Tình trạng / sai lệch"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return true; }
                @Override public Class<?> getColumnClass(int column) { return column == 1 || column == 2 ? Integer.class : Object.class; }
            };
            JTable lineTable = new JTable(lines);
            lineTable.setRowHeight(28);
            lineTable.setFillsViewportHeight(true);
            lineTable.getColumnModel().getColumn(0).setPreferredWidth(250);
            lineTable.getColumnModel().getColumn(4).setPreferredWidth(260);
            JButton addLine = new JButton("Thêm đầu sách");
            JButton removeLine = new JButton("Xóa dòng chọn");
            addLine.addActionListener(e -> {
                BookOption chosen = (BookOption) JOptionPane.showInputDialog(this, "Chọn đầu sách", "Thêm vào phiếu",
                        JOptionPane.PLAIN_MESSAGE, null, books.toArray(), books.get(0));
                if (chosen != null) lines.addRow(new Object[]{chosen, 1, 1, BigDecimal.ZERO, ""});
            });
            removeLine.addActionListener(e -> {
                int row = lineTable.getSelectedRow();
                if (row >= 0) lines.removeRow(lineTable.convertRowIndexToModel(row));
            });

            JPanel fields = new JPanel(new GridBagLayout());
            fields.setBorder(new EmptyBorder(8, 8, 5, 8));
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4, 5, 4, 5);
            gc.anchor = GridBagConstraints.WEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.gridx = 0; gc.gridy = 0; fields.add(new JLabel("Nhà cung cấp:"), gc);
            gc.gridx = 1; gc.weightx = 1; fields.add(supplierBox, gc);
            gc.gridx = 2; gc.weightx = 0; fields.add(addSupplier, gc);
            gc.gridx = 0; gc.gridy = 1; fields.add(new JLabel("Ngày tiếp nhận:"), gc);
            gc.gridx = 1; gc.gridwidth = 2; fields.add(dateSpinner, gc);
            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 3; fields.add(supplierHint, gc);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttons.add(addLine);
            buttons.add(removeLine);
            JPanel body = new JPanel(new BorderLayout(0, 8));
            body.add(fields, BorderLayout.NORTH);
            body.add(new JScrollPane(lineTable), BorderLayout.CENTER);
            body.add(buttons, BorderLayout.SOUTH);
            body.setPreferredSize(new Dimension(900, 430));

            while (true) {
                int choice = JOptionPane.showConfirmDialog(this, body, "Lập phiếu tiếp nhận sách", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) return;
                if (lineTable.isEditing()) lineTable.getCellEditor().stopCellEditing();
                try {
                    saveReceipt((SupplierOption) supplierBox.getSelectedItem(), dateSpinner.getValue(), lines);
                    message("Đã lưu phiếu tiếp nhận. Kho chỉ tăng theo số lượng sách đạt; số lượng loại và lý do đã được ghi nhận.", JOptionPane.INFORMATION_MESSAGE);
                    load();
                    return;
                } catch (IllegalArgumentException ex) {
                    message(ex.getMessage(), JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) {
                    message("Không thể lưu phiếu nhập: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        } catch (Exception ex) {
            message("Không thể mở biểu mẫu nhập sách: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<SupplierOption> suppliers() throws SQLException {
        List<SupplierOption> result = new ArrayList<>();
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT supplier_id,supplier_name FROM suppliers WHERE is_active=TRUE ORDER BY supplier_name"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) result.add(new SupplierOption(rows.getLong(1), rows.getString(2)));
        }
        return result;
    }

    private List<BookOption> books() throws SQLException {
        List<BookOption> result = new ArrayList<>();
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT book_id,title,isbn FROM books WHERE record_status='ACTIVE' ORDER BY title"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) result.add(new BookOption(rows.getLong(1), rows.getString(2), rows.getString(3)));
        }
        return result;
    }

    private SupplierOption createSupplier() {
        JTextField name = new JTextField(24);
        JTextField contact = new JTextField(24);
        JTextField phone = new JTextField(24);
        JTextField email = new JTextField(24);
        JTextField address = new JTextField(24);
        JPanel form = new JPanel(new GridLayout(0, 2, 7, 7));
        form.add(new JLabel("Tên nhà cung cấp *")); form.add(name);
        form.add(new JLabel("Người liên hệ")); form.add(contact);
        form.add(new JLabel("Điện thoại")); form.add(phone);
        form.add(new JLabel("Email")); form.add(email);
        form.add(new JLabel("Địa chỉ")); form.add(address);
        if (JOptionPane.showConfirmDialog(this, form, "Thêm nhà cung cấp", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        if (name.getText().isBlank()) {
            message("Tên nhà cung cấp là bắt buộc.", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(
                "INSERT INTO suppliers(supplier_name,contact_name,phone,email,address,is_active) VALUES(?,?,?,?,?,TRUE)", Statement.RETURN_GENERATED_KEYS)) {
            query.setString(1, name.getText().trim());
            query.setString(2, blankToNull(contact.getText()));
            query.setString(3, blankToNull(phone.getText()));
            query.setString(4, blankToNull(email.getText()));
            query.setString(5, blankToNull(address.getText()));
            query.executeUpdate();
            try (ResultSet keys = query.getGeneratedKeys()) {
                if (keys.next()) return new SupplierOption(keys.getLong(1), name.getText().trim());
            }
            throw new SQLException("Không tạo được mã nhà cung cấp.");
        } catch (Exception ex) {
            message("Không thể thêm nhà cung cấp: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void saveReceipt(SupplierOption supplier, Object receivedDate, DefaultTableModel lines) throws Exception {
        if (supplier == null) throw new IllegalArgumentException("Hãy chọn hoặc thêm nhà cung cấp.");
        if (lines.getRowCount() == 0) throw new IllegalArgumentException("Phiếu phải có ít nhất một đầu sách.");
        List<ReceiptLine> receiptLines = new ArrayList<>();
        for (int row = 0; row < lines.getRowCount(); row++) {
            Object bookValue = lines.getValueAt(row, 0);
            if (!(bookValue instanceof BookOption book)) throw new IllegalArgumentException("Dòng " + (row + 1) + " chưa chọn đầu sách.");
            int expected = parseInt(lines.getValueAt(row, 1), "Số lượng chứng từ ở dòng " + (row + 1));
            int accepted = parseInt(lines.getValueAt(row, 2), "Số lượng đạt ở dòng " + (row + 1));
            BigDecimal cost = parseMoney(lines.getValueAt(row, 3), row);
            String note = lines.getValueAt(row, 4) == null ? "" : lines.getValueAt(row, 4).toString().trim();
            if (expected <= 0 || accepted < 0 || accepted > expected) throw new IllegalArgumentException("Dòng " + (row + 1) + ": cần 0 ≤ số lượng đạt ≤ số lượng chứng từ và số lượng chứng từ > 0.");
            if (accepted < expected && note.isBlank()) throw new IllegalArgumentException("Dòng " + (row + 1) + " có chênh lệch/hư hỏng; hãy ghi rõ tình trạng hoặc lý do loại.");
            receiptLines.add(new ReceiptLine(book, expected, accepted, cost, note));
        }

        LocalDate day = ((java.util.Date) receivedDate).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        Timestamp receivedAt = Timestamp.valueOf(day.atStartOfDay());
        String receiptNumber = "RC-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long staffId = staffId(connection);
                long receiptId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO stock_receipts(receipt_number,supplier_id,received_by,received_at,receipt_status,notes) VALUES(?,?,?,?,'RECEIVED',?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, receiptNumber);
                    insert.setLong(2, supplier.id());
                    insert.setLong(3, staffId);
                    insert.setTimestamp(4, receivedAt);
                    insert.setString(5, "Đã kiểm đếm từng dòng; kho chỉ cộng số lượng đạt.");
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Không tạo được phiếu nhập.");
                        receiptId = keys.getLong(1);
                    }
                }
                for (ReceiptLine line : receiptLines) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO stock_receipt_lines(receipt_id,book_id,quantity,unit_cost,accepted_quantity,notes) VALUES(?,?,?,?,?,?)")) {
                        insert.setLong(1, receiptId);
                        insert.setLong(2, line.book().id());
                        insert.setInt(3, line.expected());
                        insert.setBigDecimal(4, line.unitCost());
                        insert.setInt(5, line.accepted());
                        insert.setString(6, blankToNull(line.note()));
                        insert.executeUpdate();
                    }
                    if (line.accepted() > 0) {
                        try (PreparedStatement insertCopy = connection.prepareStatement(
                                "INSERT INTO book_copies(book_id,barcode,copy_status,acquired_at,notes) VALUES(?,?,'AVAILABLE',?,?)")) {
                            for (int i = 0; i < line.accepted(); i++) {
                                insertCopy.setLong(1, line.book().id());
                                insertCopy.setString(2, "BC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
                                insertCopy.setDate(3, java.sql.Date.valueOf(day));
                                insertCopy.setString(4, blankToNull(line.note()));
                                insertCopy.addBatch();
                            }
                            insertCopy.executeBatch();
                        }
                    }
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int parseInt(Object value, String label) {
        try { return Integer.parseInt(value.toString().trim()); }
        catch (Exception ex) { throw new IllegalArgumentException(label + " phải là số nguyên."); }
    }

    private BigDecimal parseMoney(Object value, int row) {
        try {
            BigDecimal amount = new BigDecimal(value.toString().trim().replace(",", ""));
            if (amount.signum() < 0) throw new NumberFormatException();
            return amount;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Đơn giá ở dòng " + (row + 1) + " phải là số không âm.");
        }
    }

    private long staffId(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT staff_id FROM accounts WHERE account_id=? AND staff_id IS NOT NULL")) {
            query.setLong(1, session.accountId());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Tài khoản chưa liên kết nhân viên.");
                return row.getLong(1);
            }
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void message(String text, int type) { JOptionPane.showMessageDialog(this, text, "Nhập sách", type); }

    private record SupplierOption(long id, String name) { @Override public String toString() { return name; } }
    private record BookOption(long id, String title, String isbn) {
        @Override public String toString() { return title + (isbn == null ? "" : " · " + isbn); }
    }
    private record ReceiptLine(BookOption book, int expected, int accepted, BigDecimal unitCost, String note) { }
}
