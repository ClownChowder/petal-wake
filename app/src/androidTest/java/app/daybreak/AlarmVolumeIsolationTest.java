package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import org.json.JSONObject;

/** Disposable-emulator check: the system-volume rise respects a later manual change. */
public final class AlarmVolumeIsolationTest extends Instrumentation {
    private void check(boolean value,String label)throws Exception {if(!value)throw new Exception(label);Bundle status=new Bundle();status.putString("passed",label);sendStatus(0,status);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();AudioManager audio=c.getSystemService(AudioManager.class);
        int original=audio.getStreamVolume(AudioManager.STREAM_ALARM);
        try{
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),min=audio.getStreamMinVolume(AudioManager.STREAM_ALARM);
            audio.setStreamVolume(AudioManager.STREAM_ALARM,max,0);
            Thread.sleep(200);
            int chosen=audio.getStreamVolume(AudioManager.STREAM_ALARM); // Some emulators cap the requested top step.
            Store.p(c).edit().putInt("alarmRampRestoreStep",max).commit();
            AlarmService.recoverAlarmVolume(c);
            check(audio.getStreamVolume(AudioManager.STREAM_ALARM)==chosen&&!Store.p(c).contains("alarmRampRestoreStep"),"Old crash marker cannot override current phone volume: actual="+audio.getStreamVolume(AudioManager.STREAM_ALARM)+" chosen="+chosen+" marker="+Store.p(c).contains("alarmRampRestoreStep"));
            Store.p(c).edit().putInt("fade",5).putInt("riseStartPercent",30).putBoolean("vibrate",false).putInt("fallbackMinutes",0).commit();
            JSONObject alarm=new JSONObject().put("id","test:volume-isolation").put("kind","test").put("title","Synthetic volume test").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            long deadline=System.currentTimeMillis()+30_000;
            while(!Store.object(c,"ringing").has("test:volume-isolation")&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            Thread.sleep(1200);
            check(Store.object(c,"ringing").has("test:volume-isolation"),"Synthetic alarm started");
            check(audio.getStreamVolume(AudioManager.STREAM_ALARM)<chosen,"Fade onset lowers the real phone alarm volume");
            int manual=min;
            audio.setStreamVolume(AudioManager.STREAM_ALARM,manual,0);
            Thread.sleep(5300);
            check(audio.getStreamVolume(AudioManager.STREAM_ALARM)==manual,"Manual phone volume change overrides the remaining rise");
            c.startService(new Intent(c,AlarmService.class).setAction("STOP_TEST"));
            Thread.sleep(500);
            check(audio.getStreamVolume(AudioManager.STREAM_ALARM)==manual,"Stopping test preserves the manual phone-volume choice");
            result.putString("summary","System-volume rise and manual override passed");finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP_TEST"));audio.setStreamVolume(AudioManager.STREAM_ALARM,original,0);Store.p(c).edit().remove("alarmRampRestoreStep").remove("alarmRampPreviousStep").remove("alarmRampLastStep").commit();}
    }
}
