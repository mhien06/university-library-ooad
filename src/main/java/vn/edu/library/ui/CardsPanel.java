package vn.edu.library.ui;

import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public final class CardsPanel extends JPanel {
    private final UserSession session;
    private final JTextField search=new JTextField(22);
    private final JLabel status=new JLabel(" ");
    private final DefaultTableModel model=new DefaultTableModel(){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    public CardsPanel(UserSession session){this.session=session;setLayout(new BorderLayout(0,16));setBackground(UiStyle.CANVAS);setBorder(new EmptyBorder(27,30,24,30));add(header(),BorderLayout.NORTH);table.setRowHeight(33);table.setAutoCreateRowSorter(true);table.setFillsViewportHeight(true);JScrollPane sc=new JScrollPane(table);sc.setBorder(BorderFactory.createLineBorder(new Color(229,233,240)));add(sc,BorderLayout.CENTER);status.setForeground(UiStyle.MUTED);add(status,BorderLayout.SOUTH);load();}
    private JComponent header(){JPanel h=new JPanel(new BorderLayout(0,16));h.setOpaque(false);JPanel t=new JPanel();t.setOpaque(false);t.setLayout(new BoxLayout(t,BoxLayout.Y_AXIS));JLabel title=new JLabel("Thẻ thư viện");title.setFont(title.getFont().deriveFont(Font.BOLD,25f));JLabel sub=new JLabel("Đăng ký, thu phí, giao thẻ trực tiếp và quản lý trạng thái thẻ.");sub.setForeground(UiStyle.MUTED);t.add(title);t.add(Box.createVerticalStrut(5));t.add(sub);h.add(t,BorderLayout.NORTH);search.putClientProperty("JTextField.placeholderText","Số thẻ, mã trường, tên độc giả");JButton find=new JButton("Tìm kiếm"),refresh=new JButton("Làm mới"),request=new JButton("Đăng ký thẻ"),pay=new JButton("Thu phí"),handover=new JButton("Xác nhận giao thẻ"),toggle=new JButton("Khóa / mở khóa");request.setBackground(UiStyle.BLUE);request.setForeground(Color.WHITE);find.addActionListener(e->load());refresh.addActionListener(e->{search.setText("");load();});search.addActionListener(e->load());request.addActionListener(e->requestCard());pay.addActionListener(e->collectFee());handover.addActionListener(e->handover());toggle.addActionListener(e->toggleCard());JPanel c=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));c.setOpaque(false);for(JComponent x:new JComponent[]{search,find,refresh,request,pay,handover,toggle})c.add(x);h.add(c,BorderLayout.SOUTH);return h;}
    private void load(){String term=search.getText().trim();String sql="SELECT c.card_id,c.card_number AS 'Số thẻ',m.member_code AS 'Mã trường',m.full_name AS 'Độc giả',m.member_type AS 'Nhóm',c.issue_type AS 'Loại cấp',c.fee AS 'Phí (VND)',c.card_status AS 'Trạng thái',COALESCE(SUM(p.amount),0) AS 'Đã thanh toán',c.requested_at AS 'Ngày đăng ký',c.handed_over_at AS 'Ngày giao' FROM library_cards c JOIN school_members m ON m.member_id=c.member_id LEFT JOIN card_payments p ON p.card_id=c.card_id WHERE (?='' OR c.card_number LIKE ? OR m.member_code LIKE ? OR m.full_name LIKE ?) GROUP BY c.card_id ORDER BY c.requested_at DESC";try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement(sql)){String p="%"+term+"%";q.setString(1,term);q.setString(2,p);q.setString(3,p);q.setString(4,p);try(ResultSet rs=q.executeQuery()){ResultSetMetaData md=rs.getMetaData();model.setRowCount(0);model.setColumnCount(0);for(int i=2;i<=md.getColumnCount();i++)model.addColumn(md.getColumnLabel(i));while(rs.next()){Object[] a=new Object[md.getColumnCount()-1];for(int i=2;i<=md.getColumnCount();i++)a[i-2]=rs.getObject(i);model.addRow(a);}}status.setText("Đang hiển thị "+model.getRowCount()+" thẻ.");}catch(Exception ex){model.setRowCount(0);status.setText("Không tải được thẻ: "+ex.getMessage());}}
    private void requestCard(){String code=JOptionPane.showInputDialog(this,"Mã thành viên trường:","Đăng ký thẻ",JOptionPane.PLAIN_MESSAGE);if(code==null||code.isBlank())return;try(Connection c=Database.getConnection()){long memberId;int existing;try(PreparedStatement q=c.prepareStatement("SELECT member_id FROM school_members WHERE member_code=? AND member_type IN ('STUDENT','LEARNER','LECTURER') AND institution_status='ACTIVE' AND (valid_until IS NULL OR valid_until>=CURRENT_DATE())")){q.setString(1,code.trim());try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không đủ điều kiện cấp thẻ: hồ sơ không hoạt động hoặc đã hết hạn.");memberId=r.getLong(1);}}try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM library_cards WHERE member_id=? AND card_status IN ('ACTIVE','PENDING_PAYMENT','PENDING_HANDOVER')")){q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("Độc giả đã có thẻ đang hoạt động hoặc chờ xử lý.");}}try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM library_cards WHERE member_id=?")){q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){r.next();existing=r.getInt(1);}}String kind=existing==0?"FIRST_ISSUE":"REISSUE";int fee=setting(c,existing==0?"CARD_FIRST_ISSUE_FEE":"CARD_REISSUE_FEE",existing==0?50000:20000);String number="LC-"+System.currentTimeMillis();try(PreparedStatement q=c.prepareStatement("INSERT INTO library_cards(card_number,member_id,issue_type,fee) VALUES(?,?,?,?)")){q.setString(1,number);q.setLong(2,memberId);q.setString(3,kind);q.setInt(4,fee);q.executeUpdate();}load();message("Đã tạo yêu cầu thẻ "+number+" với phí "+fee+" đồng. Thu phí rồi xác nhận giao thẻ tại thư viện.",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){message("Không đăng ký được thẻ: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private void collectFee(){String number=JOptionPane.showInputDialog(this,"Số thẻ cần thu phí:","Thu phí thẻ",JOptionPane.PLAIN_MESSAGE);if(number==null||number.isBlank())return;try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long cardId;String state;BigDecimalValue due;try(PreparedStatement q=c.prepareStatement("SELECT c.card_id,c.card_status,c.fee-COALESCE((SELECT SUM(p.amount) FROM card_payments p WHERE p.card_id=c.card_id),0) AS due FROM library_cards c WHERE c.card_number=? FOR UPDATE")){q.setString(1,number.trim());try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy số thẻ.");cardId=r.getLong(1);state=r.getString(2);due=new BigDecimalValue(r.getBigDecimal(3));}}if(!"PENDING_PAYMENT".equals(state))throw new IllegalArgumentException("Thẻ không ở trạng thái chờ thanh toán.");if(due.value().compareTo(java.math.BigDecimal.ZERO)<=0)throw new IllegalArgumentException("Thẻ đã thanh toán đủ phí.");long staff=staffId(c);String receipt="CP-"+System.currentTimeMillis();try(PreparedStatement q=c.prepareStatement("INSERT INTO card_payments(card_id,amount,receipt_number,received_by) VALUES(?,?,?,?)")){q.setLong(1,cardId);q.setBigDecimal(2,due.value());q.setString(3,receipt);q.setLong(4,staff);q.executeUpdate();}try(PreparedStatement q=c.prepareStatement("UPDATE library_cards SET card_status='PENDING_HANDOVER' WHERE card_id=?")){q.setLong(1,cardId);q.executeUpdate();}c.commit();load();message("Đã thu "+due.value().toPlainString()+" đồng. Thẻ chờ giao trực tiếp.",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}}catch(Exception ex){message("Không ghi nhận được thanh toán: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}}
    private void handover() {
        String number=JOptionPane.showInputDialog(this,"Số thẻ đã giao trực tiếp cho độc giả:","Giao thẻ",JOptionPane.PLAIN_MESSAGE);
        if(number==null||number.isBlank())return;
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long cardId,memberId,staff; String issueType;
                String sql="SELECT c.card_id,c.member_id,c.issue_type FROM library_cards c JOIN school_members m ON m.member_id=c.member_id "
                        +"WHERE c.card_number=? AND c.card_status='PENDING_HANDOVER' AND c.fee<=COALESCE((SELECT SUM(amount) FROM card_payments WHERE card_id=c.card_id),0) "
                        +"AND m.institution_status='ACTIVE' AND (m.valid_until IS NULL OR m.valid_until>=CURRENT_DATE()) FOR UPDATE";
                try(PreparedStatement q=c.prepareStatement(sql)){
                    q.setString(1,number.trim());
                    try(ResultSet r=q.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("Thẻ chưa thanh toán đủ, hồ sơ trường hết hạn hoặc thẻ không chờ giao.");
                        cardId=r.getLong(1);memberId=r.getLong(2);issueType=r.getString(3);
                    }
                }
                staff=staffId(c);
                try(PreparedStatement q=c.prepareStatement("UPDATE library_cards SET card_status='ACTIVE',handed_over_at=CURRENT_TIMESTAMP,handed_over_by=?,locked_reason=NULL WHERE card_id=? AND card_status='PENDING_HANDOVER'")){
                    q.setLong(1,staff);q.setLong(2,cardId);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Trạng thái thẻ vừa thay đổi; hãy tải lại.");
                }
                if("REISSUE".equals(issueType)){
                    try(PreparedStatement q=c.prepareStatement("UPDATE library_cards SET card_status='LOCKED',locked_reason=CONCAT('Đã thay thế bởi ',?) WHERE member_id=? AND card_id<>? AND card_status='ACTIVE'")){
                        q.setString(1,number.trim());q.setLong(2,memberId);q.setLong(3,cardId);q.executeUpdate();
                    }
                }
                c.commit();load();message("Đã xác nhận giao thẻ. Thẻ cũ đã bị vô hiệu hóa nếu đây là lần cấp lại.",JOptionPane.INFORMATION_MESSAGE);
            }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }catch(Exception ex){message("Không xác nhận được giao thẻ: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}
    }
    private void toggleCard() {
        int view=table.getSelectedRow();
        if(view<0){message("Chọn một thẻ trước.",JOptionPane.INFORMATION_MESSAGE);return;}
        int row=table.convertRowIndexToModel(view);
        String number=model.getValueAt(row,0).toString();
        String state=model.getValueAt(row,6).toString();
        if(!"ACTIVE".equals(state)&&!"LOCKED".equals(state)){message("Chỉ có thể khóa hoặc mở khóa thẻ đã cấp.",JOptionPane.WARNING_MESSAGE);return;}
        boolean locking="ACTIVE".equals(state);
        String reason=null;
        if(locking){
            reason=JOptionPane.showInputDialog(this,"Nhập lý do khóa thẻ:","Khóa thẻ",JOptionPane.PLAIN_MESSAGE);
            if(reason==null)return;
            if(reason.isBlank()){message("Cần nhập lý do khóa thẻ.",JOptionPane.WARNING_MESSAGE);return;}
        }
        if(JOptionPane.showConfirmDialog(this,"Xác nhận "+(locking?"khóa":"mở khóa")+" thẻ "+number+"?","Xác nhận",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long cardId,memberId;String lockedReason;
                try(PreparedStatement q=c.prepareStatement("SELECT card_id,member_id,locked_reason FROM library_cards WHERE card_number=? AND card_status=? FOR UPDATE")){
                    q.setString(1,number);q.setString(2,state);
                    try(ResultSet r=q.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("Trạng thái thẻ vừa thay đổi; hãy tải lại.");
                        cardId=r.getLong(1);memberId=r.getLong(2);lockedReason=r.getString(3);
                    }
                }
                if(locking){
                    try(PreparedStatement q=c.prepareStatement("UPDATE library_cards SET card_status='LOCKED',locked_reason=? WHERE card_id=?")){q.setString(1,reason.trim());q.setLong(2,cardId);q.executeUpdate();}
                }else{
                    if(lockedReason!=null&&lockedReason.startsWith("Đã thay thế bởi "))throw new IllegalArgumentException("Thẻ cũ đã được thay thế và không thể mở lại.");
                    try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM school_members WHERE member_id=? AND institution_status='ACTIVE' AND (valid_until IS NULL OR valid_until>=CURRENT_DATE())")){
                        q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)==0)throw new IllegalArgumentException("Hồ sơ trường không còn hiệu lực.");}
                    }
                    try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM violations v JOIN loan_items li ON li.loan_item_id=v.loan_item_id JOIN loans l ON l.loan_id=li.loan_id WHERE l.member_id=? AND v.violation_status IN ('UNPAID','PARTIALLY_PAID')")){
                        q.setLong(1,memberId);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("Độc giả còn vi phạm chưa thanh toán.");}
                    }
                    try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM library_cards WHERE member_id=? AND card_status='ACTIVE' AND card_id<>?")){
                        q.setLong(1,memberId);q.setLong(2,cardId);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("Độc giả đã có thẻ hoạt động khác.");}
                    }
                    try(PreparedStatement q=c.prepareStatement("UPDATE library_cards SET card_status='ACTIVE',locked_reason=NULL WHERE card_id=? AND card_status='LOCKED'")){q.setLong(1,cardId);q.executeUpdate();}
                }
                c.commit();load();message(locking?"Đã khóa thẻ và lưu lý do.":"Đã mở khóa thẻ sau khi kiểm tra điều kiện.",JOptionPane.INFORMATION_MESSAGE);
            }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }catch(Exception ex){message("Không đổi được trạng thái thẻ: "+ex.getMessage(),JOptionPane.ERROR_MESSAGE);}
    }
    private int setting(Connection c,String key,int fallback)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key=?")){q.setString(1,key);try(ResultSet r=q.executeQuery()){return r.next()?Integer.parseInt(r.getString(1)):fallback;}}}
    private long staffId(Connection c)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT staff_id FROM accounts WHERE account_id=? AND staff_id IS NOT NULL")){q.setLong(1,session.accountId());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Tài khoản chưa liên kết nhân viên.");return r.getLong(1);}}}
    private void message(String s,int t){JOptionPane.showMessageDialog(this,s,"Thẻ thư viện",t);}
    private record BigDecimalValue(java.math.BigDecimal amount){java.math.BigDecimal value(){return amount==null?java.math.BigDecimal.ZERO:amount;}}
}
