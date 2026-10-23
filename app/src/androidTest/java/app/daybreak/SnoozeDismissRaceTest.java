package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/** Exercises dismissal after Snooze has read the ringing batch but before it can schedule. */
public final class SnoozeDismissRaceTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Context c=getTargetContext();Bundle result=new Bundle();
        try{
            Store.put(c,"ringing",new JSONObject());Store.put(c,"snoozes",new JSONObject());
            Store.p(c).edit().putInt("snooze",1).putBoolean("vibrate",false).putInt("fade",0).commit();
            JSONObject alarm=new JSONObject().put("id","qa-dismiss-race").put("kind","manual")
                .put("title","Synthetic dismiss race").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            await(()->Store.object(c,"ringing").length()==1 && AlarmService.playbackPercent()>=0,5000,"ringing alarm");
            Thread.sleep(300);
            synchronized(Scheduler.class){
                c.startService(new Intent(c,AlarmService.class).setAction("SNOOZE"));
                await(()->alarmWorkerBlocked(),5000,"snooze worker blocked in Scheduler");
                c.startService(new Intent(c,AlarmService.class).setAction("STOP"));
                await(()->Store.object(c,"ringing").length()==0,5000,"dismissal");
            }
            Thread.sleep(1500);
            if(Store.object(c,"snoozes").length()!=0)throw new Exception("Dismissed alarm was snoozed");
            JSONObject scheduled=Store.object(c,"scheduled");
            for(java.util.Iterator<String> ids=scheduled.keys();ids.hasNext();)if(ids.next().startsWith("snooze:"))throw new Exception("Dismissed alarm remained scheduled");
            result.putString("summary","Dismissal cancelled snooze queued across Scheduler lock");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{
            try{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));}catch(RuntimeException ignored){}
            JSONObject set=Store.object(c,"snoozes");for(java.util.Iterator<String> ids=set.keys();ids.hasNext();)Scheduler.cancel(c,ids.next());
            Store.put(c,"snoozes",new JSONObject());Store.put(c,"ringing",new JSONObject());
        }
    }
    private boolean alarmWorkerBlocked(){
        for(Thread thread:Thread.getAllStackTraces().keySet()){
            if(thread.getState()!=Thread.State.BLOCKED)continue;
            for(StackTraceElement frame:thread.getStackTrace())if(frame.getClassName().equals("app.daybreak.AlarmService"))return true;
        }
        return false;
    }
    private interface Check{boolean ready()throws Exception;}
    private void await(Check check,long timeoutMs,String state)throws Exception{
        long until=System.currentTimeMillis()+timeoutMs;
        while(System.currentTimeMillis()<until){if(check.ready())return;Thread.sleep(20);}
        throw new Exception("Timed out waiting for "+state);
    }
}
