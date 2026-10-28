package app.daybreak;

import dev.fourwags.rouse.R;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Repeated real preview playback and Activity teardown on a disposable emulator. */
public class LifecycleCleanupTest extends Instrumentation {
    private MainActivity activity;
    private WebView web;
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String message)throws Exception{
        if(!value)throw new Exception(message);
        checks++;Bundle status=new Bundle();status.putString("passed",message);sendStatus(0,status);
    }
    private Object field(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    private WebView find(View view){
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){WebView child=find(group.getChildAt(n));if(child!=null)return child;}}
        return null;
    }
    private String js(String code)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] result={null};
        runOnMainSync(()->web.evaluateJavascript(code,value->{result[0]=value;latch.countDown();}));
        if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("WebView did not respond.");
        return result[0];
    }
    private void awaitPlaying()throws Exception{
        long until=System.currentTimeMillis()+8000;
        while(System.currentTimeMillis()<until){MediaPlayer player=(MediaPlayer)field("preview");if(player!=null&&player.isPlaying())return;Thread.sleep(100);}
        throw new Exception("Preview did not begin; selected="+SoundLibrary.current(activity)+", playable="+SoundLibrary.playable(activity)+", player="+(field("preview")!=null)+", error="+Store.p(activity).getString("error",""));
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();
        activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->web=find(activity.findViewById(android.R.id.content)));
        long until=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<until&&!"true".equals(js("!!st")))Thread.sleep(100);
        check("true".equals(js("!!st")),"App loaded for repeated preview test");
        for(int n=0;n<12;n++){
            js("send('preview',{file:'gentle_01.m4a'})");awaitPlaying();
            js("send('stopPreview')");
            check(field("preview")==null&&field("previewShaper")==null,"Preview "+(n+1)+" released native audio resources");
        }
        js("send('preview',{file:'gentle_01.m4a'})");awaitPlaying();
        runOnMainSync(activity::finish);
        until=System.currentTimeMillis()+8000;
        while(System.currentTimeMillis()<until&&!activity.isDestroyed())Thread.sleep(100);
        check(activity.isDestroyed()&&field("preview")==null&&field("web")==null&&field("assetLoader")==null&&Boolean.FALSE.equals(field("receiverRegistered")),
            "Activity teardown releases preview, WebView, and receiver");
        result.putString("summary",checks+" lifecycle cleanup checks passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
