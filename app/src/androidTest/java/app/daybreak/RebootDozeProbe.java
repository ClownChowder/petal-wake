package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/** Synthetic, isolated-emulator-only alarm delivery and permission probe. */
public final class RebootDozeProbe extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args==null?new Bundle():args;start();}
    @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();try{
        // Finish startup work before writing synthetic alarm state on this isolated emulator.
        DaybreakApp.IO.submit(()->{}).get();
        String phase=arguments.getString("phase","");String id=arguments.getString("id","qa-reboot");
        if("setup".equals(phase)){
            if(Store.object(c,"manual").length()!=0||Store.object(c,"ringing").length()!=0)throw new Exception("Requires clean isolated app data");
            if(!Scheduler.allowed(c))throw new Exception("Exact alarm permission denied on emulator");
            long at=System.currentTimeMillis()+Long.parseLong(arguments.getString("delay","90000"));
            JSONObject alarm=new JSONObject().put("id",id).put("kind","manual").put("at",at).put("title","Synthetic delivery probe");
            Store.put(c,"manual",new JSONObject().put(id,alarm));Sync.run(c,false);
            if(!Store.object(c,"scheduled").has(id))throw new Exception("Synthetic alarm was not scheduled");
            result.putString("summary","Scheduled "+id+" at "+at);result.putLong("at",at);
        }else if("refresh".equals(phase)||"forceRefresh".equals(phase)){
            Sync.run(c,false,"forceRefresh".equals(phase));
            result.putString("summary","Ran "+phase+" against synthetic stored alarms");
        }else if("verify".equals(phase)){
            if(!Store.object(c,"ringing").has(id))throw new Exception("Synthetic alarm did not ring; scheduled="+Store.object(c,"scheduled").has(id)+" inFlight="+Store.object(c,"inFlight").has(id));
            result.putString("summary",id+" rang through normal AlarmReceiver and AlarmService");
        }else if("receiver".equals(phase)){
            long at=System.currentTimeMillis()-60_000L;
            JSONObject alarm=new JSONObject().put("id",id).put("kind","manual").put("at",at).put("title","Synthetic boot receiver probe");
            Store.put(c,"manual",new JSONObject().put(id,alarm));Store.put(c,"scheduled",new JSONObject().put(id,alarm));
            Scheduler.recoverMissedAfterBoot(c);Sync.run(c,false);
            long until=System.currentTimeMillis()+10_000L;
            while(System.currentTimeMillis()<until){JSONObject recovered=Store.object(c,"scheduled").optJSONObject(id);if(recovered!=null&&recovered.optLong("at")>System.currentTimeMillis())break;Thread.sleep(50);}
            JSONObject recovered=Store.object(c,"scheduled").optJSONObject(id);
            if(recovered==null||recovered.optLong("at")<=System.currentTimeMillis())throw new Exception("Boot receiver did not rearm recent missed alarm");
            result.putString("summary","Boot receiver rearmed synthetic recent miss");
        }else if("late".equals(phase)||"older".equals(phase)){
            long at=System.currentTimeMillis()-("late".equals(phase)?60_000L:11*60_000L);
            JSONObject alarm=new JSONObject().put("id",id).put("kind","manual").put("at",at).put("title","Synthetic missed alarm");
            Store.put(c,"manual",new JSONObject().put(id,alarm));Store.put(c,"scheduled",new JSONObject().put(id,alarm));
            boolean older=Scheduler.recoverMissedAfterBoot(c);Sync.run(c,false);
            if("late".equals(phase)){
                JSONObject recovered=Store.object(c,"scheduled").optJSONObject(id);
                if(older||recovered==null||recovered.optLong("at")<=System.currentTimeMillis())throw new Exception("Recent missed alarm was not rearmed");
                result.putString("summary","Recently missed alarm rearmed after boot");
            }else{
                if(!older||Store.object(c,"scheduled").has(id))throw new Exception("Older missed alarm was silently rescheduled");
                Scheduler.failureNotice(c);
                if(Store.p(c).getString("error","").isEmpty())throw new Exception("Older missed alarm did not record a warning");
                result.putString("summary","Older missed alarm recorded attention warning");
            }
        }else if("ringSetup".equals(phase)){
            if(Store.object(c,"ringing").length()!=0)throw new Exception("Requires clean isolated ring state");
            JSONObject alarm=new JSONObject().put("id",id).put("kind","manual").put("at",System.currentTimeMillis()).put("title","Synthetic revoked-permission ring");
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            long until=System.currentTimeMillis()+5000;
            while(!Store.object(c,"ringing").has(id)&&System.currentTimeMillis()<until)Thread.sleep(50);
            if(!Store.object(c,"ringing").has(id))throw new Exception("Synthetic alarm did not start ringing");
            result.putString("summary","Synthetic alarm is ringing before permission revocation");
        }else if("snoozeDenied".equals(phase)){
            if(Scheduler.allowed(c)||!Store.object(c,"ringing").has(id))throw new Exception("Requires ringing alarm with exact access denied");
            c.startService(new Intent(c,AlarmService.class).setAction("SNOOZE"));Thread.sleep(1500);
            if(!Store.object(c,"ringing").has(id)||Store.object(c,"snoozes").length()!=0)throw new Exception("Failed snooze silenced ring or left unscheduled snooze");
            result.putString("summary","Denied exact-alarm snooze left current ring active and scheduled none");
            c.startService(new Intent(c,AlarmService.class).setAction("STOP"));
        }else if("permissions".equals(phase)){
            long at=System.currentTimeMillis()+3600000L;
            JSONObject alarm=new JSONObject().put("id",id).put("kind","manual").put("at",at).put("title","Synthetic permission probe");
            Store.put(c,"manual",new JSONObject().put(id,alarm));
            String issue=Readiness.issue(c);Readiness.update(c);
            String requested=arguments.getString("expected","");
            String expected="notifications".equals(requested)?"Alarm notifications are off.":"exact".equals(requested)?"Exact alarm access":requested;
            if(!issue.contains(expected)||!Store.p(c).getString("readinessIssue","").contains(expected))throw new Exception("Expected permission warning "+expected+", got "+issue);
            if("exact".equals(requested)){
                if(Scheduler.allowed(c))throw new Exception("Exact alarm access unexpectedly allowed");
                Sync.run(c,false);
                if(Store.object(c,"scheduled").has(id))throw new Exception("Permission-denied alarm appeared scheduled");
                if(!Store.object(c,"manual").has(id))throw new Exception("Permission-denied alarm was deleted");
            }
            result.putString("summary","Readiness warns: "+issue);
        }else throw new Exception("Unknown probe phase");
        finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}}
}
