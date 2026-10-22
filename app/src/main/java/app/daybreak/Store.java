package app.daybreak;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class Store {
    public static SharedPreferences p(Context c){return c.getSharedPreferences("daybreak",Context.MODE_PRIVATE);}
    public static JSONArray array(Context c,String key){try{return new JSONArray(p(c).getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    public static JSONObject object(Context c,String key){try{return new JSONObject(p(c).getString(key,"{}"));}catch(Exception e){return new JSONObject();}}
    public static void put(Context c,String key,Object value){p(c).edit().putString(key,value.toString()).commit();}
    public static String cache(Context c){try{return new String(Files.readAllBytes(new java.io.File(c.getFilesDir(),"calendar.ics").toPath()),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    public static void cache(Context c,String data)throws Exception{
        android.util.AtomicFile f=new android.util.AtomicFile(new java.io.File(c.getFilesDir(),"calendar.ics"));
        java.io.FileOutputStream s=f.startWrite();
        try{s.write(data.getBytes(StandardCharsets.UTF_8));f.finishWrite(s);}catch(Exception e){f.failWrite(s);throw e;}
    }
    public static void error(Context c,String message){p(c).edit().putString("error",message==null?"":message).commit();}
}
