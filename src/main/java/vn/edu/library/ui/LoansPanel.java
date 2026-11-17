package vn.edu.library.ui;

import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class LoansPanel extends JPanel {
    private final UserSession session;
    private final JTextField search = new JTextField(24);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel() { @Override public boolean isCellEditable(int r,int c){return false;} };
    private final JTable table = new JTable(model);

    public LoansPanel(UserSession session) {
        this.session=session; setLayout(new BorderLayout(0,16)); setBackground(UiStyle.CANVAS); setBorder(new EmptyBorder(27,30,24,30));
        add(header(),BorderLayout.NORTH); table.setRowHeight(33); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true);
        JScrollPane scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createLineBorder(new Color(229,233,240)));add(scroll,BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);add(status,BorderLayout.SOUTH);load();
    }
    private JComponent header(){JPanel h=new JPanel(new BorderLayout(0,16));h.setOpaque(false);JPanel t=new JPanel();t.setOpaque(false);t.setLayout(new BoxLayout(t,BoxLayout.Y_AXIS));JLabel title=new JLabel("Mượn – trả sách");title.setFont(title.getFont().deriveFont(Font.BOLD,25f));JLabel sub=new JLabel("Lập phiếu tại quầy, nhận trả sách và gia hạn theo quy định.");sub.setForeground(UiStyle.MUTED);t.add(title);t.add(Box.createVerticalStrut(5));t.add(sub);h.add(t,BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText","Mã phiếu, độc giả, mã vạch hoặc tên sách");JButton find=new JButton("Tìm kiếm"),refresh=new JButton("Làm mới"),borrow=new JButton("Lập phiếu nháp"),confirm=new JButton("Xác nhận giao"),cancel=new JButton("Hủy phiếu nháp"),returnBook=new JButton("Nhận trả"),renew=new JButton("Gia hạn"),print=new JButton("In phiếu mượn");borrow.setBackground(UiStyle.BLUE);borrow.setForeground(Color.WHITE);find.addActionListener(e->load());refresh.addActionListener(e->{search.setText("");load();});search.addActionListener(e->load());borrow.addActionListener(e->createLoan());confirm.addActionListener(e->confirmSelectedDraft());cancel.addActionListener(e->cancelSelectedDraft());returnBook.addActionListener(e->returnCopy());renew.addActionListener(e->renewCopy());print.addActionListener(e->printSelectedLoan());JPanel c=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));c.setOpaque(false);for(JComponent x:new JComponent[]{search,find,refresh,borrow,confirm,cancel,returnBook,renew,print})c.add(x);h.add(c,BorderLayout.SOUTH);return h;}
    private void load(){String term=search.getText().trim();String sql="SELECT l.loan_number AS 'Mã phiếu',m.member_code AS 'Mã độc giả',m.full_name AS 'Độc giả',b.title AS 'Tên sách',cp.barcode AS 'Mã bản sách',li.due_at AS 'Hạn trả',li.returned_at AS 'Ngày trả',li.renew_count AS 'Số lần gia hạn',CASE WHEN l.loan_status='DRAFT' THEN 'Chờ xác nhận giao' WHEN li.returned_at IS NOT NULL THEN 'Đã trả' WHEN li.due_at<CURRENT_DATE() THEN 'Quá hạn' ELSE 'Đang mượn' END AS 'Trạng thái' FROM loan_items li JOIN loans l ON l.loan_id=li.loan_id JOIN school_members m ON m.member_id=l.member_id JOIN book_copies cp ON cp.copy_id=li.copy_id JOIN books b ON b.book_id=cp.book_id WHERE l.loan_status<>'CANCELLED' AND (?='' OR l.loan_number LIKE ? OR m.member_code LIKE ? OR m.full_name LIKE ? OR cp.barcode LIKE ? OR b.title LIKE ?) ORDER BY l.loan_status='DRAFT' DESC,li.returned_at IS NULL DESC,li.due_at";try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement(sql)){String p="%"+term+"%";q.setString(1,term);for(int i=2;i<=6;i++)q.setString(i,p);try(ResultSet rows=q.executeQuery()){ResultSetMetaData md=rows.getMetaData();model.setRowCount(0);model.setColumnCount(0);for(int i=1;i<=md.getColumnCount();i++)model.addColumn(md.getColumnLabel(i));while(rows.next()){Object[] a=new Object[md.getColumnCount()];for(int i=1;i<=a.length;i++)a[i-1]=rows.getObject(i);model.addRow(a);}}status.setText("Đang hiển thị "+model.getRowCount()+" dòng mượn – trả; phiếu nháp cần được xác nhận sau khi giao sách.");}catch(Exception ex){model.setRowCount(0);status.setText("Không tải được giao dịch: "+ex.getMessage());}}
    private void createLoan(){JTextField member=new JTextField(22),card=new JTextField(22),barcodes=new JTextField(22);JPanel p=formPanel("Mã thành viên trường",member,"Số thẻ thư viện đang hoạt động",card,"Mã vạch sách (nhiều mã ngăn cách dấu phẩy)",barcodes);if(JOptionPane.showConfirmDialog(this,p,"Lập phiếu mượn nháp",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;List<String> codes=java.util.Arrays.stream(barcodes.getText().split(",")).map(String::trim).filter(s->!s.isBlank()).distinct().toList();if(codes.isEmpty()){message("Nhập ít nhất một mã vạch sách.",JOptionPane.WARNING_MESSAGE);return;}try{String number=doBorrow(member.getText().trim(),card.getText().trim(),codes);load();message("Đã lập phiếu nháp "+number+". Kiểm tra độc giả và sách, giao sách thực tế rồi chọn phiếu và nhấn Xác nhận giao.",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){message("Không lập được phiếu: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private String doBorrow(String memberCode,String cardNumber,List<String> codes)throws Exception{
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long staff=staffId(c),memberId,cardId;
                try(PreparedStatement q=c.prepareStatement("SELECT member_id FROM school_members WHERE member_code=? AND institution_status='ACTIVE' AND (valid_until IS NULL OR valid_until>=CURRENT_DATE()) FOR UPDATE")){
                    q.setString(1,memberCode);
                    try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Độc giả không tồn tại hoặc không còn đủ điều kiện thuộc trường.");memberId=r.getLong(1);}
                }
                try(PreparedStatement q=c.prepareStatement("SELECT card_id FROM library_cards WHERE card_number=? AND member_id=? AND card_status='ACTIVE' FOR UPDATE")){
                    q.setString(1,cardNumber);q.setLong(2,memberId);
                    try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Thẻ không hợp lệ hoặc chưa được kích hoạt.");cardId=r.getLong(1);}
                }
                int outstanding;
                try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM loan_items li JOIN loans l ON l.loan_id=li.loan_id WHERE l.member_id=? AND l.loan_status IN ('DRAFT','OPEN','PARTIALLY_RETURNED') AND li.returned_at IS NULL")){
                    q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){r.next();outstanding=r.getInt(1);}
                }
                int max=setting(c,"LOAN_MAX_COPIES",5);
                if(outstanding+codes.size()>max)throw new IllegalArgumentException("Độc giả đang có "+outstanding+" bản mượn hoặc chờ giao; giới hạn là "+max+" bản.");
                List<Long> copies=new ArrayList<>();
                for(String code:codes){
                    String sql="SELECT cp.copy_id,cp.copy_status FROM book_copies cp WHERE cp.barcode=? AND (cp.copy_status='AVAILABLE' OR (cp.copy_status='RESERVED' AND EXISTS (SELECT 1 FROM reservations r WHERE r.reserved_copy_id=cp.copy_id AND r.member_id=? AND r.reservation_status='READY_FOR_PICKUP' AND r.expires_at>=CURRENT_TIMESTAMP))) AND NOT EXISTS (SELECT 1 FROM loan_items x JOIN loans lx ON lx.loan_id=x.loan_id WHERE x.copy_id=cp.copy_id AND lx.loan_status='DRAFT') FOR UPDATE";
                    try(PreparedStatement q=c.prepareStatement(sql)){q.setString(1,code);q.setLong(2,memberId);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Mã vạch "+code+" không tồn tại, đã được giữ hoặc sách không sẵn sàng.");copies.add(r.getLong(1));}}
                }
                String loanNo="LN-"+System.currentTimeMillis();long loanId;
                try(PreparedStatement q=c.prepareStatement("INSERT INTO loans(loan_number,member_id,card_id,borrowed_by,loan_status) VALUES(?,?,?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS)){
                    q.setString(1,loanNo);q.setLong(2,memberId);q.setLong(3,cardId);q.setLong(4,staff);q.executeUpdate();
                    try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Không tạo được phiếu mượn.");loanId=r.getLong(1);}
                }
                int days=setting(c,"LOAN_PERIOD_DAYS",21);
                for(long copyId:copies){
                    try(PreparedStatement q=c.prepareStatement("INSERT INTO loan_items(loan_id,copy_id,due_at) VALUES(?,?,?)")){q.setLong(1,loanId);q.setLong(2,copyId);q.setDate(3,Date.valueOf(LocalDate.now().plusDays(days)));q.executeUpdate();}
                    try(PreparedStatement q=c.prepareStatement("UPDATE book_copies SET copy_status='PENDING_LOAN' WHERE copy_id=? AND copy_status='AVAILABLE'")){q.setLong(1,copyId);q.executeUpdate();}
                }
                c.commit();return loanNo;
            }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }
    }
    private void confirmSelectedDraft(){String number=selectedLoanNumber();if(number==null)return;if(JOptionPane.showConfirmDialog(this,"Chỉ xác nhận sau khi đã đối chiếu thẻ và thực sự bàn giao đủ sách cho độc giả. Xác nhận giao phiếu "+number+"?","Xác nhận bàn giao",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE)!=JOptionPane.YES_OPTION)return;try{confirmDraft(number);load();message("Đã xác nhận bàn giao sách. Hạn trả được tính từ ngày hôm nay; có thể in phiếu mượn.",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){message("Không xác nhận được phiếu: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private void confirmDraft(String number)throws Exception{
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long loanId,memberId,cardId;
                try(PreparedStatement q=c.prepareStatement("SELECT loan_id,member_id,card_id,loan_status FROM loans WHERE loan_number=? FOR UPDATE")){
                    q.setString(1,number);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy phiếu.");loanId=r.getLong(1);memberId=r.getLong(2);cardId=r.getLong(3);if(!"DRAFT".equals(r.getString(4)))throw new IllegalArgumentException("Phiếu không còn ở trạng thái chờ xác nhận.");}
                }
                try(PreparedStatement q=c.prepareStatement("SELECT member_id FROM school_members WHERE member_id=? AND institution_status='ACTIVE' AND (valid_until IS NULL OR valid_until>=CURRENT_DATE()) FOR UPDATE")){
                    q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Hồ sơ thành viên đã hết hiệu lực; không thể giao sách.");}
                }
                try(PreparedStatement q=c.prepareStatement("SELECT card_id FROM library_cards WHERE card_id=? AND member_id=? AND card_status='ACTIVE' FOR UPDATE")){
                    q.setLong(1,cardId);q.setLong(2,memberId);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Thẻ đã bị khóa hoặc hết hiệu lực; không thể giao sách.");}
                }
                int outstanding;
                try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM loan_items li JOIN loans l ON l.loan_id=li.loan_id WHERE l.member_id=? AND l.loan_status IN ('DRAFT','OPEN','PARTIALLY_RETURNED') AND li.returned_at IS NULL")){
                    q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){r.next();outstanding=r.getInt(1);}
                }
                int max=setting(c,"LOAN_MAX_COPIES",5);if(outstanding>max)throw new IllegalArgumentException("Số bản đang mượn/chờ giao vượt giới hạn "+max+" cuốn.");
                List<long[]> copies=new ArrayList<>();List<String> states=new ArrayList<>();
                try(PreparedStatement q=c.prepareStatement("SELECT li.copy_id,cp.copy_status FROM loan_items li JOIN book_copies cp ON cp.copy_id=li.copy_id WHERE li.loan_id=? AND li.returned_at IS NULL FOR UPDATE")){
                    q.setLong(1,loanId);try(ResultSet r=q.executeQuery()){while(r.next()){copies.add(new long[]{r.getLong(1)});states.add(r.getString(2));}}
                }
                if(copies.isEmpty())throw new IllegalArgumentException("Phiếu không có bản sách để giao.");
                int days=setting(c,"LOAN_PERIOD_DAYS",21);
                for(int i=0;i<copies.size();i++){
                    long copyId=copies.get(i)[0];String state=states.get(i);
                    if("PENDING_LOAN".equals(state)){
                        try(PreparedStatement q=c.prepareStatement("UPDATE book_copies SET copy_status='ON_LOAN' WHERE copy_id=? AND copy_status='PENDING_LOAN'")){q.setLong(1,copyId);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Bản sách vừa thay đổi trạng thái.");}
                    }else if("RESERVED".equals(state)){
                        try(PreparedStatement q=c.prepareStatement("UPDATE reservations SET reservation_status='FULFILLED' WHERE reserved_copy_id=? AND member_id=? AND reservation_status='READY_FOR_PICKUP' AND expires_at>=CURRENT_TIMESTAMP")){q.setLong(1,copyId);q.setLong(2,memberId);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Yêu cầu giữ sách đã hết hạn hoặc thuộc độc giả khác.");}
                        try(PreparedStatement q=c.prepareStatement("UPDATE book_copies SET copy_status='ON_LOAN' WHERE copy_id=? AND copy_status='RESERVED'")){q.setLong(1,copyId);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Bản sách giữ trước vừa thay đổi trạng thái.");}
                    }else throw new IllegalArgumentException("Bản sách không còn sẵn sàng để bàn giao.");
                }
                try(PreparedStatement q=c.prepareStatement("UPDATE loan_items SET due_at=DATE_ADD(CURRENT_DATE,INTERVAL ? DAY) WHERE loan_id=? AND returned_at IS NULL")){q.setInt(1,days);q.setLong(2,loanId);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("UPDATE loans SET loan_status='OPEN',borrowed_at=CURRENT_TIMESTAMP WHERE loan_id=? AND loan_status='DRAFT'")){q.setLong(1,loanId);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Phiếu vừa được xử lý ở nơi khác.");}
                c.commit();
            }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }
    }
    private void cancelSelectedDraft(){String number=selectedLoanNumber();if(number==null)return;if(JOptionPane.showConfirmDialog(this,"Hủy phiếu nháp "+number+" và trả bản sách về trạng thái sẵn sàng?","Hủy phiếu nháp",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long loanId;try(PreparedStatement q=c.prepareStatement("SELECT loan_id FROM loans WHERE loan_number=? AND loan_status='DRAFT' FOR UPDATE")){q.setString(1,number);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Phiếu không còn chờ xác nhận.");loanId=r.getLong(1);}}try(PreparedStatement q=c.prepareStatement("UPDATE book_copies cp JOIN loan_items li ON li.copy_id=cp.copy_id SET cp.copy_status='AVAILABLE' WHERE li.loan_id=? AND cp.copy_status='PENDING_LOAN'")){q.setLong(1,loanId);q.executeUpdate();}try(PreparedStatement q=c.prepareStatement("UPDATE loans SET loan_status='CANCELLED' WHERE loan_id=? AND loan_status='DRAFT'")){q.setLong(1,loanId);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Phiếu vừa được xử lý ở nơi khác.");}c.commit();load();}catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}}catch(Exception ex){message("Không hủy được phiếu nháp: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private String selectedLoanNumber(){int view=table.getSelectedRow();if(view<0){message("Chọn một dòng thuộc phiếu cần xử lý.",JOptionPane.INFORMATION_MESSAGE);return null;}int row=table.convertRowIndexToModel(view);return model.getValueAt(row,0).toString();}
    private void returnCopy(){String barcode=JOptionPane.showInputDialog(this,"Nhập mã vạch sách cần nhận trả:","Nhận trả sách",JOptionPane.PLAIN_MESSAGE);if(barcode==null||barcode.isBlank())return;String[] conditions={"GOOD","WORN","DAMAGED","LOST"};String condition=(String)JOptionPane.showInputDialog(this,"Tình trạng sách (hao mòn thông thường không tính bồi thường)","Ghi nhận trả sách",JOptionPane.PLAIN_MESSAGE,null,conditions,conditions[0]);if(condition==null)return;try{String note="";if(!"GOOD".equals(condition)){note=JOptionPane.showInputDialog(this,"Ghi chú tình trạng sách:","Ghi nhận vi phạm",JOptionPane.PLAIN_MESSAGE);if(note==null)return;}String result=doReturn(barcode.trim(),condition,note);load();message(result,JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){message("Không nhận trả được: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private String doReturn(String barcode,String condition,String note)throws Exception{try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long staff=staffId(c);long itemId,copyId;Date due;BigDecimal price;try(PreparedStatement q=c.prepareStatement("SELECT li.loan_item_id,li.copy_id,li.due_at,b.price FROM loan_items li JOIN book_copies cp ON cp.copy_id=li.copy_id JOIN books b ON b.book_id=cp.book_id WHERE cp.barcode=? AND li.returned_at IS NULL FOR UPDATE")){q.setString(1,barcode);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không có khoản mượn đang mở cho mã vạch này.");itemId=r.getLong(1);copyId=r.getLong(2);due=r.getDate(3);price=r.getBigDecimal(4);}}Timestamp now=Timestamp.valueOf(java.time.LocalDateTime.now());try(PreparedStatement q=c.prepareStatement("UPDATE loan_items SET returned_at=?,item_condition_on_return=?,return_processed_by=? WHERE loan_item_id=?")){q.setTimestamp(1,now);q.setString(2,condition);q.setLong(3,staff);q.setLong(4,itemId);q.executeUpdate();}String copyStatus="GOOD".equals(condition)||"WORN".equals(condition)?"AVAILABLE":condition;try(PreparedStatement q=c.prepareStatement("UPDATE book_copies SET copy_status=? WHERE copy_id=?")){q.setString(1,copyStatus);q.setLong(2,copyId);q.executeUpdate();}StringBuilder result=new StringBuilder("Đã nhận trả sách.");long lateDays=Math.max(0,LocalDate.now().toEpochDay()-due.toLocalDate().toEpochDay());if(lateDays>0){BigDecimal fine=BigDecimal.valueOf(lateDays).multiply(BigDecimal.valueOf(setting(c,"OVERDUE_FINE_PER_DAY",5000)));insertViolation(c,itemId,"OVERDUE",fine,"Trễ "+lateDays+" ngày",staff);result.append(" Phạt quá hạn: ").append(fine.toPlainString()).append(" đồng.");}if("DAMAGED".equals(condition)||"LOST".equals(condition)){BigDecimal rate=new BigDecimal("DAMAGED".equals(condition)?settingString(c,"DAMAGED_BOOK_REPLACEMENT_RATE","0.75"):settingString(c,"LOST_BOOK_REPLACEMENT_RATE","1.00"));BigDecimal compensation=price.multiply(rate).setScale(0,RoundingMode.HALF_UP);insertViolation(c,itemId,condition,compensation,note,staff);result.append(" Bồi thường: ").append(compensation.toPlainString()).append(" đồng.");}try(PreparedStatement q=c.prepareStatement("UPDATE loans l SET loan_status=CASE WHEN EXISTS(SELECT 1 FROM loan_items x WHERE x.loan_id=l.loan_id AND x.returned_at IS NULL) THEN 'PARTIALLY_RETURNED' ELSE 'CLOSED' END WHERE l.loan_id=(SELECT loan_id FROM loan_items WHERE loan_item_id=?)")){q.setLong(1,itemId);q.executeUpdate();}c.commit();return result.toString();}catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}}}
    private void renewCopy(){String barcode=JOptionPane.showInputDialog(this,"Nhập mã vạch sách cần gia hạn:","Gia hạn mượn",JOptionPane.PLAIN_MESSAGE);if(barcode==null||barcode.isBlank())return;try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long copyId;Date due;int renew;long bookId;try(PreparedStatement q=c.prepareStatement("SELECT li.copy_id,li.due_at,li.renew_count,cp.book_id FROM loan_items li JOIN book_copies cp ON cp.copy_id=li.copy_id WHERE cp.barcode=? AND li.returned_at IS NULL FOR UPDATE")){q.setString(1,barcode.trim());try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy khoản mượn còn mở.");copyId=r.getLong(1);due=r.getDate(2);renew=r.getInt(3);bookId=r.getLong(4);}}if(renew>=1)throw new IllegalArgumentException("Sách đã được gia hạn một lần.");if(due.toLocalDate().isBefore(LocalDate.now()))throw new IllegalArgumentException("Sách quá hạn không thể gia hạn.");try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM reservations WHERE book_id=? AND reservation_status IN ('PENDING','APPROVED','READY_FOR_PICKUP')")){q.setLong(1,bookId);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("Có độc giả đang chờ sách này, không thể gia hạn.");}}int days=setting(c,"LOAN_RENEWAL_MAX_DAYS",7);try(PreparedStatement q=c.prepareStatement("UPDATE loan_items SET due_at=DATE_ADD(due_at,INTERVAL ? DAY),renew_count=renew_count+1 WHERE copy_id=? AND returned_at IS NULL")){q.setInt(1,days);q.setLong(2,copyId);q.executeUpdate();}c.commit();load();message("Đã gia hạn thêm tối đa "+days+" ngày.",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){c.rollback();message("Không gia hạn được: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}finally{c.setAutoCommit(true);}}catch(Exception ex){message("Lỗi cơ sở dữ liệu: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private void printSelectedLoan(){
        int view=table.getSelectedRow();if(view<0){message("Chọn một dòng thuộc phiếu mượn cần in.",JOptionPane.INFORMATION_MESSAGE);return;}
        int row=table.convertRowIndexToModel(view);String loanNumber=model.getValueAt(row,0).toString();
        try{
            StringBuilder slip=new StringBuilder("PHIẾU MƯỢN SÁCH\nTHƯ VIỆN TRƯỜNG ĐẠI HỌC\n\n");
            try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement("SELECT l.loan_number,m.member_code,m.full_name,l.borrowed_at,b.title,cp.barcode,li.due_at FROM loans l JOIN school_members m ON m.member_id=l.member_id JOIN loan_items li ON li.loan_id=l.loan_id JOIN book_copies cp ON cp.copy_id=li.copy_id JOIN books b ON b.book_id=cp.book_id WHERE l.loan_number=? ORDER BY b.title")){
                q.setString(1,loanNumber);
                try(ResultSet r=q.executeQuery()){
                    if(!r.next())throw new IllegalArgumentException("Không tìm thấy phiếu mượn.");
                    slip.append("Mã phiếu: ").append(r.getString(1)).append("\nĐộc giả: ").append(r.getString(3)).append(" (").append(r.getString(2)).append(")\nNgày mượn: ").append(r.getTimestamp(4)).append("\n\nSách đã giao:\n");
                    do{slip.append("- ").append(r.getString(5)).append(" | Mã vạch: ").append(r.getString(6)).append(" | Hạn trả: ").append(r.getDate(7)).append("\n");}while(r.next());
                }
            }
            slip.append("\nĐộc giả xác nhận đã nhận đủ sách.\n\nChữ ký độc giả: ____________________\n\nThủ thư: ____________________\n");
            JTextArea receipt=new JTextArea(slip.toString(),20,56);receipt.setEditable(false);receipt.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
            if(JOptionPane.showConfirmDialog(this,new JScrollPane(receipt),"In phiếu mượn "+loanNumber,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)receipt.print();
        }catch(Exception ex){message("Không tạo/in được phiếu mượn: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}
    }
    private long staffId(Connection c)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT staff_id FROM accounts WHERE account_id=? AND staff_id IS NOT NULL")){q.setLong(1,session.accountId());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Tài khoản hiện tại chưa liên kết hồ sơ nhân viên.");return r.getLong(1);}}}
    private int setting(Connection c,String key,int fallback)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key=?")){q.setString(1,key);try(ResultSet r=q.executeQuery()){return r.next()?Integer.parseInt(r.getString(1)):fallback;}}}
    private String settingString(Connection c,String key,String fallback)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key=?")){q.setString(1,key);try(ResultSet r=q.executeQuery()){return r.next()?r.getString(1):fallback;}}}
    private void insertViolation(Connection c,long itemId,String type,BigDecimal amount,String description,long staff)throws SQLException{try(PreparedStatement q=c.prepareStatement("INSERT INTO violations(loan_item_id,violation_type,assessed_amount,description,assessed_by) VALUES(?,?,?,?,?)")){q.setLong(1,itemId);q.setString(2,type);q.setBigDecimal(3,amount);q.setString(4,description);q.setLong(5,staff);q.executeUpdate();}}
    private JPanel formPanel(Object... pairs){JPanel p=new JPanel(new GridBagLayout());for(int i=0;i<pairs.length/2;i++){GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(5,5,5,5);c.gridy=i;c.gridx=0;c.anchor=GridBagConstraints.WEST;p.add(new JLabel((String)pairs[i*2]),c);c.gridx=1;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;p.add((JComponent)pairs[i*2+1],c);}return p;}
    private void message(String text,int type){JOptionPane.showMessageDialog(this,text,"Mượn – trả sách",type);}
}
