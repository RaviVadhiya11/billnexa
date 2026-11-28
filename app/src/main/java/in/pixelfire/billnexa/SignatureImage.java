package in.pixelfire.billnexa;

import android.content.ContentResolver;
import android.graphics.*;
import android.net.Uri;
import android.util.Base64;
import java.io.*;

final class SignatureImage {
    private static final String PREFIX="data:image/png;base64,";
    private static final int MAX_TEXT=120000, MAX_WIDTH=900, MAX_HEIGHT=360;
    private SignatureImage(){}

    static String importImage(ContentResolver resolver,Uri uri)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IOException("Cannot read image");
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>8000000)throw new IOException("Choose image under 8 MB");out.write(buf,0,n);}
        }
        byte[] bytes=out.toByteArray();
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(!"image/jpeg".equals(o.outMimeType)&&!"image/png".equals(o.outMimeType))throw new IOException("Choose a valid JPG or PNG image");
        if(o.outWidth<1||o.outHeight<1||o.outWidth>50000||o.outHeight>50000)throw new IOException("Invalid image dimensions");
        int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>1800)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap source=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(source==null)throw new IOException("Cannot decode image");
        try{
            if(android.os.Build.VERSION.SDK_INT>=24&&"image/jpeg".equals(o.outMimeType)){
                try{
                    int orientation=new android.media.ExifInterface(new ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
                    Matrix matrix=new Matrix();
                    switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}
                    if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);if(rotated!=source){source.recycle();source=rotated;}}
                }catch(IOException ignored){}
            }
            float scale=Math.min(1f,Math.min(MAX_WIDTH/(float)source.getWidth(),MAX_HEIGHT/(float)source.getHeight()));
            for(int attempt=0;attempt<8;attempt++){
                int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));
                Bitmap resized=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                try{
                    new Canvas(resized).drawBitmap(source,null,new Rect(0,0,w,h),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                    out.reset();if(!resized.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not prepare signature");
                    String value=PREFIX+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
                    if(value.length()<=MAX_TEXT)return value;
                }finally{resized.recycle();}
                scale*=0.75f;
            }
            throw new IOException("Signature image is too complex. Choose a smaller image.");
        }finally{source.recycle();}
    }
    private static byte[] checkedBytes(String value)throws IOException{
        if(value==null||value.length()>MAX_TEXT||!value.startsWith(PREFIX))throw new IOException("Invalid signature image");
        byte[] b;
        try{b=Base64.decode(value.substring(PREFIX.length()),Base64.NO_WRAP);}catch(IllegalArgumentException e){throw new IOException("Invalid signature image",e);}
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(b,0,b.length,o);
        if(!"image/png".equals(o.outMimeType)||o.outWidth<1||o.outHeight<1||o.outWidth>MAX_WIDTH||o.outHeight>MAX_HEIGHT)throw new IOException("Invalid signature image");
        return b;
    }
    static void validate(String value)throws IOException{byte[] b=checkedBytes(value);Bitmap bitmap=BitmapFactory.decodeByteArray(b,0,b.length);if(bitmap==null)throw new IOException("Invalid signature image");bitmap.recycle();}
    static Bitmap decode(String value){try{byte[] b=checkedBytes(value);return BitmapFactory.decodeByteArray(b,0,b.length);}catch(IOException|IllegalArgumentException e){return null;}}
}
