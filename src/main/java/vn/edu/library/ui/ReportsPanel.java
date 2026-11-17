package vn.edu.library.ui;

import vn.edu.library.data.Database;
import vn.edu.library.domain.UserSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.sql.*;
import java.time.LocalDate;

public final class ReportsPanel extends JPanel {
    private final UserSession session;
    private final JComboBox<String> kind = new JComboBox<>(new String[]{"Sách theo tình trạng", "Mượn – trả theo kỳ", "Độc giả và thẻ", "Vi phạm và tiền phạt"});
    private final JTextField from = new JTextField(LocalDate.now().withDayOfMonth(1).toString(), 10);
    private final JTextField to = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField bookFilter = new JTextField(16);
    private final JLabel status = new JLabel("Chọn báo cáo rồi nhấn Tổng hợp.");
    private final DefaultTableModel model = new DefaultTableModel() { @Override public boolean isCellEditable(int row,int col){return false;} };
    private final JTable table = new JTable(model);
    private final JButton exportPdf = new JButton("Xuất PDF"), exportXlsx = new JButton("Xuất Excel");
    private final ReportChart chart = new ReportChart();
    private String reportTitle="";

    public ReportsPanel(UserSession session) {
        this.session=session;
        setLayout(new BorderLayout(0,18));setBackground(UiStyle.CANVAS);setBorder(new EmptyBorder(28,32,24,32));
        JPanel header=new JPanel();header.setOpaque(false);header.setLayout(new BoxLayout(header,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Báo cáo – thống kê");title.setFont(title.getFont().deriveFont(Font.BOLD,25f));
        JLabel subtitle=new JLabel("Tổng hợp số liệu theo danh mục, khoảng thời gian và bộ lọc.");subtitle.setForeground(UiStyle.MUTED);
        header.add(title);header.add(Box.createVerticalStrut(5));header.add(subtitle);header.add(Box.createVerticalStrut(16));
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));controls.setOpaque(false);
        controls.add(new JLabel("Loại báo cáo"));controls.add(kind);controls.add(new JLabel("Từ ngày"));controls.add(from);controls.add(new JLabel("Đến ngày"));controls.add(to);
        controls.add(new JLabel("Lọc sách"));controls.add(bookFilter);
        JButton generate=new JButton("Tổng hợp & lưu báo cáo"),history=new JButton("Lịch sử báo cáo");generate.setBackground(UiStyle.BLUE);generate.setForeground(Color.WHITE);generate.addActionListener(e->generate());history.addActionListener(e->showHistory());
        exportPdf.setEnabled(false);exportXlsx.setEnabled(false);exportPdf.addActionListener(e->export(false));exportXlsx.addActionListener(e->export(true));
        controls.add(generate);controls.add(history);controls.add(exportPdf);controls.add(exportXlsx);header.add(controls);add(header,BorderLayout.NORTH);
        kind.addActionListener(e->updateControls());updateControls();
        table.setRowHeight(34);table.setAutoCreateRowSorter(true);table.setFillsViewportHeight(true);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),chart);split.setResizeWeight(.72);split.setDividerLocation(420);split.setBorder(null);add(split,BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);add(status,BorderLayout.SOUTH);
    }

    private void updateControls(){boolean snapshot=kind.getSelectedIndex()==0;from.setEnabled(!snapshot);to.setEnabled(!snapshot);bookFilter.setEnabled(snapshot);}

    private void generate(){
        model.setRowCount(0);model.setColumnCount(0);chart.repaint();exportPdf.setEnabled(false);exportXlsx.setEnabled(false);
        LocalDate start=null,end=null;int selected=kind.getSelectedIndex();
        if(selected!=0){
            try{start=LocalDate.parse(from.getText().trim());end=LocalDate.parse(to.getText().trim());}
            catch(Exception ex){fail("Ngày cần nhập theo định dạng YYYY-MM-DD.");return;}
            if(start.isAfter(end)){fail("Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc.");return;}
        }
        String sql;String filter=bookFilter.getText().trim();
        if(selected==0){
            sql="SELECT b.title AS 'Tên sách',c.category_name AS 'Thể loại',COALESCE(a.authors,'') AS 'Tác giả',COALESCE(i.total_count,0) AS 'Tổng bản',"
                    +"COALESCE(i.available_count,0) AS 'Có sẵn',COALESCE(i.pending_loan_count,0) AS 'Chờ giao',COALESCE(i.loan_count,0) AS 'Đang mượn',COALESCE(i.reserved_count,0) AS 'Đang giữ',"
                    +"COALESCE(i.damaged_count,0) AS 'Hư hỏng',COALESCE(i.lost_count,0) AS 'Mất',COALESCE(i.withdrawn_count,0) AS 'Thanh lý' "
                    +"FROM books b LEFT JOIN categories c ON c.category_id=b.category_id "
                    +"LEFT JOIN (SELECT book_id,GROUP_CONCAT(author_name ORDER BY author_name SEPARATOR ', ') authors FROM book_authors ba JOIN authors au ON au.author_id=ba.author_id GROUP BY book_id) a ON a.book_id=b.book_id "
                    +"LEFT JOIN (SELECT book_id,COUNT(*) total_count,SUM(copy_status='AVAILABLE') available_count,SUM(copy_status='PENDING_LOAN') pending_loan_count,SUM(copy_status='ON_LOAN') loan_count,"
                    +"SUM(copy_status='RESERVED') reserved_count,SUM(copy_status='DAMAGED') damaged_count,SUM(copy_status='LOST') lost_count,SUM(copy_status='WITHDRAWN') withdrawn_count "
                    +"FROM book_copies GROUP BY book_id) i ON i.book_id=b.book_id "
                    +"WHERE (?='' OR b.title LIKE ? OR c.category_name LIKE ? OR a.authors LIKE ? OR EXISTS(SELECT 1 FROM book_copies x WHERE x.book_id=b.book_id AND x.copy_status LIKE ?)) ORDER BY b.title";
        }else if(selected==1){
            sql="SELECT COUNT(DISTINCT CASE WHEN l.loan_status<>'DRAFT' AND l.borrowed_at>=? AND l.borrowed_at<? THEN l.loan_id END) AS 'Phiếu mượn trong kỳ',"
                    +"COALESCE(SUM(CASE WHEN li.returned_at>=? AND li.returned_at<? THEN 1 ELSE 0 END),0) AS 'Số bản trả trong kỳ',"
                    +"COALESCE(SUM(CASE WHEN li.returned_at>=? AND li.returned_at<? AND DATE(li.returned_at)<=li.due_at THEN 1 ELSE 0 END),0) AS 'Trả đúng hạn',"
                    +"COALESCE(SUM(CASE WHEN li.returned_at>=? AND li.returned_at<? AND DATE(li.returned_at)>li.due_at THEN 1 ELSE 0 END),0) AS 'Trả quá hạn',"
                    +"(SELECT COUNT(*) FROM loan_items li JOIN loans x ON x.loan_id=li.loan_id WHERE x.loan_status IN ('OPEN','PARTIALLY_RETURNED') AND li.returned_at IS NULL) AS 'Đang mượn hiện tại',"
                    +"(SELECT COUNT(*) FROM loan_items li JOIN loans x ON x.loan_id=li.loan_id WHERE x.loan_status IN ('OPEN','PARTIALLY_RETURNED') AND li.returned_at IS NULL AND li.due_at<CURRENT_DATE()) AS 'Quá hạn hiện tại' "
                    +"FROM loans l LEFT JOIN loan_items li ON li.loan_id=l.loan_id";
        }else if(selected==2){
            sql="SELECT CASE m.member_type WHEN 'STUDENT' THEN 'Sinh viên' WHEN 'LEARNER' THEN 'Học viên' ELSE 'Giảng viên' END AS 'Nhóm',"
                    +"COUNT(DISTINCT m.member_id) AS 'Số độc giả',COUNT(c.card_id) AS 'Thẻ đăng ký trong kỳ',"
                    +"COALESCE(SUM(c.card_status='ACTIVE'),0) AS 'Thẻ hoạt động',COALESCE(SUM(c.card_status IN ('PENDING_PAYMENT','PENDING_HANDOVER')),0) AS 'Thẻ chờ',"
                    +"COALESCE(SUM(c.card_status='LOCKED'),0) AS 'Thẻ bị khóa' FROM school_members m "
                    +"LEFT JOIN library_cards c ON c.member_id=m.member_id AND c.requested_at>=? AND c.requested_at<? GROUP BY m.member_type ORDER BY m.member_type";
        }else{
            sql="SELECT v.violation_type AS 'Loại vi phạm',COUNT(*) AS 'Số trường hợp',SUM(v.assessed_amount) AS 'Tổng phải thu (VND)',"
                    +"SUM(v.paid_amount) AS 'Đã thu (VND)',SUM(CASE WHEN v.violation_status='WAIVED' THEN 0 ELSE GREATEST(v.assessed_amount-v.paid_amount,0) END) AS 'Còn nợ (VND)' "
                    +"FROM violations v WHERE v.assessed_at>=? AND v.assessed_at<? GROUP BY v.violation_type ORDER BY v.violation_type";
        }
        try(Connection connection=Database.getConnection();PreparedStatement query=connection.prepareStatement(sql)){
            if(selected==0){String like="%"+filter+"%";query.setString(1,filter);for(int i=2;i<=5;i++)query.setString(i,like);}
            else if(selected==1){Timestamp begin=Timestamp.valueOf(start.atStartOfDay()),finish=Timestamp.valueOf(end.plusDays(1).atStartOfDay());for(int i=1;i<=8;i+=2){query.setTimestamp(i,begin);query.setTimestamp(i+1,finish);}}
            else{query.setTimestamp(1,Timestamp.valueOf(start.atStartOfDay()));query.setTimestamp(2,Timestamp.valueOf(end.plusDays(1).atStartOfDay()));}
            try(ResultSet rows=query.executeQuery()){
                ResultSetMetaData meta=rows.getMetaData();model.setRowCount(0);model.setColumnCount(0);
                for(int i=1;i<=meta.getColumnCount();i++)model.addColumn(meta.getColumnLabel(i));
                while(rows.next()){Object[] values=new Object[meta.getColumnCount()];for(int i=1;i<=values.length;i++)values[i-1]=rows.getObject(i);model.addRow(values);}
            }
            reportTitle=(String)kind.getSelectedItem()+(selected==0?" (tồn kho hiện tại)":" ("+start+" đến "+end+")");
            boolean saved=true;
            try{saveReport(start,end,filter);}
            catch(Exception saveError){saved=false;status.setText("Đã tổng hợp nhưng không lưu được lịch sử báo cáo: "+saveError.getMessage());}
            exportPdf.setEnabled(model.getRowCount()>0);exportXlsx.setEnabled(model.getRowCount()>0);chart.repaint();
            if(model.getRowCount()==0)status.setText("Không có dữ liệu phù hợp trong kỳ.");
            else status.setText(saved?"Đã tổng hợp và lưu "+model.getRowCount()+" dòng. Bạn có thể xem biểu đồ, mở lịch sử hoặc xuất PDF/Excel.":"Đã tổng hợp "+model.getRowCount()+" dòng nhưng không lưu được snapshot. "+status.getText());
        }catch(Exception ex){fail("Không lập được báo cáo: "+ex.getMessage());}
    }

    private void export(boolean excel){
        if(model.getRowCount()==0){fail("Báo cáo rỗng, không thể xuất.");return;}
        String extension=excel?"xlsx":"pdf";JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Xuất báo cáo "+extension.toUpperCase());
        chooser.setSelectedFile(new File("bao-cao-thu-vien."+extension));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();if(!file.getName().toLowerCase().endsWith("."+extension))file=new File(file.getParentFile(),file.getName()+"."+extension);
        if(file.exists()&&JOptionPane.showConfirmDialog(this,"Tệp đã tồn tại. Ghi đè?","Xác nhận",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{if(excel)ReportExporter.toXlsx(file.toPath(),table,reportTitle);else ReportExporter.toPdf(file.toPath(),table,reportTitle);status.setText("Đã xuất báo cáo: "+file.getAbsolutePath());}
        catch(Exception ex){fail("Không xuất được báo cáo: "+ex.getMessage());}
    }

    private void saveReport(LocalDate start,LocalDate end,String filter)throws SQLException{
        String csv=snapshotCsv();
        try(Connection c=Database.getConnection()){
            long staff;
            try(PreparedStatement q=c.prepareStatement("SELECT staff_id FROM accounts WHERE account_id=? AND staff_id IS NOT NULL")){
                q.setLong(1,session.accountId());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Tài khoản chưa liên kết hồ sơ nhân viên.");staff=r.getLong(1);}
            }
            try(PreparedStatement q=c.prepareStatement("INSERT INTO report_runs(report_title,report_type,from_date,to_date,filter_text,snapshot_csv,generated_by) VALUES(?,?,?,?,?,?,?)")){
                q.setString(1,reportTitle);q.setString(2,(String)kind.getSelectedItem());
                if(start==null)q.setNull(3,Types.DATE);else q.setDate(3,Date.valueOf(start));
                if(end==null)q.setNull(4,Types.DATE);else q.setDate(4,Date.valueOf(end));
                q.setString(5,filter.isBlank()?null:filter);q.setString(6,csv);q.setLong(7,staff);q.executeUpdate();
            }
        }
    }

    private String snapshotCsv(){
        StringBuilder out=new StringBuilder();
        for(int c=0;c<model.getColumnCount();c++){if(c>0)out.append(',');out.append(csvCell(model.getColumnName(c)));}out.append("\r\n");
        for(int r=0;r<model.getRowCount();r++){
            for(int c=0;c<model.getColumnCount();c++){if(c>0)out.append(',');Object value=model.getValueAt(r,c);out.append(csvCell(value==null?"":value.toString()));}
            out.append("\r\n");
        }
        return out.toString();
    }

    private String csvCell(String value){return "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}

    private void showHistory(){
        DefaultTableModel runs=new DefaultTableModel(new String[]{"ID","Báo cáo","Từ ngày","Đến ngày","Người lập","Thời điểm"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
        try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement("SELECT rr.report_run_id,rr.report_title,rr.from_date,rr.to_date,s.full_name,rr.generated_at FROM report_runs rr JOIN staff_profiles s ON s.staff_id=rr.generated_by ORDER BY rr.generated_at DESC LIMIT 200");ResultSet r=q.executeQuery()){
            while(r.next())runs.addRow(new Object[]{r.getLong(1),r.getString(2),r.getDate(3),r.getDate(4),r.getString(5),r.getTimestamp(6)});
        }catch(Exception ex){fail("Không tải được lịch sử báo cáo: "+ex.getMessage());return;}
        JTable history=new JTable(runs);history.setRowHeight(29);history.setAutoCreateRowSorter(true);history.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel panel=new JPanel(new BorderLayout(0,8));panel.add(new JScrollPane(history),BorderLayout.CENTER);panel.setPreferredSize(new Dimension(850,360));
        JButton view=new JButton("Xem snapshot CSV"),close=new JButton("Đóng");JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(view);actions.add(close);panel.add(actions,BorderLayout.SOUTH);
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Lịch sử báo cáo đã lưu",Dialog.ModalityType.APPLICATION_MODAL);dialog.setContentPane(panel);dialog.pack();dialog.setLocationRelativeTo(this);
        close.addActionListener(e->dialog.dispose());
        view.addActionListener(e->{int selected=history.getSelectedRow();if(selected<0){JOptionPane.showMessageDialog(dialog,"Chọn báo cáo muốn xem.");return;}int row=history.convertRowIndexToModel(selected);long id=Long.parseLong(runs.getValueAt(row,0).toString());try(Connection c=Database.getConnection();PreparedStatement q=c.prepareStatement("SELECT snapshot_csv FROM report_runs WHERE report_run_id=?")){q.setLong(1,id);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Báo cáo không còn tồn tại.");JTextArea data=new JTextArea(r.getString(1),24,90);data.setEditable(false);data.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));JOptionPane.showMessageDialog(dialog,new JScrollPane(data),runs.getValueAt(row,1).toString(),JOptionPane.INFORMATION_MESSAGE);}}catch(Exception ex){JOptionPane.showMessageDialog(dialog,"Không mở được snapshot: "+ex.getMessage(),"Lịch sử báo cáo",JOptionPane.ERROR_MESSAGE);}});
        dialog.setVisible(true);
    }

    private void fail(String message){exportPdf.setEnabled(false);exportXlsx.setEnabled(false);chart.repaint();status.setText(message);}

    private final class ReportChart extends JPanel{
        ReportChart(){setPreferredSize(new Dimension(400,175));setBackground(Color.WHITE);setBorder(BorderFactory.createTitledBorder("Biểu đồ nhanh · cột số liệu đầu tiên"));}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);if(model.getRowCount()==0||model.getColumnCount()<2)return;
            int numeric=-1;for(int c=1;c<model.getColumnCount();c++){Object value=null;for(int r=0;r<model.getRowCount()&&value==null;r++)value=model.getValueAt(r,c);if(value instanceof Number){numeric=c;break;}}
            if(numeric<0)return;Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int left=175,right=getWidth()-25,top=28,bottom=getHeight()-24;double max=1;
            for(int r=0;r<model.getRowCount();r++){Object v=model.getValueAt(r,numeric);if(v instanceof Number n)max=Math.max(max,n.doubleValue());}
            int shown=Math.min(6,model.getRowCount()),step=Math.max(18,(bottom-top)/Math.max(1,shown));
            for(int r=0;r<shown;r++){Object label=model.getValueAt(r,0),v=model.getValueAt(r,numeric);double value=v instanceof Number n?n.doubleValue():0;int y=top+r*step;String text=String.valueOf(label);if(text.length()>24)text=text.substring(0,23)+"…";g.setColor(new Color(80,97,91));g.drawString(text,12,y+12);int width=(int)((right-left)*value/max);g.setColor(new Color(61,123,95));g.fillRoundRect(left,y,Math.max(1,width),13,5,5);g.setColor(new Color(63,80,72));g.drawString(String.format(java.util.Locale.ROOT,"%.0f",value),Math.min(right-28,left+width+5),y+12);}
            g.dispose();
        }
    }
}
