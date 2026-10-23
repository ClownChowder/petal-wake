package app.daybreak;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class Store {
    public static SharedPreferences p(Context c){return c.getSharedPreferences("daybreak",Context.MODE_PRIVATE);}
    public static JSONArray array(Context c,String key){
        String saved=p(c).getString(key,"[]");
        if("extraCalendars".equals(key)||"customSounds".equals(key)){
            String plain=PrivateStorage.open(saved);
            try{JSONArray result=new JSONArray(plain);if(!PrivateStorage.encrypted(saved))put(c,key,result);return result;}
            catch(JSONException e){throw new IllegalStateException("Saved private settings could not be read. Existing alarms were kept.",e);}
        }
        try{return new JSONArray(saved);}catch(Exception e){return new JSONArray();}
    }
    public static JSONObject object(Context c,String key){try{return new JSONObject(p(c).getString(key,"{}"));}catch(Exception e){return new JSONObject();}}
    public static void put(Context c,String key,Object value){String saved=value.toString();if("extraCalendars".equals(key)||"customSounds".equals(key))saved=PrivateStorage.seal(saved);if(!p(c).edit().putString(key,saved).commit())throw new IllegalStateException("Could not save private settings.");}
    public static String url(Context c){
        SharedPreferences prefs=p(c);String secure=prefs.getString("secureUrl","");
        if(!secure.isEmpty())return PrivateStorage.open(secure);
        String legacy=prefs.getString("url","");
        if(!legacy.isEmpty()){setUrl(c,legacy);return legacy;}
        return "";
    }
    public static void setUrl(Context c,String value){
        SharedPreferences.Editor editor=p(c).edit().remove("url");
        if(value==null||value.isEmpty())editor.remove("secureUrl");else editor.putString("secureUrl",PrivateStorage.seal(value));
        if(!editor.commit())throw new IllegalStateException("Could not save the calendar link.");
    }
    public static String cache(Context c){
        java.io.File file=new java.io.File(c.getFilesDir(),"calendar.ics");if(!file.exists())return "";
        try{String saved=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);String plain=PrivateStorage.open(saved);if(!PrivateStorage.encrypted(saved))cache(c,plain);return plain;}
        catch(Exception e){throw new IllegalStateException("Saved calendar could not be read. Existing alarms were kept.",e);}
    }
    public static void cache(Context c,String data)throws Exception{
        android.util.AtomicFile f=new android.util.AtomicFile(new java.io.File(c.getFilesDir(),"calendar.ics"));
        java.io.FileOutputStream s=f.startWrite();
        try{s.write(PrivateStorage.seal(data).getBytes(StandardCharsets.UTF_8));f.finishWrite(s);}catch(Exception e){f.failWrite(s);throw e;}
    }
    public static String safeError(String message){return message==null?"":message.replaceAll("(?i)(?:https?|webcal)://[^\\s]+","[private calendar link]");}
    public static void error(Context c,String message){p(c).edit().putString("error",safeError(message)).commit();}
}
