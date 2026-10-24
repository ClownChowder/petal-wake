package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises the real unattended alarm timer and switch to the backup recording. */
public class BackupAlertTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();try{
        Store.p(c).edit().putInt("volume",100).putInt("fade",0).putInt("fallbackMinutes",1).putBoolean("vibrate",false).putString("sound",SoundLibrary.DEFAULT).commit();
        android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);int selectedStep=audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
        AlarmHistory.clear(c);
        JSONObject alarm=new JSONObject().put("id","qa-backup-alert").put("title","Backup alert test").put("kind","manual").put("at",System.currentTimeMillis());
        c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
        long deadline=System.currentTimeMillis()+75_000L;boolean switched=false;
        while(System.currentTimeMillis()<deadline){
            JSONArray rows=AlarmHistory.entries(c);
            for(int i=0;i<rows.length();i++)if("backup alert".equals(rows.optJSONObject(i).optString("event")))switched=true;
            if(switched)break;Thread.sleep(250);
        }
        if(!switched||AlarmService.playbackPercent()<0||audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)!=selectedStep)throw new Exception("Backup alert did not switch to the selected phone alarm level within one minute.");
        result.putString("summary","Backup alert switched to the standard recording at the selected phone alarm level");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));}}
}
