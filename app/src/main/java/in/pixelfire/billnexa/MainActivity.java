package in.pixelfire.billnexa;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.*;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.content.ClipData;
import java.io.*;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.print.PrintManager;
import android.webkit.*;
import android.widget.Toast;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private WebView printWeb;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingBackup;
    private static final int PICK=101, SAVE=102, SAVE_PDF=103;
    private String pendingPdf;
    private final ExecutorService pdfWorker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null)pendingPdf=state.getString("pendingPdf");
        android.view.Window window=getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        final int navy=Color.rgb(4,16,46);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(navy);
        web=new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        root.addView(web,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
            WindowInsetsController controller=window.getInsetsController();
            if(controller!=null){
                controller.show(WindowInsets.Type.systemBars());
                controller.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
                if(web!=null)web.evaluateJavascript("document.documentElement.classList.toggle('keyboard-open',"+insets.isVisible(WindowInsets.Type.ime())+")",null);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }else{
            window.setStatusBarColor(navy);
            window.setNavigationBarColor(navy);
            window.getDecorView().setSystemUiVisibility(0);
        }
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r){
                if("https://appassets.androidplatform.net/index.html".equals(r.getUrl().toString())){try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}catch(Exception e){}}
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return true;}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null); fileCallback=callback;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);
                try{startActivityForResult(i,PICK);return true;}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;return true;}
            }
        });
        web.addJavascriptInterface(new Bridge(),"BillNexaNative");
        web.loadUrl("https://appassets.androidplatform.net/index.html");
    }
    private void tell(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
    private File makePdf(JSONObject data) throws Exception {
        File root=new File(getCacheDir(),"invoice-share");
        if(root.exists()){File[] dirs=root.listFiles();if(dirs!=null)for(File d:dirs)if(d.isDirectory()&&System.currentTimeMillis()-d.lastModified()>7L*24*60*60*1000){File[] old=d.listFiles();if(old!=null)for(File f:old)f.delete();d.delete();}}
        File dir=new File(root,UUID.randomUUID().toString());if(!dir.mkdirs())throw new IOException("Cannot create PDF folder");
        String n=data.optString("number","invoice").replaceAll("[^A-Za-z0-9_.-]","_");if(n.isEmpty())n="invoice";if(n.length()>80)n=n.substring(0,80);
        File f=new File(dir,"BillNexa-"+n+".pdf");
        try(FileOutputStream out=new FileOutputStream(f)){InvoicePdf.write(data,out,null);}
        return f;
    }
    private void handlePdf(String json,String action){
        if(json==null||json.length()>1000000){tell("Invoice too large");return;}
        if("saveJpg".equals(action)||"shareJpg".equals(action)){handleJpg(json,action);return;}
        if(!"save".equals(action)&&!"share".equals(action)&&!"print".equals(action))return;
        pdfWorker.execute(()->{try{
            JSONObject data=new JSONObject(json);
            if("print".equals(action)){int pages=InvoicePdf.count(data);runOnUiThread(()->printPdf(data,pages));return;}
            File file=makePdf(data);
            runOnUiThread(()->{try{
                if("save".equals(action)){
                    if(pendingPdf!=null){tell("Finish the current PDF save first");return;}
                    pendingPdf=file.getAbsolutePath();
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,file.getName());
                    try{startActivityForResult(i,SAVE_PDF);}catch(Exception e){pendingPdf=null;throw e;}
                }else{
                    Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".invoicefiles").appendPath(file.getParentFile().getName()).appendPath(file.getName()).build();
                    Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri(file.getName(),uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i,"Share invoice PDF"));
                }
            }catch(Exception e){tell("Unable to open PDF action");}});
        }catch(Exception e){runOnUiThread(()->tell("Could not generate PDF"));}});
    }
    private void handleJpg(String json,String action){
        tell("Preparing HD invoice image...");
        pdfWorker.execute(()->{try{
            File source=makePdf(new JSONObject(json));
            int count;
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
                count=renderer.getPageCount();
            }
            final int total=count;
            runOnUiThread(()->{
                if(total<=0){tell("No invoice pages");return;}
                if(total==1){exportJpgPage(source,0,total,action);return;}
                String[] labels=new String[total];for(int n=0;n<total;n++)labels[n]="Page "+(n+1)+" of "+total;
                new AlertDialog.Builder(this).setTitle("Choose JPG page").setItems(labels,(dialog,which)->exportJpgPage(source,which,total,action)).setNegativeButton("Cancel",null).show();
            });
        }catch(Exception e){runOnUiThread(()->tell("Could not prepare invoice image"));}});
    }
    private void exportJpgPage(File source,int index,int total,String action){
        pdfWorker.execute(()->{try{
            String base=source.getName().substring(0,source.getName().length()-4);
            File image=new File(source.getParentFile(),base+(total>1?"-page-"+(index+1):"")+".jpg");
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
                try(PdfRenderer.Page p=renderer.openPage(index)){
                    Bitmap bitmap=Bitmap.createBitmap(2480,3508,Bitmap.Config.ARGB_8888);
                    try{bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        try(FileOutputStream out=new FileOutputStream(image)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,98,out))throw new IOException("JPEG encoding failed");}
                    }finally{bitmap.recycle();}
                }
            }
            runOnUiThread(()->{try{
                if("saveJpg".equals(action)){
                    if(pendingPdf!=null){tell("Finish the current file save first");return;}
                    pendingPdf=image.getAbsolutePath();
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("image/jpeg");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,image.getName());
                    try{startActivityForResult(i,SAVE_PDF);}catch(Exception e){pendingPdf=null;throw e;}
                }else{
                    Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".invoicefiles").appendPath(image.getParentFile().getName()).appendPath(image.getName()).build();
                    Intent i=new Intent(Intent.ACTION_SEND);i.setType("image/jpeg");i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri(image.getName(),uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i,"Share invoice JPG"));
                }
            }catch(Exception e){tell("Unable to open JPG action");}});
        }catch(Exception|OutOfMemoryError e){runOnUiThread(()->tell("Could not generate HD JPG"));}});
    }
    private void printPdf(JSONObject data,int pages){
        PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);
        if(pm==null){tell("Print service unavailable");return;}
        try{pm.print(data.optString("number","Invoice"),new PrintDocumentAdapter(){
            @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,CancellationSignal cancel,LayoutResultCallback cb,Bundle extras){
                if(cancel.isCanceled()){cb.onLayoutCancelled();return;}
                cb.onLayoutFinished(new PrintDocumentInfo.Builder("BillNexa.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(),!newA.equals(oldA));
            }
            @Override public void onWrite(PageRange[] ranges,ParcelFileDescriptor dest,CancellationSignal cancel,WriteResultCallback cb){
                pdfWorker.execute(()->{try(FileOutputStream out=new FileOutputStream(dest.getFileDescriptor())){
                    if(cancel.isCanceled()){cb.onWriteCancelled();return;}
                    InvoicePdf.write(data,out,ranges);
                    if(cancel.isCanceled())cb.onWriteCancelled();else cb.onWriteFinished(ranges);
                }catch(Exception e){cb.onWriteFailed("Unable to prepare invoice PDF");}});
            }
        },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());}
        catch(Exception e){tell("Print service unavailable");}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("pendingPdf",pendingPdf);}
    private class Bridge {
        @JavascriptInterface public void clearExportCache(){pdfWorker.execute(()->{
            File root=new File(getCacheDir(),"invoice-share");
            File[] folders=root.listFiles();
            if(folders!=null)for(File dir:folders){if(!dir.isDirectory())continue;File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();dir.delete();}
        });}
        @JavascriptInterface public void exportPdf(String json,String action){runOnUiThread(()->handlePdf(json,action));}
        @JavascriptInterface public void printInvoice(String name,String html){runOnUiThread(()->{
            if(printWeb!=null)printWeb.destroy();
            printWeb=new WebView(MainActivity.this);
            printWeb.setWebViewClient(new WebViewClient(){
                private boolean started=false;
                @Override public void onPageFinished(WebView v,String url){if(started)return;started=true;
                    try{PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print(name,v.createPrintDocumentAdapter(name),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());}
                    catch(Exception e){Toast.makeText(MainActivity.this,"Print unavailable",Toast.LENGTH_SHORT).show();}
                }
            });
            printWeb.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);
        });}
        @JavascriptInterface public void shareText(String value){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(i,"Share invoice summary"));});}
        @JavascriptInterface public void saveBackup(String value){runOnUiThread(()->{pendingBackup=value;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"billnexa-backup.json");try{startActivityForResult(i,SAVE);}catch(Exception e){pendingBackup=null;Toast.makeText(MainActivity.this,"Backup unavailable",Toast.LENGTH_SHORT).show();}});}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SAVE_PDF){
        String path=pendingPdf;pendingPdf=null;
        if(result==RESULT_OK&&data!=null&&data.getData()!=null&&path!=null){
            Uri target=data.getData();pdfWorker.execute(()->{
                try(InputStream in=new FileInputStream(path);OutputStream out=getContentResolver().openOutputStream(target,"wt")){
                    if(out==null)throw new IOException("Cannot open output");byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    runOnUiThread(()->tell(path.endsWith(".jpg")?"Invoice JPG saved":"Invoice PDF saved"));
                }catch(Exception e){runOnUiThread(()->tell("Invoice file save failed"));}
            });
        }
        return;
    }if(request==PICK&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}else if(request==SAVE){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingBackup!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new Exception();out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Backup saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Backup failed",Toast.LENGTH_SHORT).show();}}pendingBackup=null;}}
    @Override public void onBackPressed(){web.evaluateJavascript("if(screen==='home'){false}else{go('home');true}",value->{if("false".equals(value))super.onBackPressed();});}
}
