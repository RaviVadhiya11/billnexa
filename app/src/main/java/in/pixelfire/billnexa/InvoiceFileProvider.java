package in.pixelfire.billnexa;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.List;

public class InvoiceFileProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri) throws FileNotFoundException {
        List<String> parts=uri.getPathSegments();
        if(parts.size()!=2||!parts.get(0).matches("[a-f0-9-]{36}")||!parts.get(1).matches("[A-Za-z0-9_.-]{1,100}\\.pdf"))throw new FileNotFoundException("Invalid PDF");
        try{
            File root=new File(getContext().getCacheDir(),"invoice-share").getCanonicalFile();
            File f=new File(new File(root,parts.get(0)),parts.get(1)).getCanonicalFile();
            if(!f.getPath().startsWith(root.getPath()+File.separator)||!f.isFile())throw new FileNotFoundException("PDF unavailable");
            return f;
        }catch(IOException e){throw new FileNotFoundException("PDF unavailable");}
    }
    @Override public String getType(Uri uri){return "application/pdf";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=f.getName();else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}c.addRow(row);return c;}catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
}
