package app.daybreak;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** User-selected audio. Copies are private and offline; links keep a persisted read grant. */
public final class CustomSounds {
    private static final String KEY="customSounds";
    private static final long MAX_FILE=100_000_000L,MAX_TOTAL=500_000_000L;
    private static final int MAX_ITEMS=40;
    public static final String[] GENRES={"Orchestral","Piano","Ambient","Nature","Acoustic","Jazz","Electronic","Classical","Other"};
    public static boolean id(String value){return value!=null&&value.matches("custom:[a-f0-9-]{36}");}
    private static File folder(Context c){return new File(c.getFilesDir(),"custom-sounds");}
    private static File file(Context c,String id){if(!id(id))throw new IllegalArgumentException("Invalid custom sound.");return new File(folder(c),id.substring(7)+".audio");}
    private static JSONArray saved(Context c){return Store.array(c,KEY);}
    private static JSONObject find(Context c,String id){if(!id(id))return null;JSONArray list=saved(c);for(int i=0;i<list.length();i++){JSONObject row=list.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))return row;}return null;}
    public static JSONArray forUi(Context c)throws Exception{
        JSONArray result=new JSONArray(),list=saved(c);
        for(int i=0;i<list.length();i++){
            JSONObject row=list.getJSONObject(i);
            result.put(new JSONObject().put("file",row.optString("id")).put("title",row.optString("title","My music"))
                .put("genre",row.optString("genre","Other")).put("mode",row.optString("mode"))
                .put("duration",row.optInt("duration")).put("available",available(c,row.optString("id"))));
        }
        return result;
    }
    public static boolean available(Context c,String id){
        try{
            JSONObject row=find(c,id);if(row==null)return false;
            if("copy".equals(row.optString("mode")))return file(c,id).isFile();
            if(!"link".equals(row.optString("mode")))return false;
            String target=row.optString("uri");
            for(android.content.UriPermission grant:c.getContentResolver().getPersistedUriPermissions())
                if(grant.isReadPermission()&&target.equals(grant.getUri().toString()))return true;
            return false;
        }catch(RuntimeException unavailable){return false;}
    }
    public static boolean linked(Context c,String id){JSONObject row=find(c,id);return row!=null&&"link".equals(row.optString("mode"));}
    public static void setDataSource(MediaPlayer player,Context c,String id)throws Exception{
        JSONObject row=find(c,id);if(row==null)throw new java.io.FileNotFoundException("Custom sound was removed.");
        if("copy".equals(row.getString("mode")))player.setDataSource(file(c,id).getAbsolutePath());
        else if("link".equals(row.getString("mode"))&&available(c,id))player.setDataSource(c,Uri.parse(row.getString("uri")));
        else throw new java.io.FileNotFoundException("Linked sound is unavailable.");
    }
    public static void add(Context c,Uri uri,boolean link,String genre)throws Exception{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IllegalArgumentException("Choose an audio file from your phone.");
        boolean known=false;for(String choice:GENRES)if(choice.equals(genre))known=true;
        if(!known)throw new IllegalArgumentException("Choose a music genre.");
        JSONArray list=saved(c);if(list.length()>=MAX_ITEMS)throw new IllegalArgumentException("Keep up to 40 personal sounds.");
        if(link)for(int i=0;i<list.length();i++){JSONObject existing=list.optJSONObject(i);if(existing!=null&&uri.toString().equals(existing.optString("uri")))throw new IllegalArgumentException("This file is already linked. Select it from My music.");}
        String id="custom:"+UUID.randomUUID();File target=file(c,id),part=new File(folder(c),id.substring(7)+".part");
        boolean grant=false,committed=false;
        try{
            if(link){c.getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);grant=true;}
            else{
                if(!folder(c).isDirectory()&&!folder(c).mkdirs())throw new java.io.IOException("Could not prepare music storage.");
                long existing=0;File[] files=folder(c).listFiles();if(files!=null)for(File file:files)existing+=file.length();
                try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(part)){
                    if(in==null)throw new java.io.IOException("Could not open that audio file.");
                    byte[] bytes=new byte[32768];long size=0;int count;
                    while((count=in.read(bytes))!=-1){size+=count;if(size>MAX_FILE||existing+size>MAX_TOTAL)throw new java.io.IOException("Music storage limit reached (100 MB per file, 500 MB total).");out.write(bytes,0,count);}
                    if(size==0)throw new java.io.IOException("The selected audio file is empty.");
                    out.getFD().sync();
                }
                if(!part.renameTo(target))throw new java.io.IOException("Could not save the audio file.");
            }
            MediaPlayer check=new MediaPlayer();int duration;
            try{
                check.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                if(link)check.setDataSource(c,uri);else check.setDataSource(target.getAbsolutePath());
                check.prepare();duration=check.getDuration();
            }finally{check.release();}
            if(duration<1000)throw new java.io.IOException("Choose an audio recording at least one second long.");
            JSONObject row=new JSONObject().put("id",id).put("title",title(c,uri)).put("genre",genre)
                .put("mode",link?"link":"copy").put("duration",duration/1000);
            if(link)row.put("uri",uri.toString());
            list.put(row);Store.put(c,KEY,list);committed=true;
        }finally{
            part.delete();
            if(!committed){target.delete();if(grant)c.getContentResolver().releasePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        }
    }
    private static String title(Context c,Uri uri){
        String name=null;
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
        }catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name="My music";
        name=name.replaceAll("(?i)\\.(mp3|m4a|aac|wav|ogg|flac)$","").replaceAll("[\\p{Cntrl}]"," ").trim();
        return name.isEmpty()?"My music":name.substring(0,Math.min(80,name.length()));
    }
    public static void remove(Context c,String id)throws Exception{
        JSONArray old=saved(c),next=new JSONArray();JSONObject removed=null;
        for(int i=0;i<old.length();i++){JSONObject row=old.getJSONObject(i);if(id.equals(row.optString("id")))removed=row;else next.put(row);}
        if(removed==null)throw new java.io.FileNotFoundException("Personal sound was not found.");
        Store.put(c,KEY,next);
        if(id.equals(Store.p(c).getString("sound","")))Store.p(c).edit().putString("sound",SoundLibrary.DEFAULT).commit();
        if("copy".equals(removed.optString("mode"))){File saved=file(c,id);if(saved.exists()&&!saved.delete())throw new java.io.IOException("The sound was removed, but its private file could not be cleared.");}
        if("link".equals(removed.optString("mode"))){
            boolean stillUsed=false;for(int i=0;i<next.length();i++){JSONObject other=next.optJSONObject(i);if(other!=null&&removed.optString("uri").equals(other.optString("uri")))stillUsed=true;}
            if(!stillUsed)try{c.getContentResolver().releasePersistableUriPermission(Uri.parse(removed.getString("uri")),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        }
    }
    public static void changeGenre(Context c,String id,String genre)throws Exception{
        boolean known=false;for(String choice:GENRES)if(choice.equals(genre))known=true;
        if(!known)throw new IllegalArgumentException("Choose a music genre.");
        JSONArray list=saved(c);boolean found=false;
        for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i);if(id.equals(row.optString("id"))){row.put("genre",genre);found=true;break;}}
        if(!found)throw new java.io.FileNotFoundException("Personal sound was not found.");
        Store.put(c,KEY,list);
    }
    private CustomSounds(){}
}
