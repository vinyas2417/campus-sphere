package com.campussphere.util;

import com.campussphere.model.DownloadDocument;
import java.awt.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Standalone A4 PDF with high-resolution rendered pages, including Unicode names. */
public final class PdfDocument {
    private static final int WIDTH=1654, HEIGHT=2339, MARGIN=110;
    private PdfDocument() { }
    public static void write(Path destination,DownloadDocument document) throws IOException {
        List<BufferedImage> pages=new ArrayList<>();
        Painter painter=new Painter(document,pages);
        for(var entry:document.entries()) painter.row(entry.label(),entry.value());
        painter.row("Note",document.note()); painter.finish();
        ByteArrayOutputStream pdf=new ByteArrayOutputStream();
        ascii(pdf,"%PDF-1.4\n");
        List<Integer> offsets=new ArrayList<>();offsets.add(0);
        object(pdf,offsets,1,"<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids=new StringBuilder();for(int i=0;i<pages.size();i++)kids.append(3+i*3).append(" 0 R ");
        object(pdf,offsets,2,"<< /Type /Pages /Count "+pages.size()+" /Kids ["+kids+"] >>");
        for(int i=0;i<pages.size();i++) {
            int page=3+i*3, image=page+1, content=page+2;
            object(pdf,offsets,page,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 "+image+" 0 R >> >> /Contents "+content+" 0 R >>");
            ByteArrayOutputStream jpeg=new ByteArrayOutputStream();ImageIO.write(pages.get(i),"jpeg",jpeg);
            stream(pdf,offsets,image,"/Type /XObject /Subtype /Image /Width "+WIDTH+" /Height "+HEIGHT+" /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode",jpeg.toByteArray());
            stream(pdf,offsets,content,"","q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n".getBytes(StandardCharsets.US_ASCII));
        }
        int xref=pdf.size();ascii(pdf,"xref\n0 "+offsets.size()+"\n0000000000 65535 f \n");
        for(int i=1;i<offsets.size();i++)ascii(pdf,String.format(java.util.Locale.ROOT,"%010d 00000 n \n",offsets.get(i)));
        ascii(pdf,"trailer\n<< /Size "+offsets.size()+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");
        Path absolute=destination.toAbsolutePath();Path temporary=Files.createTempFile(absolute.getParent(),"campus-document-",".tmp");
        try {
            Files.write(temporary,pdf.toByteArray());
            try {Files.move(temporary,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
    private static void ascii(OutputStream out,String value) throws IOException {out.write(value.getBytes(StandardCharsets.US_ASCII));}
    private static void object(ByteArrayOutputStream out,List<Integer> offsets,int id,String value) throws IOException {offsets.add(out.size());ascii(out,id+" 0 obj\n"+value+"\nendobj\n");}
    private static void stream(ByteArrayOutputStream out,List<Integer> offsets,int id,String attributes,byte[] bytes) throws IOException {
        offsets.add(out.size());ascii(out,id+" 0 obj\n<< "+attributes+" /Length "+bytes.length+" >>\nstream\n");out.write(bytes);ascii(out,"\nendstream\nendobj\n");
    }
    private static final class Painter {
        private final DownloadDocument doc;private final List<BufferedImage> pages;private Graphics2D g;private int y;
        Painter(DownloadDocument doc,List<BufferedImage> pages){this.doc=doc;this.pages=pages;page();}
        private void page() {
            BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);pages.add(image);g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);g.fillRect(0,0,WIDTH,HEIGHT);g.setColor(new Color(0x822e36));g.fillRect(0,0,WIDTH,210);
            g.setColor(Color.WHITE);g.setFont(new Font("Dialog",Font.BOLD,54));g.drawString("Namma Sphere",MARGIN,105);
            g.setFont(new Font("Dialog",Font.PLAIN,28));g.drawString("Campus records",MARGIN,160);
            g.setColor(new Color(0x54262b));g.setFont(new Font("Dialog",Font.BOLD,49));g.drawString(doc.title(),MARGIN,310);
            y=355;drawLines(layout("Reference: "+doc.reference(),27,WIDTH-2*MARGIN),MARGIN,new Color(0x786f6b));y+=45;
        }
        private List<TextLayout> layout(String text,int size,int width) {
            AttributedString attributed=new AttributedString(text==null||text.isBlank()?"-":text.replace('\n',' '));attributed.addAttribute(TextAttribute.FONT,new Font("Dialog",Font.PLAIN,size));
            var iterator=attributed.getIterator();LineBreakMeasurer measurer=new LineBreakMeasurer(iterator,g.getFontRenderContext());List<TextLayout> lines=new ArrayList<>();
            while(measurer.getPosition()<iterator.getEndIndex())lines.add(measurer.nextLayout(width));return lines;
        }
        private void drawLines(List<TextLayout> lines,int x,Color color) {g.setColor(color);for(TextLayout line:lines){y+=Math.ceil(line.getAscent());line.draw(g,x,y);y+=Math.ceil(line.getDescent()+line.getLeading())+9;}}
        void row(String label,String value) {
            List<TextLayout> lines=layout(value,32,WIDTH-2*MARGIN-390);int height=lines.stream().mapToInt(l->(int)Math.ceil(l.getAscent()+l.getDescent()+l.getLeading())+9).sum()+30;
            if(y+height>HEIGHT-170){finishPage();page();}
            int start=y;g.setColor(new Color(0x786f6b));g.setFont(new Font("Dialog",Font.BOLD,27));g.drawString(label,MARGIN,y+31);
            drawLines(lines,MARGIN+390,new Color(0x332b2b));y=Math.max(y,start+45)+25;
            g.setColor(new Color(0xeadfd6));g.drawLine(MARGIN,y,WIDTH-MARGIN,y);y+=18;
        }
        void finish(){finishPage();}
        private void finishPage(){g.setColor(new Color(0x786f6b));g.setFont(new Font("Dialog",Font.PLAIN,24));g.drawString("Namma Sphere  |  "+doc.title()+"  |  Page "+pages.size(),MARGIN,HEIGHT-85);g.dispose();}
    }
}
