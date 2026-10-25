package app.daybreak;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Synthetic document provider used only by the isolated emulator test APK. */
public final class SlowAudioProvider extends ContentProvider {
    static final Uri URI=Uri.parse("content://app.daybreak.test.slow-audio/recording");
    private volatile boolean slow;
    private int opens;
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if("slow".equals(method))slow=true;
        if("fast".equals(method))slow=false;
        Bundle result=new Bundle();result.putInt("opens",opens);return result;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws java.io.FileNotFoundException {
        if(!URI.equals(uri)||!"r".equals(mode))throw new java.io.FileNotFoundException("Unknown test recording");
        synchronized(this){opens++;}
        if(slow)try{Thread.sleep(10_000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        File target=new File(getContext().getCacheDir(),"synthetic-alarm-audio.m4a");
        if(!target.isFile())try(InputStream in=getContext().createPackageContext("app.daybreak",0).getAssets().open("sounds/gentle_09.m4a");FileOutputStream out=new FileOutputStream(target)){
            byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
        }catch(Exception error){throw new java.io.FileNotFoundException(error.toString());}
        return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){return "audio/mp4";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
}
