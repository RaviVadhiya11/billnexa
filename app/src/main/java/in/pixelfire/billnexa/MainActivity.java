package in.pixelfire.billnexa;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int PICK_BACKUP=101,SAVE_BACKUP=102,SAVE_PDF=103,PICK_LOGO=104;
    private final ExecutorService pdfWorker=Executors.newSingleThreadExecutor();
    private String pendingPdf,pendingBackupPath;
    private NativeStore store;
    private NativeApp app;
    private FrameLayout root;
    private WebView migration;
    private boolean migrationFinished=false;
    interface LogoResult {void accept(String value);}
    private LogoResult logoResult;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){pendingPdf=state.getString("pendingPdf");pendingBackupPath=state.getString("pendingBackupPath");}
        Window window=getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root=new FrameLayout(this);root.setBackgroundColor(0xff04102e);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){
            window.setDecorFitsSystemWindows(false);window.setStatusBarColor(Color.TRANSPARENT);window.setNavigationBarColor(Color.TRANSPARENT);window.setStatusBarContrastEnforced(false);window.setNavigationBarContrastEnforced(false);
            WindowInsetsController controller=window.getInsetsController();if(controller!=null){controller.show(WindowInsets.Type.systemBars());controller.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
            root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));if(app!=null)app.keyboard(insets.isVisible(WindowInsets.Type.ime()));return WindowInsets.CONSUMED;});root.requestApplyInsets();
        }else{window.setStatusBarColor(0xff04102e);window.setNavigationBarColor(0xff04102e);window.getDecorView().setSystemUiVisibility(0);}
        store=new NativeStore(this);
        if(store.exists()){try{store.load();launch();}catch(Exception e){recovery("Local database could not be read. Restore a valid backup; existing files have not been overwritten.");}}
        else migrate();
    }
    private void launch(){
        migrationFinished=true;stopMigration();root.removeAllViews();app=new NativeApp(this,store);root.addView(app,new FrameLayout.LayoutParams(-1,-1));app.show("home");root.requestApplyInsets();
    }
    private void stopMigration(){if(migration!=null){root.removeView(migration);migration.stopLoading();migration.destroy();migration=null;}}
    private void recovery(String message){
        migrationFinished=true;stopMigration();root.removeAllViews();LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,36,24,24);l.setBackgroundColor(Color.WHITE);TextView t=new TextView(this);t.setText("BillNexa recovery\n\n"+message);t.setTextSize(17);t.setTextColor(0xff10223e);l.addView(t);Button retry=new Button(this);retry.setText("Retry");retry.setOnClickListener(v->recreate());l.addView(retry);Button restore=new Button(this);restore.setText("Restore JSON backup");restore.setOnClickListener(v->pickBackup());l.addView(restore);root.addView(l,new FrameLayout.LayoutParams(-1,-1));
    }
    // Only used once to import the old app's localStorage from the identical origin.
    // All user-facing screens and subsequent data operations are native Java.
    private void migrate(){
        migrationFinished=false;TextView wait=new TextView(this);wait.setText("BillNexa\nPreparing your local workspace…");wait.setTextSize(21);wait.setGravity(Gravity.CENTER);wait.setTextColor(Color.WHITE);root.addView(wait,new FrameLayout.LayoutParams(-1,-1));
        migration=new WebView(this);migration.setVisibility(View.INVISIBLE);root.addView(migration,new FrameLayout.LayoutParams(1,1));WebSettings settings=migration.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        migration.setWebViewClient(new WebViewClient(){
            boolean started=false;
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<!doctype html><html><body></body></html>".getBytes(StandardCharsets.UTF_8)));}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public void onPageFinished(WebView v,String url){if(started||migrationFinished)return;started=true;v.evaluateJavascript("(function(){try{var s=localStorage.getItem('billnexa-v1');return s===null?-1:s.length}catch(e){return -2}})()",value->{try{int length=Integer.parseInt(value);if(length==-1){store.commit(NativeStore.empty());launch();}else if(length<0||length>40_000_000)throw new IOException("Legacy data unavailable");else readChunk(0,length,new StringBuilder());}catch(Exception e){recovery("Could not migrate previous data. Retry or restore your JSON backup.");}});}
        });migration.loadUrl("https://appassets.androidplatform.net/index.html");
        new Handler(Looper.getMainLooper()).postDelayed(()->{if(!migrationFinished&&!isFinishing()&&!isDestroyed())recovery("Migration did not finish. Retry or restore your JSON backup.");},25000);
    }
    private void readChunk(int offset,int length,StringBuilder out){
        if(migrationFinished||migration==null)return;
        if(offset>=length){try{store.commit(new JSONObject(out.toString()));launch();}catch(Exception e){recovery("Previous records could not be validated. No original data was overwritten. Restore a backup or retry.");}return;}
        int end=Math.min(offset+24000,length);
        migration.evaluateJavascript("localStorage.getItem('billnexa-v1').slice("+offset+","+end+")",value->{if(migrationFinished)return;try{Object parsed=new JSONTokener(value).nextValue();if(!(parsed instanceof String)||((String)parsed).length()!=end-offset)throw new IOException("Incomplete migration");out.append((String)parsed);readChunk(end,length,out);}catch(Exception e){recovery("Previous data could not be read. Retry or restore a JSON backup.");}});
    }
    void tell(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
    void openExternal(String url){try{Intent i=new Intent(url.startsWith("mailto:")?Intent.ACTION_SENDTO:Intent.ACTION_VIEW,Uri.parse(url));if(!url.startsWith("mailto:"))i.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(i);}catch(Exception e){tell("No compatible app available");}}
    void shareSummary(String value){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(i,"Share bill summary"));}
    void clearLegacyAndExports(){WebStorage.getInstance().deleteAllData();pdfWorker.execute(()->{File dir=new File(getCacheDir(),"invoice-share");File[] folders=dir.listFiles();if(folders!=null)for(File f:folders){File[] children=f.listFiles();if(children!=null)for(File child:children)child.delete();f.delete();}dir.delete();});}
    void chooseLogo(LogoResult callback){logoResult=callback;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"});try{startActivityForResult(i,PICK_LOGO);}catch(Exception e){logoResult=null;tell("Image picker unavailable");}}
    void pickBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,PICK_BACKUP);}catch(Exception e){tell("File picker unavailable");}}
    void saveNativeBackup(String value){if(pendingBackupPath!=null){tell("Finish the current backup first");return;}try{File f=File.createTempFile("billnexa-backup-",".json",getCacheDir());try(FileOutputStream out=new FileOutputStream(f)){out.write(value.getBytes(StandardCharsets.UTF_8));}pendingBackupPath=f.getAbsolutePath();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"billnexa-backup.json");startActivityForResult(i,SAVE_BACKUP);}catch(Exception e){pendingBackupPath=null;tell("Backup unavailable");}}
    private String imageLogo(Uri uri)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot read image");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8_000_000)throw new IOException("Choose image under 8 MB");out.write(buffer,0,n);}}
        byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<1||options.outHeight<1)throw new IOException("Invalid image");int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>512)sample*=2;options.inJustDecodeBounds=false;options.inSampleSize=sample;Bitmap source=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(source==null)throw new IOException("Invalid image");Bitmap canvas=null;
        try{float scale=Math.min(1f,256f/Math.max(source.getWidth(),source.getHeight()));int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));canvas=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(canvas);c.drawColor(Color.WHITE);c.drawBitmap(source,null,new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));for(int q:new int[]{90,75,60,45,30}){out.reset();canvas.compress(Bitmap.CompressFormat.JPEG,q,out);String s="data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);if(s.length()<=36000)return s;}throw new IOException("Logo too large");}finally{source.recycle();if(canvas!=null)canvas.recycle();}
    }
    private File makePdf(JSONObject data) throws Exception {
        File root=new File(getCacheDir(),"invoice-share");
        if(root.exists()){File[] dirs=root.listFiles();if(dirs!=null)for(File d:dirs)if(d.isDirectory()&&System.currentTimeMillis()-d.lastModified()>7L*24*60*60*1000){File[] old=d.listFiles();if(old!=null)for(File f:old)f.delete();d.delete();}}
        File dir=new File(root,UUID.randomUUID().toString());if(!dir.mkdirs())throw new IOException("Cannot create PDF folder");
        String n=data.optString("number","invoice").replaceAll("[^A-Za-z0-9_.-]","_");if(n.isEmpty())n="invoice";if(n.length()>80)n=n.substring(0,80);
        File f=new File(dir,"BillNexa-"+n+".pdf");
        try(FileOutputStream out=new FileOutputStream(f)){InvoicePdf.write(data,out,null);}
        return f;
    }
    void handlePdf(String json,String action){
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

    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("pendingPdf",pendingPdf);state.putString("pendingBackupPath",pendingBackupPath);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);Uri uri=result==RESULT_OK&&data!=null?data.getData():null;
        if(request==SAVE_PDF||request==SAVE_BACKUP){boolean backup=request==SAVE_BACKUP;String path=backup?pendingBackupPath:pendingPdf;if(backup)pendingBackupPath=null;else pendingPdf=null;if(path==null)return;if(uri==null){if(backup)new File(path).delete();return;}pdfWorker.execute(()->{try(InputStream in=new FileInputStream(path);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot open destination");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->tell(backup?"Backup saved":path.endsWith(".jpg")?"Invoice JPG saved":"Invoice PDF saved"));}catch(Exception e){runOnUiThread(()->tell("File save failed"));}finally{if(backup)new File(path).delete();}});return;}
        if(request==PICK_BACKUP&&uri!=null){pdfWorker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot read backup");String raw=NativeStore.read(in,40_000_000);runOnUiThread(()->{if(app!=null)app.restore(raw);else{try{JSONObject x=new JSONObject(raw);NativeStore.validate(x);new AlertDialog.Builder(this).setTitle("Restore backup?").setMessage("Replace local records with selected backup?").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{try{store.commit(x);launch();}catch(Exception e){tell("Restore failed");}}).show();}catch(Exception e){tell("Invalid backup");}}});}catch(Exception e){runOnUiThread(()->tell("Cannot read backup"));}});return;}
        if(request==PICK_LOGO){LogoResult callback=logoResult;logoResult=null;if(uri==null||callback==null)return;pdfWorker.execute(()->{try{String image=imageLogo(uri);runOnUiThread(()->callback.accept(image));}catch(Exception|OutOfMemoryError e){runOnUiThread(()->tell("Could not read logo"));}});}
    }
    @Override public void onBackPressed(){if(app!=null)app.back();else super.onBackPressed();}
    @Override protected void onDestroy(){migrationFinished=true;stopMigration();pdfWorker.shutdown();super.onDestroy();}
}
