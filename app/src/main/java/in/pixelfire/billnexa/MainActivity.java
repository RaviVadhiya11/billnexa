package in.pixelfire.billnexa;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.*;
import android.widget.Toast;
import android.view.View;
import android.view.WindowInsets;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private WebView printWeb;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingBackup;
    private static final int PICK=101, SAVE=102;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web=new WebView(this);
        setContentView(web);
        if(android.os.Build.VERSION.SDK_INT>=35) {
            web.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
            web.requestApplyInsets();
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
    private class Bridge {
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
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}else if(request==SAVE){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingBackup!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new Exception();out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Backup saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Backup failed",Toast.LENGTH_SHORT).show();}}pendingBackup=null;}}
    @Override public void onBackPressed(){web.evaluateJavascript("if(screen==='home'){false}else{go('home');true}",value->{if("false".equals(value))super.onBackPressed();});}
}
