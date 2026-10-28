package app.daybreak;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.*;

/** Signed-release optional orchestra recordings, stored privately on the phone. */
public final class SoundPacks {
    private static final String URL="https://github.com/FourWags/rouse/releases/download/v0.1.21/petal-wake-music-pack.zip";
    private static final String SHA256="748449a1fb7dbcd39ac7a75532161e6d72927c9b6125583405be0d76c1065f16";
    private static File folder(Context c){return new File(c.getFilesDir(),"orchestra-pack");}
    public static boolean optional(String name){if(name==null||!name.matches("orchestra_[0-9]{2}\\.m4a"))return false;int number=Integer.parseInt(name.substring(10,12));return number>=5&&number<=32;}
    public static File file(Context c,String name){if(!optional(name))throw new IllegalArgumentException("Unknown pack sound.");return new File(folder(c),name);}
    public static boolean installed(Context c){File dir=folder(c);for(int i=5;i<=32;i++)if(!new File(dir,String.format(java.util.Locale.ROOT,"orchestra_%02d.m4a",i)).isFile())return false;return true;}
    static void restoreFrom(Context c,File staged)throws IOException{
        if(installed(c))return;
        File target=folder(c);
        if(target.exists())throw new IOException("Existing orchestra pack needs repair before restore.");
        if(!staged.renameTo(target))throw new IOException("Could not restore the orchestra pack.");
    }
    public static void install(Context c)throws Exception{
        if(installed(c))return;
        File archive=new File(c.getCacheDir(),"orchestra-pack-download.zip");File temp=new File(c.getFilesDir(),"orchestra-pack-new");removeTree(temp);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");URL url=new URL(URL);boolean finished=false;
        try{
            for(int redirect=0;redirect<6;redirect++){
                if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Music pack requires a secure download.");
                HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(20000);
                try{
                    int code=connection.getResponseCode();
                    if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Music pack redirect was incomplete.");url=new URL(url,location);continue;}
                    if(code!=200)throw new IOException("Music pack server returned "+code+".");
                    long total=0;try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(archive)){
                        byte[] bytes=new byte[32768];int count;while((count=in.read(bytes))!=-1){total+=count;if(total>70_000_000)throw new IOException("Music pack is too large.");digest.update(bytes,0,count);out.write(bytes,0,count);}
                    }
                    finished=true;break;
                }finally{connection.disconnect();}
            }
            if(!finished)throw new IOException("Music pack redirected too many times.");
            if(!SHA256.equals(hex(digest.digest())))throw new IOException("Music pack verification failed. Please retry.");
            if(!temp.mkdirs())throw new IOException("Cannot prepare music pack storage.");
            int entries=0;
            try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))){ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(!optional(name)||entry.isDirectory())throw new IOException("Music pack contains an unexpected file.");File target=new File(temp,name);if(target.exists())throw new IOException("Music pack contains a duplicate file.");long count=0;try(FileOutputStream out=new FileOutputStream(target)){byte[] bytes=new byte[16384];int n;while((n=zip.read(bytes))!=-1){count+=n;if(count>4_000_000)throw new IOException("Music pack recording is too large.");out.write(bytes,0,n);}}entries++;}
            }
            if(entries!=28)throw new IOException("Music pack is incomplete.");
            removeTree(folder(c));if(!temp.renameTo(folder(c)))throw new IOException("Could not finish installing music pack.");
        }finally{archive.delete();removeTree(temp);}
    }
    public static void remove(Context c){removeTree(folder(c));}
    private static void removeTree(File root){if(root.isDirectory()){File[] files=root.listFiles();if(files!=null)for(File child:files)removeTree(child);}root.delete();}
    private static String hex(byte[] data){StringBuilder out=new StringBuilder();for(byte value:data)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}
    private SoundPacks(){}
}
