package vn.edu.library.ui;

import vn.edu.library.data.Database;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;

public final class BooksPanel extends JPanel {
    private final boolean canManage;
    private final boolean canManageCatalog;
    private final JTextField search = new JTextField(27);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public BooksPanel(boolean canManage, boolean canManageCatalog) {
        this.canManage = canManage;
        this.canManageCatalog = canManageCatalog;
        setLayout(new BorderLayout(0, 16)); setBackground(UiStyle.CANVAS); setBorder(new EmptyBorder(27, 30, 24, 30));
        add(buildHeader(), BorderLayout.NORTH);
        table.setRowHeight(33); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table); scroll.setBorder(BorderFactory.createLineBorder(new Color(229, 233, 240)));
        add(scroll, BorderLayout.CENTER); status.setForeground(UiStyle.MUTED); add(status, BorderLayout.SOUTH); load();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 16)); header.setOpaque(false);
        JPanel titles = new JPanel(); titles.setOpaque(false); titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Sách in"); title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Quản lý đầu sách, tác giả, thể loại, nhà xuất bản và bản sách trong kho."); subtitle.setForeground(UiStyle.MUTED);
        titles.add(title); titles.add(Box.createVerticalStrut(5)); titles.add(subtitle); header.add(titles, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "ISBN, tên sách, tác giả, thể loại");
        JButton find = new JButton("Tìm kiếm"), refresh = new JButton("Làm mới"), add = new JButton("Thêm đầu sách"), edit = new JButton("Sửa"), archive = new JButton("Lưu trữ"), delete = new JButton("Xóa đầu sách"), copyState = new JButton("Cập nhật tình trạng bản"), catalog = new JButton("Quản lý thể loại / tác giả / NXB");
        add.setBackground(UiStyle.BLUE); add.setForeground(Color.WHITE);
        find.addActionListener(e -> load()); refresh.addActionListener(e -> { search.setText(""); load(); }); search.addActionListener(e -> load());
        add.addActionListener(e -> editBook(null)); edit.addActionListener(e -> editSelected()); archive.addActionListener(e -> toggleArchive()); delete.addActionListener(e -> deleteSelected());
        copyState.addActionListener(e -> changeCopyState());
        catalog.addActionListener(e -> { new CatalogManagerDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true); load(); });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0)); controls.setOpaque(false);
        controls.add(search); controls.add(find); controls.add(refresh);
        if (canManage) { controls.add(add); controls.add(edit); controls.add(archive); controls.add(delete); controls.add(copyState); }
        if (canManageCatalog) controls.add(catalog);
        header.add(controls, BorderLayout.SOUTH); return header;
    }

    private void load() {
        String term = search.getText().trim();
        String sql = "SELECT b.book_id AS 'ID', b.isbn AS 'ISBN', b.title AS 'Tên sách', "
                + "GROUP_CONCAT(DISTINCT a.author_name ORDER BY a.author_name SEPARATOR ', ') AS 'Tác giả', "
                + "c.category_name AS 'Thể loại', p.publisher_name AS 'Nhà xuất bản', b.publication_year AS 'Năm XB', "
                + "b.price AS 'Giá mới (VND)', COALESCE(SUM(CASE WHEN cp.copy_status='AVAILABLE' THEN 1 ELSE 0 END),0) AS 'Có sẵn', "
                + "COALESCE(SUM(CASE WHEN cp.copy_status='PENDING_LOAN' THEN 1 ELSE 0 END),0) AS 'Chờ giao', "
                + "COALESCE(SUM(CASE WHEN cp.copy_status='ON_LOAN' THEN 1 ELSE 0 END),0) AS 'Đang mượn', "
                + "COALESCE(SUM(CASE WHEN cp.copy_status='RESERVED' THEN 1 ELSE 0 END),0) AS 'Đang giữ', "
                + "COALESCE(SUM(CASE WHEN cp.copy_status='DAMAGED' THEN 1 ELSE 0 END),0) AS 'Hư hỏng', "
                + "COALESCE(SUM(CASE WHEN cp.copy_status='LOST' THEN 1 ELSE 0 END),0) AS 'Mất', b.record_status AS 'Trạng thái' "
                + "FROM books b LEFT JOIN book_authors ba ON ba.book_id=b.book_id LEFT JOIN authors a ON a.author_id=ba.author_id "
                + "LEFT JOIN categories c ON c.category_id=b.category_id LEFT JOIN publishers p ON p.publisher_id=b.publisher_id "
                + "LEFT JOIN book_copies cp ON cp.book_id=b.book_id "
                + "WHERE (?='' OR b.isbn LIKE ? OR b.title LIKE ? OR a.author_name LIKE ? OR c.category_name LIKE ? OR p.publisher_name LIKE ?) "
                + "GROUP BY b.book_id ORDER BY b.title";
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            String pattern = "%" + term + "%"; query.setString(1, term);
            for (int i = 2; i <= 6; i++) query.setString(i, pattern);
            try (ResultSet rows = query.executeQuery()) {
                ResultSetMetaData meta = rows.getMetaData(); model.setRowCount(0); model.setColumnCount(0);
                for (int i = 1; i <= meta.getColumnCount(); i++) model.addColumn(meta.getColumnLabel(i));
                while (rows.next()) { Object[] values = new Object[meta.getColumnCount()]; for (int i=1;i<=meta.getColumnCount();i++) values[i-1]=rows.getObject(i); model.addRow(values); }
            }
            status.setText("Đang hiển thị " + model.getRowCount() + " đầu sách. Bản sách là các mã vạch riêng trong kho.");
        } catch (Exception ex) { model.setRowCount(0); status.setText("Không tải được danh mục sách: " + ex.getMessage()); }
    }

    private void editSelected() {
        int view = table.getSelectedRow(); if (view < 0) { message("Chọn một đầu sách trước.", JOptionPane.INFORMATION_MESSAGE); return; }
        int row = table.convertRowIndexToModel(view); long id = Long.parseLong(model.getValueAt(row, 0).toString());
        try (Connection connection = Database.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT b.*,c.category_name,p.publisher_name,GROUP_CONCAT(a.author_name SEPARATOR ', ') author_names FROM books b "
                        + "LEFT JOIN categories c ON c.category_id=b.category_id LEFT JOIN publishers p ON p.publisher_id=b.publisher_id "
                        + "LEFT JOIN book_authors ba ON ba.book_id=b.book_id LEFT JOIN authors a ON a.author_id=ba.author_id WHERE b.book_id=? GROUP BY b.book_id")) {
            query.setLong(1, id); try (ResultSet rs=query.executeQuery()) { if(rs.next()) editBook(new BookForm(rs)); }
        } catch (Exception ex) { message("Không mở được đầu sách: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private void editBook(BookForm existing) {
        boolean isNew = existing == null; BookForm form = isNew ? new BookForm() : existing;
        int result = JOptionPane.showConfirmDialog(this, form.panel, isNew ? "Thêm đầu sách" : "Cập nhật đầu sách", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try { save(form, isNew); load(); message("Đã lưu đầu sách.", JOptionPane.INFORMATION_MESSAGE); }
        catch (Exception ex) { message("Không lưu được đầu sách: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private void save(BookForm form, boolean isNew) throws Exception {
        String title = form.title.getText().trim(); if (title.isBlank()) throw new IllegalArgumentException("Tên sách là bắt buộc.");
        BigDecimal price; try { price = new BigDecimal(form.price.getText().trim()); if(price.signum()<0) throw new NumberFormatException(); }
        catch (Exception ex) { throw new IllegalArgumentException("Giá sách phải là số không âm."); }
        Integer year = null; if (!form.year.getText().trim().isBlank()) { try { year=Integer.parseInt(form.year.getText().trim()); if(year<0||year>2200) throw new NumberFormatException(); } catch(Exception ex){throw new IllegalArgumentException("Năm xuất bản không hợp lệ.");} }
        try (Connection connection=Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long categoryId = upsertLookup(connection,"categories","category_name",form.category.getText());
                Long publisherId = upsertLookup(connection,"publishers","publisher_name",form.publisher.getText());
                long bookId;
                if (isNew) {
                    try (PreparedStatement insert=connection.prepareStatement("INSERT INTO books(isbn,title,category_id,publisher_id,publication_year,price,description) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        bindBook(insert,form,categoryId,publisherId,year,price,1); insert.executeUpdate();
                        try(ResultSet keys=insert.getGeneratedKeys()){keys.next();bookId=keys.getLong(1);}
                    }
                } else {
                    bookId=form.bookId;
                    try(PreparedStatement update=connection.prepareStatement("UPDATE books SET isbn=?,title=?,category_id=?,publisher_id=?,publication_year=?,price=?,description=? WHERE book_id=?")) {
                        bindBook(update,form,categoryId,publisherId,year,price,1); update.setLong(8,bookId); update.executeUpdate();
                    }
                }
                try(PreparedStatement delete=connection.prepareStatement("DELETE FROM book_authors WHERE book_id=?")){delete.setLong(1,bookId);delete.executeUpdate();}
                String[] names=form.authors.getText().split(",");
                for(String raw:names){String author=raw.trim();if(author.isBlank())continue;long authorId;
                    try(PreparedStatement insert=connection.prepareStatement("INSERT INTO authors(author_name) VALUES(?) ON DUPLICATE KEY UPDATE author_id=LAST_INSERT_ID(author_id)" ,Statement.RETURN_GENERATED_KEYS)){insert.setString(1,author);insert.executeUpdate();try(ResultSet keys=insert.getGeneratedKeys()){keys.next();authorId=keys.getLong(1);}}
                    try(PreparedStatement link=connection.prepareStatement("INSERT INTO book_authors(book_id,author_id) VALUES(?,?)")){link.setLong(1,bookId);link.setLong(2,authorId);link.executeUpdate();}
                }
                connection.commit();
            } catch(Exception ex){connection.rollback();throw ex;} finally {connection.setAutoCommit(true);}
        }
    }

    private Long upsertLookup(Connection connection,String table,String column,String raw) throws SQLException {
        String value=raw.trim(); if(value.isBlank()) return null;
        try(PreparedStatement insert=connection.prepareStatement("INSERT INTO "+table+"("+column+") VALUES(?) ON DUPLICATE KEY UPDATE "+column+"=VALUES("+column+")")){insert.setString(1,value);insert.executeUpdate();}
        try(PreparedStatement query=connection.prepareStatement("SELECT "+(table.equals("categories")?"category_id":"publisher_id")+" FROM "+table+" WHERE "+column+"=?")){query.setString(1,value);try(ResultSet rs=query.executeQuery()){rs.next();return rs.getLong(1);}}
    }
    private void bindBook(PreparedStatement statement,BookForm form,Long categoryId,Long publisherId,Integer year,BigDecimal price,int offset)throws SQLException{
        statement.setString(1,emptyToNull(form.isbn.getText()));statement.setString(2,form.title.getText().trim());
        if(categoryId==null)statement.setNull(3,Types.BIGINT);else statement.setLong(3,categoryId);
        if(publisherId==null)statement.setNull(4,Types.BIGINT);else statement.setLong(4,publisherId);
        if(year==null)statement.setNull(5,Types.SMALLINT);else statement.setInt(5,year);
        statement.setBigDecimal(6,price);statement.setString(7,emptyToNull(form.description.getText()));
    }
    private void toggleArchive() {
        int view=table.getSelectedRow();if(view<0){message("Chọn một đầu sách trước.",JOptionPane.INFORMATION_MESSAGE);return;}
        int row=table.convertRowIndexToModel(view);long id=Long.parseLong(model.getValueAt(row,0).toString());
        String title=model.getValueAt(row,2).toString(),current=model.getValueAt(row,14).toString();
        String next="ACTIVE".equals(current)?"ARCHIVED":"ACTIVE";
        if(JOptionPane.showConfirmDialog(this,"Chuyển '"+title+"' thành "+next+"?","Xác nhận",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                if("ARCHIVED".equals(next)){
                    int open=count(c,"SELECT COUNT(*) FROM loan_items li JOIN book_copies cp ON cp.copy_id=li.copy_id WHERE cp.book_id=? AND li.returned_at IS NULL",id);
                    int waiting=count(c,"SELECT COUNT(*) FROM reservations WHERE book_id=? AND reservation_status IN ('PENDING','APPROVED','READY_FOR_PICKUP')",id);
                    if(open>0)throw new IllegalArgumentException("Sách đang được mượn, không thể xóa/lưu trữ.");
                    if(waiting>0)throw new IllegalArgumentException("Sách còn yêu cầu đặt trước đang xử lý.");
                }
                try(PreparedStatement q=c.prepareStatement("UPDATE books SET record_status=? WHERE book_id=?")){q.setString(1,next);q.setLong(2,id);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Đầu sách không còn tồn tại.");}
                c.commit();load();
            }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }catch(Exception ex){message("Không đổi được trạng thái: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}
    }

    private void deleteSelected() {
        int view=table.getSelectedRow();if(view<0){message("Chọn một đầu sách trước.",JOptionPane.INFORMATION_MESSAGE);return;}
        int row=table.convertRowIndexToModel(view);long id=Long.parseLong(model.getValueAt(row,0).toString());
        String title=model.getValueAt(row,2).toString();
        if(JOptionPane.showConfirmDialog(this,"Chỉ xóa được đầu sách chưa có lượt mượn, đặt trước hoặc phiếu nhập. Xóa '"+title+"'?","Xác nhận xóa",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                if(count(c,"SELECT COUNT(*) FROM loan_items li JOIN book_copies cp ON cp.copy_id=li.copy_id WHERE cp.book_id=?",id)>0)
                    throw new IllegalArgumentException("Đầu sách có lịch sử mượn hoặc đang được mượn; hãy lưu trữ để giữ lịch sử.");
                if(count(c,"SELECT COUNT(*) FROM reservations WHERE book_id=?",id)>0)
                    throw new IllegalArgumentException("Đầu sách có lịch sử đặt trước; hãy lưu trữ để giữ lịch sử.");
                if(count(c,"SELECT COUNT(*) FROM stock_receipt_lines WHERE book_id=?",id)>0)
                    throw new IllegalArgumentException("Đầu sách có lịch sử nhập kho; hãy lưu trữ để giữ lịch sử.");
                try(PreparedStatement q=c.prepareStatement("DELETE FROM book_authors WHERE book_id=?")){q.setLong(1,id);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("DELETE FROM book_copies WHERE book_id=?")){q.setLong(1,id);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("DELETE FROM books WHERE book_id=?")){q.setLong(1,id);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Đầu sách không còn tồn tại.");}
                c.commit();load();message("Đã xóa đầu sách.",JOptionPane.INFORMATION_MESSAGE);
            }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }catch(Exception ex){message("Không xóa được đầu sách: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}
    }

    private void changeCopyState() {
        int view = table.getSelectedRow();
        if (view < 0) { message("Chọn đầu sách trước.", JOptionPane.INFORMATION_MESSAGE); return; }
        int row = table.convertRowIndexToModel(view);
        long bookId = Long.parseLong(model.getValueAt(row, 0).toString());
        String title = model.getValueAt(row, 2).toString();
        DefaultTableModel copies = new DefaultTableModel(new String[]{"ID", "Mã vạch", "Tình trạng", "Ghi chú"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT copy_id,barcode,copy_status,notes FROM book_copies WHERE book_id=? ORDER BY barcode")) {
            q.setLong(1, bookId);
            try (ResultSet rs = q.executeQuery()) { while (rs.next()) copies.addRow(new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)}); }
        } catch (Exception ex) { message("Không tải được bản sách: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); return; }
        if (copies.getRowCount() == 0) { message("Đầu sách chưa có bản sách trong kho.", JOptionPane.INFORMATION_MESSAGE); return; }
        JTable copyTable = new JTable(copies);
        copyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        copyTable.setRowHeight(26);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Tình trạng đang mượn/đang giữ phải được xử lý qua nghiệp vụ mượn – trả hoặc đặt trước."), BorderLayout.NORTH);
        panel.add(new JScrollPane(copyTable), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(690, 320));
        if (JOptionPane.showConfirmDialog(this, panel, "Bản sách · " + title, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        int selected = copyTable.getSelectedRow();
        if (selected < 0) { message("Chọn một mã vạch.", JOptionPane.WARNING_MESSAGE); return; }
        selected = copyTable.convertRowIndexToModel(selected);
        long copyId = Long.parseLong(copies.getValueAt(selected, 0).toString());
        String oldState = copies.getValueAt(selected, 2).toString();
        if ("ON_LOAN".equals(oldState) || "RESERVED".equals(oldState) || "PENDING_LOAN".equals(oldState)) { message("Không thể đổi tình trạng khi bản sách đang mượn, chờ giao hoặc đặt giữ.", JOptionPane.WARNING_MESSAGE); return; }
        String[] options = {"AVAILABLE", "DAMAGED", "LOST", "WITHDRAWN"};
        String next = (String) JOptionPane.showInputDialog(this, "Chọn tình trạng mới:", "Cập nhật tình trạng bản sách",
                JOptionPane.PLAIN_MESSAGE, null, options, oldState);
        if (next == null || next.equals(oldState)) return;
        String note = "AVAILABLE".equals(next) ? null : JOptionPane.showInputDialog(this, "Ghi lý do / tình trạng:", "Ghi nhận hiện trạng", JOptionPane.PLAIN_MESSAGE);
        if (!"AVAILABLE".equals(next) && (note == null || note.isBlank())) { message("Cần ghi lý do cho bản hỏng, mất hoặc thanh lý.", JOptionPane.WARNING_MESSAGE); return; }
        try (Connection c = Database.getConnection(); PreparedStatement q = c.prepareStatement(
                "UPDATE book_copies SET copy_status=?,notes=? WHERE copy_id=? AND copy_status=? AND copy_status NOT IN ('ON_LOAN','RESERVED')")) {
            q.setString(1, next); q.setString(2, note == null ? null : note.trim()); q.setLong(3, copyId); q.setString(4, oldState);
            if (q.executeUpdate() != 1) throw new IllegalArgumentException("Bản sách vừa được xử lý ở nơi khác; tải lại danh sách.");
            load();
        } catch (Exception ex) { message("Không cập nhật được tình trạng: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE); }
    }

    private int count(Connection c,String sql,long id)throws SQLException{try(PreparedStatement q=c.prepareStatement(sql)){q.setLong(1,id);try(ResultSet r=q.executeQuery()){r.next();return r.getInt(1);}}}
    private String emptyToNull(String value){String clean=value==null?"":value.trim();return clean.isEmpty()?null:clean;}
    private void message(String text,int type){JOptionPane.showMessageDialog(this,text,"Sách in",type);}

    private final class BookForm {
        final JPanel panel=new JPanel(new GridBagLayout());final JTextField isbn=new JTextField(22),title=new JTextField(22),authors=new JTextField(22),category=new JTextField(22),publisher=new JTextField(22),year=new JTextField(22),price=new JTextField("0",22);final JTextArea description=new JTextArea(4,22);long bookId;
        BookForm(){build();}
        BookForm(ResultSet rs)throws SQLException{bookId=rs.getLong("book_id");isbn.setText(rs.getString("isbn"));title.setText(rs.getString("title"));authors.setText(rs.getString("author_names"));category.setText(rs.getString("category_name"));publisher.setText(rs.getString("publisher_name"));year.setText(rs.getObject("publication_year")==null?"":rs.getString("publication_year"));price.setText(rs.getString("price"));description.setText(rs.getString("description"));build();}
        void build(){panel.setBorder(new EmptyBorder(8,4,8,4));int y=0;y=row(y,"ISBN",isbn);y=row(y,"Tên sách *",title);y=row(y,"Tác giả (phân cách bằng dấu phẩy)",authors);y=row(y,"Thể loại",category);y=row(y,"Nhà xuất bản",publisher);y=row(y,"Năm xuất bản",year);y=row(y,"Giá sách mới (VND)",price);row(y,"Mô tả",new JScrollPane(description));}
        int row(int y,String label,JComponent input){GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(4,5,4,5);c.gridy=y;c.anchor=GridBagConstraints.WEST;c.gridx=0;panel.add(new JLabel(label),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(input,c);return y+1;}
    }
}
