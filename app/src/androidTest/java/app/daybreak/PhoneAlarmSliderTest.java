package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.webkit.WebView;
import java.lang.reflect.Field;

/** Disposable-emulator check: dragging is read-only; committing the slider writes STREAM_ALARM. */
public final class PhoneAlarmSliderTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();AudioManager audio=c.getSystemService(AudioManager.class);int original=audio.getStreamVolume(AudioManager.STREAM_ALARM);MainActivity screen=null;
        try{
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),min=audio.getStreamMinVolume(AudioManager.STREAM_ALARM);
            int chosen=original<max?original+1:original-1;
            if(chosen<min||chosen>max)throw new Exception("Emulator alarm stream has no second volume step");
            screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field ready=MainActivity.class.getDeclaredField("pageReady"),webField=MainActivity.class.getDeclaredField("web");ready.setAccessible(true);webField.setAccessible(true);
            long deadline=System.currentTimeMillis()+30_000;
            while(!ready.getBoolean(screen)&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            if(!ready.getBoolean(screen))throw new Exception("Settings WebView did not become ready");
            WebView web=(WebView)webField.get(screen);
            runOnMainSync(()->web.evaluateJavascript("let slider=document.getElementById('phoneAlarmVolume');slider.value="+chosen+";slider.dispatchEvent(new Event('input',{bubbles:true}));",null));
            Thread.sleep(700);
            if(audio.getStreamVolume(AudioManager.STREAM_ALARM)!=original)throw new Exception("Dragging changed the phone volume before explicit commit");
            runOnMainSync(()->web.evaluateJavascript("document.getElementById('phoneAlarmVolume').dispatchEvent(new Event('change',{bubbles:true}));",null));
            deadline=System.currentTimeMillis()+15_000;
            while(audio.getStreamVolume(AudioManager.STREAM_ALARM)!=chosen&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            if(audio.getStreamVolume(AudioManager.STREAM_ALARM)!=chosen)throw new Exception("Explicit slider change did not update STREAM_ALARM");
            result.putString("summary","Slider input left STREAM_ALARM unchanged; explicit change updated it");finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{audio.setStreamVolume(AudioManager.STREAM_ALARM,original,0);if(screen!=null){MainActivity last=screen;runOnMainSync(last::finish);}}
    }
}
