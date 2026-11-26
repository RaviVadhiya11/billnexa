package in.pixelfire.billnexa;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.print.PageRange;
import org.json.*;
import java.io.OutputStream;
import java.util.ArrayList;

final class InvoicePdf {
    static final int W=595,H=842,M=34, END=793;
    static final int INK=Color.rgb(52,37,24), BLUE=Color.rgb(154,52,18), MUTED=Color.rgb(98,84,73), LINE=Color.rgb(241,213,181), SAFFRON=Color.rgb(255,153,51), DEEP=Color.rgb(180,83,9), CREAM=Color.rgb(255,245,233);
    static final int[] WIDTHS={181,64,73,49,47,113};
    private final JSONObject invoice;
    private final PdfDocument doc;
    private final PageRange[] ranges;
    private PdfDocument.Page page;
    private Canvas canvas;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int y,pageNo;
    private boolean table;
    InvoicePdf(JSONObject data,PdfDocument document,PageRange[] selection){invoice=data;doc=document;ranges=selection;}
    static int count(JSONObject data) throws Exception {
        InvoicePdf r=new InvoicePdf(data,null,null);r.draw();return r.pageNo;
    }
    static int write(JSONObject data,OutputStream stream,PageRange[] pages) throws Exception {
        PdfDocument pdf=new PdfDocument();
        InvoicePdf r=new InvoicePdf(data,pdf,pages);
        try {r.draw();pdf.writeTo(stream);return r.pageNo;}
        finally {try{r.finish();}finally{pdf.close();}}
    }
    private boolean selected(int index){if(ranges==null)return true;for(PageRange r:ranges)if(index>=r.getStart()&&index<=r.getEnd())return true;return false;}
    private TextPaint font(float size,boolean bold,int color){
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return p;
    }
    private StaticLayout layout(String text,int width,float size,boolean bold,int color,Layout.Alignment align){
        String s=text==null?"":text;
        return StaticLayout.Builder.obtain(s,0,s.length(),font(size,bold,color),Math.max(1,width)).setAlignment(align).setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();
    }
    private void drawLayout(StaticLayout l,int x,int top){if(canvas!=null){canvas.save();canvas.translate(x,top);l.draw(canvas);canvas.restore();}}
    private void rect(int x,int top,int right,int bottom,int color){if(canvas!=null){paint.setColor(color);canvas.drawRect(x,top,right,bottom,paint);}}
    private void label(String t,int x,int top,int width,float size,boolean bold,int color,boolean right){drawLayout(layout(t,width,size,bold,color,right?Layout.Alignment.ALIGN_OPPOSITE:Layout.Alignment.ALIGN_NORMAL),x,top);}
    private void start(){
        finish();pageNo++;if(doc!=null&&selected(pageNo-1)){page=doc.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());canvas=page.getCanvas();}
        y=30;rect(M,y,W-M,y+5,SAFFRON);y+=12;
        rect(M,y,W-M,y+49,CREAM);
        String title=invoice.optString("title","");
        if(!title.isEmpty())label(title,W-M-250,y+7,238,13,true,BLUE,true);
        label("No. "+invoice.optString("number")+"  ·  Page "+pageNo,M+12,y+(title.isEmpty()?17:28),W-2*M-24,10,false,MUTED,false);
        y+=62;
    }
    private void finish(){
        if(canvas!=null){rect(M,810,W-M,811,LINE);label("Generated with BillNexa",M,818,260,8,false,MUTED,false);label("Page "+pageNo,W-M-100,818,100,8,false,MUTED,true);doc.finishPage(page);}
        page=null;canvas=null;
    }
    private void ensure(int height){if(y+height>END){start();if(table)header();}}
    private void paragraph(String text,float size,boolean bold,int color){
        if(text==null||text.isEmpty())return;
        StaticLayout l=layout(text,W-2*M,size,bold,color,Layout.Alignment.ALIGN_NORMAL);
        // Draw each laid-out line separately so arbitrarily long descriptions can span pages.
        int lines=l.getLineCount();
        for(int n=0;n<lines;n++){
            int top=l.getLineTop(n),bottom=l.getLineBottom(n),height=Math.max(1,bottom-top);
            ensure(height+2);
            if(canvas!=null){canvas.save();canvas.clipRect(M,y,W-M,y+height);canvas.translate(M,y-top);l.draw(canvas);canvas.restore();}
            y+=height;
        }
        y+=5;
    }
    private void info(JSONArray arr){if(arr!=null)for(int k=0;k<arr.length();k++)paragraph(arr.optString(k),10,false,INK);}
    private void heading(String title){ensure(24);y+=5;paragraph(title,11,true,BLUE);}
    private void header(){
        String[] cols={"Product / service","Qty","Rate","Disc.","GST","Amount"};
        rect(M,y,W-M,y+23,DEEP);int x=M;
        for(int n=0;n<cols.length;n++){label(cols[n],x+5,y+6,WIDTHS[n]-10,9,true,Color.WHITE,n>0);x+=WIDTHS[n];}
        y+=23;
    }
    private void row(JSONArray values,int index){
        StaticLayout[] cells=new StaticLayout[6];int maxLines=1;
        for(int n=0;n<6;n++){cells[n]=layout(values.optString(n),WIDTHS[n]-10,9,n==0,INK,n==0?Layout.Alignment.ALIGN_NORMAL:Layout.Alignment.ALIGN_OPPOSITE);maxLines=Math.max(maxLines,cells[n].getLineCount());}
        int line=0;while(line<maxLines){
            ensure(21);
            int maxChunk=Math.max(1,(END-y-8)/13),take=Math.min(maxChunk,maxLines-line),height=take*13+8;
            if(index%2==0)rect(M,y,W-M,y+height,Color.rgb(255,248,239));
            int x=M;for(int n=0;n<6;n++){
                StaticLayout l=cells[n];
                if(line<l.getLineCount()&&canvas!=null){
                    // Each cell is clipped to this chunk of lines, never across the table boundary.
                    for(int q=line;q<Math.min(line+take,l.getLineCount());q++){
                        int top=l.getLineTop(q),bottom=l.getLineBottom(q),at=y+4+(q-line)*13;
                        canvas.save();canvas.clipRect(x+5,at,x+WIDTHS[n]-5,at+Math.min(13,bottom-top));canvas.translate(x+5,at-top);l.draw(canvas);canvas.restore();
                    }
                }
                x+=WIDTHS[n];
            }
            y+=height;rect(M,y-1,W-M,y,LINE);line+=take;
        }
    }
    private void summary(String key,String value){
        boolean total=key.equals("Grand total");
        StaticLayout a=layout(key,200,total?14:11,true,total?BLUE:INK,Layout.Alignment.ALIGN_NORMAL);
        StaticLayout b=layout(value,157,total?14:11,true,total?BLUE:INK,Layout.Alignment.ALIGN_OPPOSITE);
        int h=Math.max(a.getHeight(),b.getHeight())+12;ensure(h);
        int x=W-M-370;
        if(total){rect(x,y,W-M,y+h,Color.rgb(255,237,215));rect(x,y,x+4,y+h,SAFFRON);}
        drawLayout(a,x+6,y+6);drawLayout(b,W-M-163,y+6);y+=h;
    }
    private void drawSignature(JSONArray strokes) throws Exception {
        if(strokes==null||strokes.length()==0)return;
        if(strokes.length()>80)throw new IllegalArgumentException("Too many signature strokes");
        ArrayList<Path> paths=new ArrayList<>();int points=0;
        for(int s=0;s<strokes.length();s++){
            JSONArray stroke=strokes.getJSONArray(s);if(stroke.length()==0)throw new IllegalArgumentException("Empty stroke");
            points+=stroke.length();if(points>1600)throw new IllegalArgumentException("Signature too large");
            Path path=new Path();float px=0,py=0;
            for(int n=0;n<stroke.length();n++){
                JSONArray point=stroke.getJSONArray(n);double x=point.getDouble(0),yy=point.getDouble(1);
                if(point.length()!=2||Double.isNaN(x)||Double.isInfinite(x)||Double.isNaN(yy)||Double.isInfinite(yy)||x<0||x>600||yy<0||yy>200)throw new IllegalArgumentException("Invalid signature point");
                px=(float)x;py=(float)yy;if(n==0)path.moveTo(px,py);else path.lineTo(px,py);
            }
            if(stroke.length()==1)path.lineTo(px+0.01f,py);
            paths.add(path);
        }
        ensure(90);
        if(canvas!=null){
            Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);pen.setColor(Color.rgb(21,45,85));pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(4.5f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);
            canvas.save();canvas.translate(M,y);canvas.scale(0.30f,0.30f);for(Path path:paths)canvas.drawPath(path,pen);canvas.restore();
        }
        y+=65;
    }
    private void drawShopLogo() {
        String logo=invoice.optString("logo","");
        String prefix="data:image/jpeg;base64,";
        if(!logo.startsWith(prefix)||logo.length()>36000)return;
        Bitmap bitmap=null;
        try{
            byte[] bytes=android.util.Base64.decode(logo.substring(prefix.length()),android.util.Base64.DEFAULT);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>256||bounds.outHeight>256)return;
            bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(bitmap==null)return;
            float scale=Math.min(105f/bitmap.getWidth(),65f/bitmap.getHeight());
            int height=Math.max(1,(int)Math.ceil(bitmap.getHeight()*scale));
            ensure(height+8);
            if(canvas!=null){Paint logoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.drawBitmap(bitmap,null,new RectF(M,y,M+bitmap.getWidth()*scale,y+height),logoPaint);}
            y+=height+8;
        }catch(IllegalArgumentException ignored){}finally{if(bitmap!=null)bitmap.recycle();}
    }
    private void draw() throws Exception {
        start();
        drawShopLogo();paragraph(invoice.optString("business"),23,true,BLUE);info(invoice.optJSONArray("businessInfo"));ensure(12);rect(M,y,W-M,y+1,LINE);y+=9;
        heading("BILL TO");paragraph(invoice.optString("customer"),15,true,INK);info(invoice.optJSONArray("customerInfo"));
        paragraph("Date: "+invoice.optString("date")+(invoice.optString("dueDate").isEmpty()?"":"   |   Due: "+invoice.optString("dueDate")),10,false,INK);
        ensure(30);rect(M,y,W-M,y+27,CREAM);label("PAYMENT  ·  "+invoice.optString("payment"),M+8,y+8,W-2*M-16,10,true,BLUE,false);y+=35;
        ensure(45);table=true;header();
        JSONArray rows=invoice.getJSONArray("rows");if(rows.length()>1000)throw new IllegalArgumentException("Maximum 1000 items per PDF");
        for(int n=0;n<rows.length();n++)row(rows.getJSONArray(n),n);
        table=false;y+=10;
        JSONArray sums=invoice.getJSONArray("summary");for(int n=0;n<sums.length();n++){JSONArray entry=sums.getJSONArray(n);summary(entry.optString(0),entry.optString(1));}
        JSONArray pay=invoice.optJSONArray("paymentInfo");if(pay!=null&&pay.length()>0){heading("PAYMENT DETAILS");info(pay);}
        if(!invoice.optString("notes").isEmpty()){heading("NOTES / TERMS");paragraph(invoice.optString("notes"),10,false,INK);}
        ensure(60);y+=12;rect(M,y,W-M,y+1,LINE);y+=10;paragraph("Thank you for your business.",10,true,BLUE);
        drawSignature(invoice.optJSONArray("signature"));
        paragraph("Authorised signatory",10,true,INK);paragraph(invoice.optString("business"),10,false,MUTED);
        finish();
    }
}
