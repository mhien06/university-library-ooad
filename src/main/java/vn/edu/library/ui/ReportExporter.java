package vn.edu.library.ui;

import javax.swing.JTable;
import javax.swing.table.TableModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ReportExporter {
    private ReportExporter() { }

    static void toXlsx(Path path, JTable table, String title) throws IOException {
        TableModel model=table.getModel();
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path),StandardCharsets.UTF_8)){
            put(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    +"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    +"<Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    +"<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    +"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    +"<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Bao cao\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    +"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                    .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            sheet.append("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>").append(xml(title)).append("</t></is></c></row>");
            appendRow(sheet,2,model,true);
            for(int row=0;row<model.getRowCount();row++)appendRow(sheet,row+3,model,false);
            sheet.append("</sheetData></worksheet>");
            put(zip,"xl/worksheets/sheet1.xml",sheet.toString());
        }
    }

    private static void appendRow(StringBuilder out,int row,TableModel model,boolean headers){
        out.append("<row r=\"").append(row).append("\">");
        for(int col=0;col<model.getColumnCount();col++){
            String reference=columnName(col+1)+row;
            Object value=headers?model.getColumnName(col):model.getValueAt(row-3,col);
            if(value instanceof Number number&&!headers){out.append("<c r=\"").append(reference).append("\"><v>").append(number).append("</v></c>");}
            else out.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value==null?"":value.toString())).append("</t></is></c>");
        }
        out.append("</row>");
    }

    private static String columnName(int number){StringBuilder name=new StringBuilder();while(number>0){int rem=(number-1)%26;name.insert(0,(char)('A'+rem));number=(number-1)/26;}return name.toString();}
    private static String xml(String value){StringBuilder out=new StringBuilder();for(char c:value.toCharArray()){if(c=='&')out.append("&amp;");else if(c=='<')out.append("&lt;");else if(c=='>')out.append("&gt;");else if(c=='\"')out.append("&quot;");else if(c=='\'')out.append("&apos;");else if(c=='\t'||c=='\n'||c=='\r'||c>=0x20)out.append(c);}return out.toString();}
    private static void put(ZipOutputStream zip,String name,String content)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}

    static void toPdf(Path path,JTable table,String title)throws IOException{
        TableModel model=table.getModel();int rowsPerPage=44;int pageCount=Math.max(1,(model.getRowCount()+rowsPerPage-1)/rowsPerPage);
        int firstPageObject=4;int objectCount=3+pageCount*2;List<byte[]> objects=new ArrayList<>(objectCount+1);objects.add(new byte[0]);
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids=new StringBuilder("<< /Type /Pages /Count ").append(pageCount).append(" /Kids [");
        for(int p=0;p<pageCount;p++)kids.append(firstPageObject+p*2).append(" 0 R ");
        kids.append("] >>");objects.add(ascii(kids.toString()));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        for(int page=0;page<pageCount;page++){
            int pageObject=firstPageObject+page*2,contentObject=pageObject+1;
            String content=pageContent(model,title,page,rowsPerPage);
            byte[] stream=ascii(content);
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents "+contentObject+" 0 R >>"));
            ByteArrayOutputStream streamObject=new ByteArrayOutputStream();streamObject.write(ascii("<< /Length "+stream.length+" >>\nstream\n"));streamObject.write(stream);streamObject.write(ascii("\nendstream"));objects.add(streamObject.toByteArray());
        }
        ByteArrayOutputStream pdf=new ByteArrayOutputStream();pdf.write(ascii("%PDF-1.4\n"));long[] offsets=new long[objectCount+1];
        for(int id=1;id<=objectCount;id++){offsets[id]=pdf.size();pdf.write(ascii(id+" 0 obj\n"));pdf.write(objects.get(id));pdf.write(ascii("\nendobj\n"));}
        long xref=pdf.size();pdf.write(ascii("xref\n0 "+(objectCount+1)+"\n0000000000 65535 f \n"));
        for(int id=1;id<=objectCount;id++)pdf.write(ascii(String.format(java.util.Locale.ROOT,"%010d 00000 n \n",offsets[id])));
        pdf.write(ascii("trailer\n<< /Size "+(objectCount+1)+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n"));
        Files.write(path,pdf.toByteArray());
    }

    private static String pageContent(TableModel model,String title,int page,int pageSize){
        StringBuilder out=new StringBuilder("BT /F1 14 Tf 32 565 Td (").append(pdf(title+"  |  Trang "+(page+1))).append(") Tj /F1 7 Tf 0 -20 Td (");
        for(int c=0;c<model.getColumnCount();c++){if(c>0)out.append(" | ");out.append(pdf(trim(model.getColumnName(c),22)));}
        out.append(") Tj");int start=page*pageSize,end=Math.min(model.getRowCount(),start+pageSize);
        for(int r=start;r<end;r++){out.append(" 0 -11 Td (");for(int c=0;c<model.getColumnCount();c++){if(c>0)out.append(" | ");out.append(pdf(trim(String.valueOf(model.getValueAt(r,c)),22)));}out.append(") Tj");}
        return out.append(" ET").toString();
    }
    private static String trim(String value,int max){String text=asciiText(value);return text.length()<=max?text:text.substring(0,Math.max(0,max-1))+"~";}
    private static String asciiText(String value){String normalized=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('Đ','D').replace('đ','d');return normalized.replaceAll("[^\\x20-\\x7E]","?");}
    private static String pdf(String value){return asciiText(value).replace("\\","\\\\").replace("(","\\(").replace(")","\\)");}
    private static byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
}
