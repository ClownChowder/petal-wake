package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Runs with synthetic alarms in an isolated emulator app installation. */
public final class SnoozeResponsivenessTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();CountDownLatch release=new CountDownLatch(1);
        try{
            Store.put(c,"ringing",new JSONObject());Store.put(c,"snoozes",new JSONObject());
            Store.p(c).edit().putInt("snooze",1).putBoolean("vibrate",false).putInt("fade",0).commit();
            JSONObject alarm=new JSONObject().put("id","qa-snooze-controls").put("kind","manual")
                .put("title","Synthetic snooze test").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            await(()->Store.object(c,"ringing").length()==1,5000,"ring started");
            CountDownLatch occupied=new CountDownLatch(1);
            DaybreakApp.IO.execute(()->{occupied.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            if(!occupied.await(5,TimeUnit.SECONDS))throw new Exception("Could not occupy calendar executor");
            c.startService(new Intent(c,AlarmService.class).setAction("SNOOZE"));
            c.startService(new Intent(c,AlarmService.class).setAction("SNOOZE"));
            boolean responsive=waitFor(()->Store.object(c,"snoozes").length()>0,2000);
            release.countDown();
            await(()->Store.object(c,"ringing").length()==0,8000,"ring stopped");
            DaybreakApp.IO.submit(()->{}).get(5,TimeUnit.SECONDS);
            int count=Store.object(c,"snoozes").length();
            if(!responsive||count!=1)throw new Exception("Snooze was delayed or duplicated: responsive="+responsive+", scheduled="+count);
            result.putString("summary","Snooze bypassed blocked calendar refresh and repeated taps scheduled one alarm");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{
            release.countDown();
            try{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));}catch(RuntimeException ignored){}
            JSONObject scheduled=Store.object(c,"snoozes");for(java.util.Iterator<String> ids=scheduled.keys();ids.hasNext();)Scheduler.cancel(c,ids.next());
            Store.put(c,"snoozes",new JSONObject());Store.put(c,"ringing",new JSONObject());
        }
    }
    private interface Check {boolean ready()throws Exception;}
    private boolean waitFor(Check check,long timeoutMs)throws Exception{
        long deadline=System.currentTimeMillis()+timeoutMs;
        while(System.currentTimeMillis()<deadline){if(check.ready())return true;Thread.sleep(25);}return check.ready();
    }
    private void await(Check check,long timeoutMs,String state)throws Exception{if(!waitFor(check,timeoutMs))throw new Exception("Timed out waiting for "+state+"; ringing="+Store.object(getTargetContext(),"ringing").length()+", snoozes="+Store.object(getTargetContext(),"snoozes").length()+", error="+Store.p(getTargetContext()).getString("error",""));}
}
