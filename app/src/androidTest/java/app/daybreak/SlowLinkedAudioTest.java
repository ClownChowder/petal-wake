package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A stalled linked file must not block the UI or leave the alarm silent. */
public final class SlowLinkedAudioTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private String linkedId(Context c){JSONArray rows=Store.array(c,"customSounds");for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&SlowAudioProvider.URI.toString().equals(row.optString("uri")))return row.optString("id");}return null;}
    private boolean fallbackRecorded(Context c){JSONArray rows=AlarmHistory.entries(c);for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&"audio fallback".equals(row.optString("event")))return true;}return false;}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Activity screen=null;String id=null;
        String previous=Store.p(c).getString("sound",SoundLibrary.DEFAULT);boolean oldVibration=Store.p(c).getBoolean("vibrate",true);
        try{
            screen=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity current=screen;
            runOnMainSync(()->current.startActivityForResult(new Intent().setClassName("app.daybreak.test","app.daybreak.SlowAudioPicker"),31));
            long deadline=System.currentTimeMillis()+10_000;while((id=linkedId(c))==null&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            if(id==null)throw new Exception("Synthetic document grant was not imported.");
            c.getContentResolver().call(SlowAudioProvider.URI,"slow",null,null);
            int before=c.getContentResolver().call(SlowAudioProvider.URI,"count",null,null).getInt("opens");
            long began=System.currentTimeMillis();runOnMainSync(()->{try{CustomSounds.forUi(c);SoundLibrary.playable(c);}catch(Exception error){throw new RuntimeException(error);}});
            long elapsed=System.currentTimeMillis()-began;
            int after=c.getContentResolver().call(SlowAudioProvider.URI,"count",null,null).getInt("opens");
            if(elapsed>1500||after!=before)throw new Exception("UI availability opened the slow audio provider: "+elapsed+" ms, "+(after-before)+" opens");
            Method preview=MainActivity.class.getDeclaredMethod("preview",String.class);preview.setAccessible(true);
            String previewId=id;
            began=System.currentTimeMillis();runOnMainSync(()->{try{preview.invoke(current,previewId);}catch(Exception error){throw new RuntimeException(error);}});
            if(System.currentTimeMillis()-began>1500)throw new Exception("Preview blocked the main thread while opening linked audio.");
            Thread.sleep(6_000);
            Method stop=MainActivity.class.getDeclaredMethod("stopPreview");stop.setAccessible(true);
            runOnMainSync(()->{try{stop.invoke(current);}catch(Exception error){throw new RuntimeException(error);}});
            Store.p(c).edit().putString("sound",id).putBoolean("vibrate",false).putInt("fade",0).commit();AlarmHistory.clear(c);
            JSONObject alarm=new JSONObject().put("id","qa-slow-link").put("title","Synthetic slow link").put("kind","manual").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            CountDownLatch mainResponsive=new CountDownLatch(1);new Handler(Looper.getMainLooper()).post(mainResponsive::countDown);
            if(!mainResponsive.await(2,TimeUnit.SECONDS))throw new Exception("Main thread stalled while linked audio was opening.");
            deadline=System.currentTimeMillis()+8_000;while(!fallbackRecorded(c)&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            if(!fallbackRecorded(c)||AlarmService.playbackPercent()<0)throw new Exception("Bundled backup did not sound before slow provider returned.");
            c.startService(new Intent(c,AlarmService.class).setAction("STOP"));
            Thread.sleep(6_000); // The provider returns after the alarm has been dismissed.
            if(Store.object(c,"current").length()!=0||AlarmService.playbackPercent()>=0)throw new Exception("Late provider completion restarted a dismissed alarm.");
            result.putString("summary","Slow linked provider did not block library or preview; bundled alarm sounded within timeout; late completion stayed dismissed");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{
            c.startService(new Intent(c,AlarmService.class).setAction("STOP"));
            Store.p(c).edit().putString("sound",previous).putBoolean("vibrate",oldVibration).commit();
            c.getContentResolver().call(SlowAudioProvider.URI,"fast",null,null);
            if(id!=null)try{CustomSounds.remove(c,id);}catch(Exception ignored){}
            if(screen!=null)runOnMainSync(screen::finish);
        }
    }
}
