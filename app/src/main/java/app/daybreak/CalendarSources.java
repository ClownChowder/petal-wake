package app.daybreak;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Additional calendars. The original calendar remains in its existing storage slot. */
public final class CalendarSources {
    private static final String KEY="extraCalendars";
    public static JSONArray extras(Context c){return Store.array(c,KEY);}
    public static String link(Context c,String id){JSONArray list=extras(c);for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))return item.optString("url");}return "";}
    public static boolean hasPrimary(Context c){return !Store.p(c).getString("source","").isEmpty();}
    public static String data(Context c,String id){
        File path=file(c,id);if(!path.exists())return "";
        try{String saved=new String(Files.readAllBytes(path.toPath()),StandardCharsets.UTF_8);String plain=PrivateStorage.open(saved);if(!PrivateStorage.encrypted(saved))data(c,id,plain);return plain;}
        catch(IOException e){throw new IllegalStateException("Saved calendar could not be read. Existing alarms were kept.",e);}
        catch(Exception e){throw new IllegalStateException("Saved calendar could not be secured. Existing alarms were kept.",e);}
    }
    private static File file(Context c,String id){if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid calendar ID.");return new File(c.getFilesDir(),"calendar-"+id+".ics");}
    public static void data(Context c,String id,String value)throws Exception{
        AtomicFile target=new AtomicFile(file(c,id));FileOutputStream stream=target.startWrite();
        try{stream.write(PrivateStorage.seal(value).getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception e){target.failWrite(stream);throw e;}
    }
    public static void add(Context c,String name,String url)throws Exception{
        if(!hasPrimary(c))throw new IOException("Connect your first work calendar before adding another.");
        JSONArray list=extras(c);if(list.length()>=5)throw new IOException("You can connect up to six calendars.");
        String normalized=url.trim().replaceFirst("^webcal:","https:");String body=Sync.download(normalized);
        CalendarReader.read(body,System.currentTimeMillis()-Sync.DAY,System.currentTimeMillis()+60*Sync.DAY);
        String id=UUID.randomUUID().toString();data(c,id,body);
        JSONObject entry=new JSONObject().put("id",id).put("name",cleanName(name)).put("url",normalized).put("lastSync",System.currentTimeMillis()).put("lead",Store.p(c).getInt("lead",90)).put("filter","").put("allDayEnabled",Store.p(c).getBoolean("allDayEnabled",false)).put("allDayMinutes",Store.p(c).getInt("allDayMinutes",480)).put("error","");
        list.put(entry);Store.put(c,KEY,list);Sync.run(c,false);
    }
    public static String cleanName(String name){String value=name==null?"":name.trim();return value.isEmpty()?"Work calendar":value.substring(0,Math.min(60,value.length()));}
    public static void update(Context c,String id,String name,String filter,int lead,boolean allDayEnabled,int allDayMinutes)throws Exception{
        if(lead<0||lead>1440||allDayMinutes<0||allDayMinutes>1439||filter.length()>120)throw new IOException("Calendar settings are outside the allowed range.");
        JSONArray list=extras(c);boolean found=false;
        for(int i=0;i<list.length();i++){JSONObject entry=list.getJSONObject(i);if(id.equals(entry.optString("id"))){entry.put("name",cleanName(name)).put("filter",filter.trim()).put("lead",lead).put("allDayEnabled",allDayEnabled).put("allDayMinutes",allDayMinutes);found=true;break;}}
        if(!found)throw new IOException("Calendar was not found.");Store.put(c,KEY,list);Sync.run(c,false);
    }
    public static void remove(Context c,String id)throws Exception{
        JSONArray old=extras(c),next=new JSONArray();boolean found=false;
        for(int i=0;i<old.length();i++){JSONObject entry=old.getJSONObject(i);if(id.equals(entry.optString("id")))found=true;else next.put(entry);}
        if(!found)throw new IOException("Calendar was not found.");Store.put(c,KEY,next);file(c,id).delete();Sync.run(c,false);
    }
    public static void clear(Context c){JSONArray list=extras(c);for(int i=0;i<list.length();i++){JSONObject entry=list.optJSONObject(i);if(entry!=null)try{file(c,entry.optString("id")).delete();}catch(Exception ignored){}}Store.put(c,KEY,new JSONArray());}
    private CalendarSources(){}
}
