package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks shift-change detection, private settings transfer, and bounded history. */
public class NextReleaseTest extends Instrumentation {
    private int checks;
    private void check(boolean condition,String message)throws Exception{if(!condition)throw new Exception(message);checks++;Bundle status=new Bundle();status.putString("passed",message);sendStatus(0,status);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();Store.p(c).edit().clear().putInt("playbackRevision",1).commit();
        long now=System.currentTimeMillis(),tomorrow=now+86_400_000L;
        JSONArray old=new JSONArray().put(new JSONObject().put("id","a").put("at",tomorrow)).put(new JSONObject().put("id","b").put("at",tomorrow+1000)).put(new JSONObject().put("id","s").put("at",tomorrow).put("skipped",true));
        JSONArray updated=new JSONArray().put(new JSONObject().put("id","a").put("at",tomorrow+60_000)).put(new JSONObject().put("id","c").put("at",tomorrow+120_000)).put(new JSONObject().put("id","s").put("at",tomorrow+120_000).put("skipped",true));
        JSONArray changes=ShiftChanges.diff(old,updated,now);
        check(changes.length()==3,"Moved, canceled, and added wake-ups are identified; skipped shifts are ignored");
        check(ShiftChanges.diff(updated,updated,now).length()==0,"Unchanged feeds do not create shift notices");
        String privateUrl="https://secret.example/work?token=private-calendar-key";
        Store.p(c).edit().putString("url",privateUrl).putString("source","Shared calendar link").putBoolean("enabled",false).putInt("volume",80).commit();
        Store.put(c,"manual",new JSONObject().put("private","alarm"));AlarmHistory.record(c,"scheduled",new JSONObject().put("kind","shift").put("title","Sensitive work title").put("at",tomorrow));
        String exported=SettingsTransfer.exportJson(c);
        check(!exported.contains("secret.example")&&!exported.contains("private-calendar-key")&&!exported.contains("Sensitive work title")&&!exported.contains("manual"),"Settings file excludes calendar URLs, event titles, alarms, and history");
        JSONObject backup=new JSONObject(exported);backup.getJSONObject("settings").put("volume",65).put("fallbackMinutes",10);
        SettingsTransfer.importJson(c,backup.toString());
        check(Store.p(c).getInt("volume",0)==65&&Store.p(c).getInt("fallbackMinutes",0)==10,"Playback and backup-alert settings restore");
        check(privateUrl.equals(Store.p(c).getString("url","")),"Settings restore preserves the private calendar link already on the phone");
        backup.getJSONObject("settings").put("volume",999);boolean rejected=false;try{SettingsTransfer.importJson(c,backup.toString());}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected&&Store.p(c).getInt("volume",0)==65,"Invalid settings are rejected before saved preferences change");
        for(int i=0;i<120;i++)AlarmHistory.record(c,"rang",new JSONObject().put("kind","manual").put("title","Private title"));
        check(AlarmHistory.entries(c).length()==100&&!AlarmHistory.entries(c).toString().contains("Private title"),"History is bounded and excludes event titles");
        result.putString("summary",checks+" next-release checks passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
