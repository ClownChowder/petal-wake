package app.daybreak;

import dev.fourwags.rouse.R;
import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

public final class Scheduler {
    private static final long DELIVERY_WATCHDOG_MS=45_000L;
    private static final long BOOT_RECOVERY_GRACE_MS=10*60_000L;
    public static boolean allowed(Context c){return android.os.Build.VERSION.SDK_INT<31 || ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    private static PendingIntent operation(Context c,String id,int flags){
        Intent i=new Intent(c,AlarmReceiver.class).setAction("app.daybreak.RING").setData(Uri.parse("daybreak://alarm/"+Uri.encode(id))).putExtra("id",id);
        return PendingIntent.getBroadcast(c,0,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent watchdog(Context c,String id,int flags){
        Intent i=new Intent(c,RecoveryReceiver.class).setAction("app.daybreak.RECOVER").setData(Uri.parse("daybreak://recover/"+Uri.encode(id))).putExtra("id",id);
        return PendingIntent.getBroadcast(c,0,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void cancel(Context c,String id){
        PendingIntent pi=operation(c,id,PendingIntent.FLAG_NO_CREATE);
        if(pi!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}
    }
    public static synchronized void reconcile(Context c,JSONObject desired,boolean forceRearm)throws Exception {
        // Sync may have assembled a snapshot before a snooze, personal alarm, or
        // test was cancelled on another executor. Recheck at the install boundary.
        JSONObject live=new JSONObject();
        for(Iterator<String> ids=desired.keys();ids.hasNext();){
            String id=ids.next();JSONObject alarm=desired.optJSONObject(id);if(alarm==null)continue;
            String kind=alarm.optString("kind"),key="snooze".equals(kind)?"snoozes":"manual".equals(kind)?"manual":"test".equals(kind)?"testAlarms":null;
            if(key!=null&&!Store.object(c,key).has(id))continue;
            if("shift".equals(kind)&&(!Store.p(c).getBoolean("enabled",false)||Store.object(c,"skipped").optBoolean(id)))continue;
            live.put(id,alarm);
        }
        desired=live;
        AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);JSONObject old=Store.object(c,"scheduled");
        JSONObject installed=new JSONObject();
        for(Iterator<String> it=old.keys();it.hasNext();) {String id=it.next();if(!desired.has(id)){
            JSONObject pending=old.optJSONObject(id);
            if(deliveryInProgress(c,id,pending))installed.put(id,pending);else{cancel(c,id);AlarmHistory.record(c,"removed",pending);}
        }}
        if(allowed(c))for(Iterator<String> it=desired.keys();it.hasNext();) {
            String id=it.next();JSONObject a=desired.getJSONObject(id);
            JSONObject prior=old.optJSONObject(id);
            if(!forceRearm&&prior!=null&&prior.optLong("at")==a.optLong("at")
                    &&operation(c,id,PendingIntent.FLAG_NO_CREATE)!=null){
                installed.put(id,a);continue;
            }
            Intent show=new Intent(c,MainActivity.class).setData(Uri.parse("daybreak://show/"+Uri.encode(id)));
            PendingIntent display=PendingIntent.getActivity(c,0,show,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            try {m.setAlarmClock(new AlarmManager.AlarmClockInfo(a.getLong("at"),display),operation(c,id,PendingIntent.FLAG_UPDATE_CURRENT));installed.put(id,a);if(prior==null||prior.optLong("at")!=a.optLong("at"))AlarmHistory.record(c,"scheduled",a);}
            catch(SecurityException e){Store.error(c,"Allow Alarms & reminders in Android settings.");break;}
        }
        Store.put(c,"scheduled",installed);
    }
    private static boolean deliveryInProgress(Context c,String id,JSONObject alarm){
        if(alarm==null)return false;long age=System.currentTimeMillis()-alarm.optLong("at");if(age<0||age>30_000)return false;
        String kind=alarm.optString("kind");
        if("manual".equals(kind))return Store.object(c,"manual").has(id);
        if("test".equals(kind))return Store.object(c,"testAlarms").has(id);
        if("snooze".equals(kind))return Store.object(c,"snoozes").has(id);
        if("shift".equals(kind)&&Store.p(c).getBoolean("enabled",false)&&!Store.object(c,"skipped").optBoolean(id)){
            var shifts=Store.array(c,"shifts");for(int n=0;n<shifts.length();n++){JSONObject shift=shifts.optJSONObject(n);if(shift!=null&&id.equals(shift.optString("id"))&&alarm.optLong("at")==shift.optLong("at"))return true;}
        }
        return false;
    }
    public static void skip(Context c,String id)throws Exception {
        JSONObject skips=Store.object(c,"skipped");skips.put(id,true);Store.put(c,"skipped",skips);cancel(c,id);Sync.run(c,false);
    }
    public static synchronized JSONObject claim(Context c,String id)throws Exception {
        JSONObject all=Store.object(c,"scheduled");JSONObject alarm=all.optJSONObject(id);if(alarm==null)return null;
        long now=System.currentTimeMillis();if(alarm.optLong("at")>now+3000)return null;
        JSONObject inFlight=Store.object(c,"inFlight");inFlight.put(id,alarm);Store.put(c,"inFlight",inFlight);
        try{((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,now+DELIVERY_WATCHDOG_MS,watchdog(c,id,PendingIntent.FLAG_UPDATE_CURRENT));}
        catch(RuntimeException failure){inFlight.remove(id);Store.put(c,"inFlight",inFlight);throw failure;}
        all.remove(id);Store.put(c,"scheduled",all);
        if(alarm.optString("kind").equals("shift")){JSONObject skipped=Store.object(c,"skipped");skipped.put(id,true);Store.put(c,"skipped",skipped);}
        for(String key:new String[]{"manual","testAlarms","snoozes"}){JSONObject set=Store.object(c,key);set.remove(id);Store.put(c,key,set);}
        return alarm;
    }
    public static synchronized void acknowledge(Context c,String id)throws Exception {
        JSONObject pending=Store.object(c,"inFlight");if(!pending.has(id))return;
        pending.remove(id);Store.put(c,"inFlight",pending);
        PendingIntent alarm=watchdog(c,id,PendingIntent.FLAG_NO_CREATE);if(alarm!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm);alarm.cancel();}
    }
    public static synchronized void retryFailedDelivery(Context c,String id){
        if(id==null)return;
        JSONObject pending=Store.object(c,"inFlight"),original=pending.optJSONObject(id);if(original==null)return;
        try{
            PendingIntent check=watchdog(c,id,PendingIntent.FLAG_NO_CREATE);if(check!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(check);check.cancel();}
            pending.remove(id);Store.put(c,"inFlight",pending);
            if(original.optInt("retryCount",0)>=1||!allowed(c)){failureNotice(c);return;}
            String nextId="retry:"+UUID.randomUUID();long at=System.currentTimeMillis()+30_000L;
            JSONObject retry=new JSONObject(original.toString()).put("id",nextId).put("at",at).put("kind","manual").put("retryCount",1);
            JSONObject manual=Store.object(c,"manual");manual.put(nextId,retry);Store.put(c,"manual",manual);
            JSONObject scheduled=Store.object(c,"scheduled");scheduled.put(nextId,retry);Store.put(c,"scheduled",scheduled);
            Intent open=new Intent(c,MainActivity.class).setData(Uri.parse("daybreak://show/"+Uri.encode(nextId)));
            PendingIntent display=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(new AlarmManager.AlarmClockInfo(at,display),operation(c,nextId,PendingIntent.FLAG_UPDATE_CURRENT));
            AlarmHistory.record(c,"delivery retry",retry);
        }catch(Exception failure){failureNotice(c);}
    }
    public static synchronized void recoverOutstanding(Context c){
        JSONObject pending=Store.object(c,"inFlight");long now=System.currentTimeMillis();
        for(Iterator<String> keys=pending.keys();keys.hasNext();){String id=keys.next();JSONObject alarm=pending.optJSONObject(id);if(alarm!=null&&now-alarm.optLong("at")<10*60_000L)retryFailedDelivery(c,id);else{try{acknowledge(c,id);}catch(Exception ignored){}failureNotice(c);}}
    }
    /** Re-arm alarms missed while the phone was rebooting. Returns true for an older missed alarm. */
    public static synchronized boolean recoverMissedAfterBoot(Context c)throws Exception {
        long now=System.currentTimeMillis();boolean olderMiss=false,changed=false;
        JSONObject scheduled=Store.object(c,"scheduled"),manual=Store.object(c,"manual"),snoozes=Store.object(c,"snoozes");
        for(Iterator<String> ids=scheduled.keys();ids.hasNext();){
            String id=ids.next();JSONObject alarm=scheduled.optJSONObject(id);if(alarm==null||Store.object(c,"inFlight").has(id)||Store.object(c,"ringing").has(id))continue;
            long age=now-alarm.optLong("at");if(age<0)continue;
            String kind=alarm.optString("kind");
            boolean relevant="manual".equals(kind)?manual.has(id):"snooze".equals(kind)?snoozes.has(id):"shift".equals(kind)&&Store.p(c).getBoolean("enabled",false)&&!Store.object(c,"skipped").optBoolean(id);
            if(!relevant)continue;
            if(age>BOOT_RECOVERY_GRACE_MS){olderMiss=true;continue;}
            JSONObject late=new JSONObject(alarm.toString()).put("kind","manual").put("at",now+30_000L).put("originalAt",alarm.optLong("at"));
            manual.put(id,late);if("snooze".equals(kind))snoozes.remove(id);changed=true;
            try{AlarmHistory.record(c,"late boot recovery",alarm);}catch(Exception ignored){}
        }
        if(changed){Store.put(c,"manual",manual);Store.put(c,"snoozes",snoozes);}
        return olderMiss;
    }
    static void failureNotice(Context c){
        Store.error(c,"An alarm could not start. Check Rouse and Android alarm permissions.");
        try{
            NotificationManager manager=c.getSystemService(NotificationManager.class);
            PendingIntent open=PendingIntent.getActivity(c,204,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            manager.notify(204,new Notification.Builder(c,"alarm_readiness").setSmallIcon(R.drawable.ic_alarm_notification).setContentTitle("Rouse alarm needs attention")
                .setContentText("An alarm could not start. Open Rouse to check it.").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
        }catch(RuntimeException ignored){}
    }
    public static synchronized String snooze(Context c,JSONObject alarm,java.util.function.BooleanSupplier cancelled)throws Exception {
        if(cancelled.getAsBoolean())return null;
        if(!allowed(c))throw new SecurityException("Exact alarm access is off; the alarm keeps ringing.");
        int minutes=Store.p(c).getInt("snooze",9);
        if(minutes<1||minutes>60)throw new IllegalStateException("Invalid snooze duration; the alarm keeps ringing.");
        String id="snooze:"+UUID.randomUUID();long at=System.currentTimeMillis()+minutes*60_000L;
        JSONObject a=new JSONObject(alarm.toString()).put("id",id).put("at",at).put("kind","snooze");
        JSONObject set=Store.object(c,"snoozes");set.put(id,a);Store.put(c,"snoozes",set);
        if(cancelled.getAsBoolean()){set.remove(id);Store.put(c,"snoozes",set);return null;}
        try{
            Sync.run(c,false);
            if(!allowed(c)||!Store.object(c,"scheduled").has(id))throw new IllegalStateException("Snooze could not be scheduled; the alarm keeps ringing.");
        }catch(Exception failure){
            JSONObject pending=Store.object(c,"snoozes");pending.remove(id);Store.put(c,"snoozes",pending);
            cancel(c,id);JSONObject installed=Store.object(c,"scheduled");installed.remove(id);Store.put(c,"scheduled",installed);
            throw failure;
        }
        if(cancelled.getAsBoolean()){cancelSnoozes(c,java.util.Collections.singletonList(id));return null;}
        try{AlarmHistory.record(c,"snoozed",alarm);}catch(Exception ignored){}
        return id;
    }
    public static synchronized void cancelSnoozes(Context c,java.util.List<String> ids)throws Exception {
        if(ids.isEmpty())return;
        JSONObject set=Store.object(c,"snoozes");
        for(String id:ids)set.remove(id);
        Store.put(c,"snoozes",set);
        for(String id:ids)cancel(c,id);
        JSONObject scheduled=Store.object(c,"scheduled");for(String id:ids)scheduled.remove(id);Store.put(c,"scheduled",scheduled);
        Sync.run(c,false);
    }
}
