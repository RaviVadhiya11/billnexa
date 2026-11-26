package in.pixelfire.billnexa;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

final class NativeStore {
    private final AtomicFile file;
    JSONObject db;
    NativeStore(Context c){file=new AtomicFile(new File(c.getFilesDir(),"billnexa-native-v1.json"));}
    boolean exists(){return file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists();}
    static JSONObject obj(Object... pairs){JSONObject o=new JSONObject();for(int i=0;i<pairs.length;i+=2)put(o,(String)pairs[i],pairs[i+1]);return o;}
    static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static JSONObject copy(JSONObject o){try{return new JSONObject(o.toString());}catch(Exception e){throw new IllegalArgumentException(e);}}
    static JSONArray arr(JSONObject o,String k){JSONArray a=o.optJSONArray(k);return a==null?new JSONArray():a;}
    static JSONObject sub(JSONObject o,String k){JSONObject a=o.optJSONObject(k);return a==null?new JSONObject():a;}
    static String str(JSONObject o,String k){return o.optString(k,"");}
    static String id(){return UUID.randomUUID().toString();}
    static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    static JSONObject business(){return obj("name","","address","","phone","","email","","gstin","","state","Gujarat","bank","","upi","","prefix","INV");}
    static JSONObject customer(){return obj("name","","phone","","gstin","","address","");}
    static JSONObject empty(){return obj("version",1,"business",business(),"customers",new JSONArray(),"products",new JSONArray(),"invoices",new JSONArray(),"next",1);}
    static String read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("File too large");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    void load()throws Exception{try(FileInputStream in=file.openRead()){JSONObject x=new JSONObject(read(in,40_000_000));validate(x);db=x;}}
    void commit(JSONObject next)throws Exception{validate(next);byte[] bytes=next.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>40_000_000)throw new IOException("Local data limit reached. Export backup.");FileOutputStream out=file.startWrite();try{out.write(bytes);file.finishWrite(out);db=next;}catch(Exception e){file.failWrite(out);throw e;}}
    static double n(JSONObject o,String k){double v=o.optDouble(k,0);return Double.isNaN(v)||Double.isInfinite(v)?0:Math.max(0,v);}
    static double round(double v){return BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP).doubleValue();}
    static double stockRound(double v){return Math.round(v*1000d)/1000d;}
    static String money(double v){NumberFormat f=NumberFormat.getNumberInstance(new Locale("en","IN"));f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return "₹"+f.format(v);}
    static String quantity(double v){return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();}
    static boolean quote(JSONObject i){return "quote".equals(str(i,"kind"));}
    static String title(JSONObject i){return quote(i)?"QUOTATION":"none".equals(str(i,"taxMode"))?"":"TAX INVOICE";}
    static JSONObject find(JSONArray a,String id){for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&str(x,"id").equals(id))return x;}return null;}
    static void upsert(JSONArray a,JSONObject o)throws Exception{for(int i=0;i<a.length();i++)if(str(a.getJSONObject(i),"id").equals(str(o,"id"))){a.put(i,o);return;}a.put(o);}
    static void remove(JSONArray a,String id){for(int i=0;i<a.length();i++)if(str(a.optJSONObject(i),"id").equals(id)){a.remove(i);return;}}
    static void require(boolean v,String m)throws IOException{if(!v)throw new IOException(m);}
    static boolean finite(Object value){if(!(value instanceof Number))return false;double d=((Number)value).doubleValue();return !Double.isNaN(d)&&!Double.isInfinite(d)&&d>=0&&d<=1e12;}
    static void fields(JSONObject o,String... keys)throws IOException{require(o!=null,"Missing record");for(String k:keys)require(o.opt(k) instanceof String,"Invalid "+k);}
    static void signature(Object v)throws IOException{if(v==null)return;require(v instanceof JSONArray,"Invalid signature");JSONArray a=(JSONArray)v;require(a.length()>0&&a.length()<=80,"Invalid signature");int count=0;for(int j=0;j<a.length();j++){JSONArray stroke=a.optJSONArray(j);require(stroke!=null&&stroke.length()>0,"Invalid signature stroke");count+=stroke.length();require(count<=1600,"Signature too large");for(int k=0;k<stroke.length();k++){JSONArray p=stroke.optJSONArray(k);require(p!=null&&p.length()==2&&finite(p.opt(0))&&finite(p.opt(1))&&p.optDouble(0)<=600&&p.optDouble(1)<=200,"Invalid signature point");}}}
    static void biz(JSONObject b)throws IOException{fields(b,"name","address","phone","email","gstin","state","bank","upi","prefix");if(b.has("logo")){String l=str(b,"logo");require(l.length()<=36000&&l.matches("data:image/jpeg;base64,[A-Za-z0-9+/]+={0,2}"),"Invalid shop logo");}}
    static void client(JSONObject c)throws IOException{fields(c,"name","phone","gstin","address");}
    static void recordId(JSONObject o,Set<String> seen)throws IOException{String v=str(o,"id");require(v.matches("[A-Za-z0-9-]+")&&seen.add(v),"Invalid or duplicate record ID");}
    static void validate(JSONObject x)throws Exception{
        require(x.optInt("version")==1&&x.optLong("next")>0,"Unsupported backup");biz(x.optJSONObject("business"));signature(x.opt("defaultSignature"));
        for(String k:new String[]{"customers","products","invoices"})require(x.optJSONArray(k)!=null,"Missing "+k);
        Set<String> ids=new HashSet<>();
        for(int j=0;j<arr(x,"customers").length();j++){JSONObject c=arr(x,"customers").getJSONObject(j);client(c);recordId(c,ids);}
        ids.clear();for(int j=0;j<arr(x,"products").length();j++){JSONObject p=arr(x,"products").getJSONObject(j);recordId(p,ids);fields(p,"name","hsn","unit");for(String k:new String[]{"rate","gst"})require(finite(p.opt(k)),"Invalid product "+k);for(String k:new String[]{"stock","lowStock","cost","discount"})if(p.has(k))require(finite(p.opt(k)),"Invalid "+k);}
        ids.clear();for(int j=0;j<arr(x,"invoices").length();j++){JSONObject i=arr(x,"invoices").getJSONObject(j);recordId(i,ids);fields(i,"number","date","dueDate","payment","notes");require(Arrays.asList("none","local","inter").contains(str(i,"taxMode")),"Invalid tax mode");biz(i.optJSONObject("business"));client(i.optJSONObject("customer"));signature(i.opt("signature"));require(finite(i.opt("paid")),"Invalid payment");JSONArray a=i.optJSONArray("items");require(a!=null&&a.length()>0&&a.length()<=1000,"Invalid items");for(int k=0;k<a.length();k++){JSONObject p=a.getJSONObject(k);fields(p,"name","hsn","unit");for(String v:new String[]{"qty","rate","gst","discount"})require(finite(p.opt(v)),"Invalid item "+v);require(n(p,"qty")>0,"Invalid quantity");}
          if(i.has("stockMoves")){JSONArray m=i.optJSONArray("stockMoves");require(m!=null,"Invalid stock history");for(int k=0;k<m.length();k++){JSONObject p=m.getJSONObject(k);require(str(p,"id").matches("[A-Za-z0-9-]+")&&finite(p.opt("qty"))&&n(p,"qty")>0,"Invalid stock move");}}
        }
    }
    static final class Totals {
        double base,discount,tax,total,paid,due;final JSONArray lines=new JSONArray();
        Totals(JSONObject i){JSONArray a=arr(i,"items");for(int j=0;j<a.length();j++){JSONObject x=a.optJSONObject(j);double gross=round(n(x,"qty")*n(x,"rate")),disc=round(gross*Math.min(100,n(x,"discount"))/100),b=round(gross-disc),t="none".equals(str(i,"taxMode"))?0:round(b*n(x,"gst")/100),v=round(b+t);lines.put(obj("gross",gross,"discount",disc,"base",b,"tax",t,"total",v));base=round(base+b);discount=round(discount+disc);tax=round(tax+t);total=round(total+v);}paid=quote(i)?0:Math.min(total,round(n(i,"paid")));due=round(total-paid);}
        String status(){return due==0?"Paid":paid>0?"Partial":"Unpaid";}
    }
    static JSONArray details(JSONObject o,String... pairs){JSONArray a=new JSONArray();for(int j=0;j<pairs.length;j+=2)if(!str(o,pairs[j]).isEmpty())a.put(pairs[j+1]+str(o,pairs[j]));return a;}
    static JSONArray row(Object... values){JSONArray a=new JSONArray();for(Object v:values)a.put(v);return a;}
    static JSONObject payload(JSONObject i){Totals t=new Totals(i);JSONObject b=sub(i,"business"),c=sub(i,"customer");JSONArray lines=new JSONArray(),items=arr(i,"items");for(int j=0;j<items.length();j++){JSONObject x=items.optJSONObject(j);lines.put(row(str(x,"name")+(str(x,"hsn").isEmpty()?"":"\nHSN: "+str(x,"hsn"))+(str(x,"sku").isEmpty()?"":"\nSKU: "+str(x,"sku")),quantity(n(x,"qty"))+" "+str(x,"unit"),money(n(x,"rate")),quantity(n(x,"discount"))+"%","none".equals(str(i,"taxMode"))?"—":quantity(n(x,"gst"))+"%",money(n(t.lines.optJSONObject(j),"total"))));}
        JSONArray sums=new JSONArray();sums.put(row("Discount",money(t.discount)));sums.put(row("Taxable / net amount",money(t.base)));if("local".equals(str(i,"taxMode"))){double cg=round(t.tax/2);sums.put(row("CGST",money(cg)));sums.put(row("SGST",money(round(t.tax-cg))));}else if("inter".equals(str(i,"taxMode")))sums.put(row("IGST",money(t.tax)));sums.put(row("Grand total",money(t.total)));if(!quote(i)){sums.put(row("Received",money(t.paid)));sums.put(row("Balance due",money(t.due)));}
        return obj("number",str(i,"number"),"title",title(i),"date",str(i,"date"),"dueDate",str(i,"dueDate"),"business",str(b,"name"),"logo",str(b,"logo"),"businessInfo",details(b,"address","","phone","Phone: ","email","Email: ","gstin","GSTIN: ","state","State: "),"customer",str(c,"name"),"customerInfo",details(c,"address","","phone","Phone: ","gstin","GSTIN: "),"payment",quote(i)?"Estimate only — not a tax invoice":str(i,"payment")+" · "+t.status(),"rows",lines,"summary",sums,"paymentInfo",details(b,"bank","","upi","UPI: "),"notes",str(i,"notes"),"signature",arr(i,"signature"));}
    static void stock(JSONObject db,JSONObject old,JSONObject next)throws Exception{Map<String,Double> delta=new LinkedHashMap<>(),moves=new LinkedHashMap<>();if(old!=null){JSONArray a=arr(old,"stockMoves");for(int j=0;j<a.length();j++){JSONObject m=a.getJSONObject(j);String id=str(m,"id");delta.put(id,stockRound((delta.containsKey(id)?delta.get(id):0)+n(m,"qty")));}}
        if(next!=null&&!quote(next)){JSONArray a=arr(next,"items");for(int j=0;j<a.length();j++){JSONObject x=a.getJSONObject(j);String id=str(x,"productId");JSONObject p=find(arr(db,"products"),id);if(p!=null&&p.has("stock")){double q=n(x,"qty");moves.put(id,stockRound((moves.containsKey(id)?moves.get(id):0)+q));delta.put(id,stockRound((delta.containsKey(id)?delta.get(id):0)-q));}}}
        for(Map.Entry<String,Double> e:delta.entrySet()){JSONObject p=find(arr(db,"products"),e.getKey());require(p!=null&&p.has("stock"),"Stock record missing");require(stockRound(n(p,"stock")+e.getValue())>=0,"Insufficient stock: "+str(p,"name"));}
        for(Map.Entry<String,Double> e:delta.entrySet()){JSONObject p=find(arr(db,"products"),e.getKey());put(p,"stock",stockRound(n(p,"stock")+e.getValue()));}
        if(next!=null){JSONArray a=new JSONArray();for(Map.Entry<String,Double> e:moves.entrySet())if(e.getValue()>0)a.put(obj("id",e.getKey(),"qty",e.getValue()));put(next,"stockMoves",a);}
    }
}
