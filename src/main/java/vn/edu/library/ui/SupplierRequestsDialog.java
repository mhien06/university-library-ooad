package vn.edu.library.ui;

import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SupplierRequestsDialog extends JDialog {
    private final UserSession session;
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public SupplierRequestsDialog(Window owner, UserSession session) {
        super(owner, "Yêu cầu nhập sách từ nhà cung cấp", ModalityType.APPLICATION_MODAL);
        this.session = session;
        setLayout(new BorderLayout(0, 10));
        setMinimumSize(new Dimension(900, 520));
        setSize(1080, 650);
        setLocationRelativeTo(owner);
        table.setRowHeight(29);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JButton create = new JButton("Tạo yêu cầu"), email = new JButton("Mở email NCC"), cancel = new JButton("Hủy yêu cầu"), refresh = new JButton("Làm mới"), close = new JButton("Đóng");
        create.setBackground(UiStyle.BLUE); create.setForeground(Color.WHITE);
        create.addActionListener(e -> createRequest());
        email.addActionListener(e -> sendRequest());
        cancel.addActionListener(e -> cancelRequest());
        refresh.addActionListener(e -> load());
        close.addActionListener(e -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(create); actions.add(email); actions.add(cancel); actions.add(refresh); actions.add(close);
        add(actions, BorderLayout.NORTH);
        JLabel hint = new JLabel("Yêu cầu được lưu trước; nút email mở thư nháp. Chỉ đánh dấu ĐÃ GỬI sau khi nhân viên gửi thư thực tế.");
        hint.setBorder(new EmptyBorder(0, 8, 8, 8));
        hint.setForeground(UiStyle.MUTED);
        add(hint, BorderLayout.SOUTH);
        load();
    }

    private void load() {
        String sql = "SELECT r.request_id,r.request_number AS 'Mã yêu cầu',s.supplier_name AS 'Nhà cung cấp',s.email AS 'Email NCC',"
                + "sp.full_name AS 'Người lập',r.requested_at AS 'Ngày lập',r.request_status AS 'Trạng thái',"
                + "GROUP_CONCAT(CONCAT(b.title,' × ',rl.requested_quantity) ORDER BY b.title SEPARATOR '; ') AS 'Danh sách sách' "
                + "FROM supplier_requests r JOIN suppliers s ON s.supplier_id=r.supplier_id "
                + "JOIN staff_profiles sp ON sp.staff_id=r.requested_by JOIN supplier_request_lines rl ON rl.request_id=r.request_id "
                + "JOIN books b ON b.book_id=rl.book_id GROUP BY r.request_id ORDER BY r.requested_at DESC";
        try (Connection c = Database.getConnection(); Statement query = c.createStatement(); ResultSet rows = query.executeQuery(sql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            model.setRowCount(0); model.setColumnCount(0);
            for (int i = 1; i <= metadata.getColumnCount(); i++) model.addColumn(metadata.getColumnLabel(i));
            while (rows.next()) {
                Object[] values = new Object[metadata.getColumnCount()];
                for (int i = 1; i <= values.length; i++) values[i - 1] = rows.getObject(i);
                model.addRow(values);
            }
            table.getColumnModel().getColumn(0).setMinWidth(0);
            table.getColumnModel().getColumn(0).setMaxWidth(0);
        } catch (Exception ex) { error("Không tải được yêu cầu nhà cung cấp: " + ex.getMessage()); }
    }

    private void createRequest() {
        try {
            List<SupplierOption> suppliers = loadSuppliers();
            List<BookOption> books = loadBooks();
            if (books.isEmpty()) throw new IllegalArgumentException("Chưa có đầu sách đang hoạt động.");
            JComboBox<SupplierOption> supplier = new JComboBox<>(suppliers.toArray(SupplierOption[]::new));
            JButton addSupplier = new JButton("Thêm NCC");
            JLabel supplierStatus = new JLabel(suppliers.isEmpty() ? "Chưa có NCC đang hoạt động." : " ");
            addSupplier.addActionListener(e -> {
                SupplierOption added = createSupplier();
                if (added != null) { supplier.addItem(added); supplier.setSelectedItem(added); supplierStatus.setText("Đã tạo nhà cung cấp."); }
            });
            JTextArea notes = new JTextArea(3, 30);
            DefaultTableModel lines = new DefaultTableModel(new Object[]{"Đầu sách", "Số lượng", "Giá dự kiến (VND)"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return true; }
                @Override public Class<?> getColumnClass(int column) { return column == 1 ? Integer.class : Object.class; }
            };
            JTable lineTable = new JTable(lines);
            lineTable.setRowHeight(27);
            JButton addLine = new JButton("Thêm sách"), removeLine = new JButton("Xóa dòng");
            addLine.addActionListener(e -> {
                BookOption chosen = (BookOption) JOptionPane.showInputDialog(this, "Chọn đầu sách", "Danh sách yêu cầu",
                        JOptionPane.PLAIN_MESSAGE, null, books.toArray(), books.get(0));
                if (chosen != null) lines.addRow(new Object[]{chosen, 1, 0});
            });
            removeLine.addActionListener(e -> { int view = lineTable.getSelectedRow(); if (view >= 0) lines.removeRow(lineTable.convertRowIndexToModel(view)); });
            JPanel vendor = new JPanel(new BorderLayout(5, 5));
            vendor.add(supplier, BorderLayout.CENTER); vendor.add(addSupplier, BorderLayout.EAST);
            JPanel header = new JPanel(new GridBagLayout());
            addField(header, 0, "Nhà cung cấp", vendor);
            addField(header, 1, "Ghi chú yêu cầu", new JScrollPane(notes));
            addField(header, 2, "", supplierStatus);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT)); controls.add(addLine); controls.add(removeLine);
            JPanel body = new JPanel(new BorderLayout(0, 6)); body.add(header, BorderLayout.NORTH); body.add(new JScrollPane(lineTable), BorderLayout.CENTER); body.add(controls, BorderLayout.SOUTH);
            body.setPreferredSize(new Dimension(760, 430));
            while (true) {
                if (JOptionPane.showConfirmDialog(this, body, "Tạo yêu cầu nhập sách", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
                if (lineTable.isEditing()) lineTable.getCellEditor().stopCellEditing();
                try { saveRequest((SupplierOption) supplier.getSelectedItem(), lines, notes.getText()); load(); return; }
                catch (IllegalArgumentException ex) { error(ex.getMessage()); }
            }
        } catch (Exception ex) { error("Không tạo được yêu cầu: " + ex.getMessage()); }
    }

    private void saveRequest(SupplierOption supplier, DefaultTableModel rows, String note) throws Exception {
        if (supplier == null) throw new IllegalArgumentException("Chọn hoặc thêm nhà cung cấp trước.");
        if (rows.getRowCount() == 0) throw new IllegalArgumentException("Thêm ít nhất một đầu sách.");
        List<RequestLine> lines = new ArrayList<>();
        for (int i = 0; i < rows.getRowCount(); i++) {
            if (!(rows.getValueAt(i, 0) instanceof BookOption book)) throw new IllegalArgumentException("Chọn đầu sách ở dòng " + (i + 1) + ".");
            int quantity; java.math.BigDecimal cost;
            try { quantity = Integer.parseInt(rows.getValueAt(i, 1).toString().trim()); if (quantity <= 0) throw new NumberFormatException(); }
            catch (Exception ex) { throw new IllegalArgumentException("Số lượng ở dòng " + (i + 1) + " phải lớn hơn 0."); }
            try { cost = new java.math.BigDecimal(rows.getValueAt(i, 2).toString().trim().replace(",", "")); if (cost.signum() < 0) throw new NumberFormatException(); }
            catch (Exception ex) { throw new IllegalArgumentException("Giá dự kiến ở dòng " + (i + 1) + " không hợp lệ."); }
            lines.add(new RequestLine(book, quantity, cost));
        }
        String number = "SR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long staff = staffId(c), requestId;
                try (PreparedStatement q = c.prepareStatement("INSERT INTO supplier_requests(request_number,supplier_id,requested_by,notes) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, number); q.setLong(2, supplier.id()); q.setLong(3, staff); q.setString(4, blankToNull(note)); q.executeUpdate();
                    try (ResultSet keys = q.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Không tạo được mã yêu cầu."); requestId = keys.getLong(1); }
                }
                try (PreparedStatement q = c.prepareStatement("INSERT INTO supplier_request_lines(request_id,book_id,requested_quantity,estimated_unit_cost) VALUES(?,?,?,?)")) {
                    for (RequestLine line : lines) { q.setLong(1, requestId); q.setLong(2, line.book().id()); q.setInt(3, line.quantity()); q.setBigDecimal(4, line.cost()); q.addBatch(); }
                    q.executeBatch();
                }
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
        JOptionPane.showMessageDialog(this, "Đã lưu yêu cầu " + number + " ở trạng thái NHÁP.");
    }

    private void sendRequest() {
        int row = selectedRow(); if (row < 0) return;
        long id = Long.parseLong(model.getValueAt(row, 0).toString());
        String state = model.getValueAt(row, 6).toString();
        if (!"DRAFT".equals(state)) { error("Chỉ yêu cầu nháp mới có thể gửi."); return; }
        try (Connection c = Database.getConnection()) {
            String email, number, supplier;
            StringBuilder body = new StringBuilder("Kính gửi Quý Nhà cung cấp,\n\nThư viện đề nghị báo giá/cung cấp các đầu sách sau:\n");
            try (PreparedStatement q = c.prepareStatement("SELECT r.request_number,s.supplier_name,s.email FROM supplier_requests r JOIN suppliers s ON s.supplier_id=r.supplier_id WHERE r.request_id=?")) {
                q.setLong(1, id); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Không tìm thấy yêu cầu."); number=r.getString(1); supplier=r.getString(2); email=r.getString(3); }
            }
            try (PreparedStatement q = c.prepareStatement("SELECT b.title,rl.requested_quantity,rl.estimated_unit_cost FROM supplier_request_lines rl JOIN books b ON b.book_id=rl.book_id WHERE rl.request_id=? ORDER BY b.title")) {
                q.setLong(1, id); try (ResultSet r = q.executeQuery()) { while (r.next()) body.append("- ").append(r.getString(1)).append(" | Số lượng: ").append(r.getInt(2)).append(" | Giá dự kiến: ").append(r.getBigDecimal(3)).append(" VND\n"); }
            }
            body.append("\nMã yêu cầu: ").append(number).append("\n\nTrân trọng.\nThư viện trường đại học");
            if (email == null || email.isBlank()) throw new IllegalArgumentException("Nhà cung cấp chưa có email. Hãy cập nhật email trước khi gửi.");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) throw new IllegalArgumentException("Máy này chưa cấu hình ứng dụng email mặc định; yêu cầu vẫn được lưu ở trạng thái nháp.");
            String subject = URLEncoder.encode("Yêu cầu cung cấp sách " + number, StandardCharsets.UTF_8).replace("+", "%20");
            String message = URLEncoder.encode(body.toString(), StandardCharsets.UTF_8).replace("+", "%20");
            Desktop.getDesktop().mail(URI.create("mailto:" + email.trim() + "?subject=" + subject + "&body=" + message));
            if (JOptionPane.showConfirmDialog(this, "Thư nháp đã mở. Bạn đã gửi email cho " + supplier + "?", "Xác nhận gửi", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try (PreparedStatement update = c.prepareStatement("UPDATE supplier_requests SET request_status='SENT' WHERE request_id=? AND request_status='DRAFT'")) {
                    update.setLong(1, id); if (update.executeUpdate() != 1) throw new IllegalArgumentException("Yêu cầu đã đổi trạng thái.");
                }
                load();
            }
        } catch (Exception ex) { error("Không gửi được yêu cầu: " + ex.getMessage()); }
    }

    private void cancelRequest() {
        int row = selectedRow(); if (row < 0) return;
        if (!"DRAFT".equals(model.getValueAt(row, 6).toString())) { error("Chỉ yêu cầu nháp mới có thể hủy."); return; }
        long id = Long.parseLong(model.getValueAt(row, 0).toString());
        if (JOptionPane.showConfirmDialog(this, "Hủy yêu cầu nhập sách này?", "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement("UPDATE supplier_requests SET request_status='CANCELLED' WHERE request_id=? AND request_status='DRAFT'")) {
            q.setLong(1, id); if (q.executeUpdate() != 1) throw new IllegalArgumentException("Yêu cầu vừa được xử lý."); load();
        } catch (Exception ex) { error("Không hủy được yêu cầu: " + ex.getMessage()); }
    }

    private SupplierOption createSupplier() {
        JTextField name=new JTextField(24), contact=new JTextField(24), phone=new JTextField(24), email=new JTextField(24), address=new JTextField(24);
        JPanel p=new JPanel(new GridLayout(0,2,7,7));p.add(new JLabel("Tên NCC *"));p.add(name);p.add(new JLabel("Người liên hệ"));p.add(contact);p.add(new JLabel("Điện thoại"));p.add(phone);p.add(new JLabel("Email"));p.add(email);p.add(new JLabel("Địa chỉ"));p.add(address);
        if(JOptionPane.showConfirmDialog(this,p,"Thêm nhà cung cấp",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        if(name.getText().isBlank()){error("Tên nhà cung cấp là bắt buộc.");return null;}
        try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO suppliers(supplier_name,contact_name,phone,email,address) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            q.setString(1,name.getText().trim());q.setString(2,blankToNull(contact.getText()));q.setString(3,blankToNull(phone.getText()));q.setString(4,blankToNull(email.getText()));q.setString(5,blankToNull(address.getText()));q.executeUpdate();
            try(ResultSet keys=q.getGeneratedKeys()){if(keys.next())return new SupplierOption(keys.getLong(1),name.getText().trim());}
            throw new SQLException("Không tạo được nhà cung cấp.");
        }catch(Exception ex){error("Không thêm được nhà cung cấp: "+ex.getMessage());return null;}
    }

    private List<SupplierOption> loadSuppliers() throws SQLException {
        List<SupplierOption> out=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement("SELECT supplier_id,supplier_name FROM suppliers WHERE is_active=TRUE ORDER BY supplier_name");ResultSet r=q.executeQuery()){while(r.next())out.add(new SupplierOption(r.getLong(1),r.getString(2)));}
        return out;
    }

    private List<BookOption> loadBooks() throws SQLException {
        List<BookOption> out=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement("SELECT book_id,title,isbn FROM books WHERE record_status='ACTIVE' ORDER BY title");ResultSet r=q.executeQuery()){while(r.next())out.add(new BookOption(r.getLong(1),r.getString(2),r.getString(3)));}
        return out;
    }

    private int selectedRow() { int view=table.getSelectedRow(); if(view<0){error("Chọn yêu cầu trước.");return -1;} return table.convertRowIndexToModel(view); }
    private long staffId(Connection c)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT staff_id FROM accounts WHERE account_id=? AND staff_id IS NOT NULL")){q.setLong(1,session.accountId());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Tài khoản chưa liên kết nhân viên.");return r.getLong(1);}}}
    private void addField(JPanel p,int y,String label,JComponent input){GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(4,5,4,5);c.gridy=y;c.gridx=0;c.anchor=GridBagConstraints.WEST;p.add(new JLabel(label),c);c.gridx=1;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;p.add(input,c);}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private void error(String message){JOptionPane.showMessageDialog(this,message,"Yêu cầu nhà cung cấp",JOptionPane.WARNING_MESSAGE);}
    private record SupplierOption(long id,String name){@Override public String toString(){return name;}}
    private record BookOption(long id,String title,String isbn){@Override public String toString(){return title+(isbn==null?"":" · "+isbn);}}
    private record RequestLine(BookOption book,int quantity,java.math.BigDecimal cost){}
}
