package app.daybreak;
import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

public final class Scheduler {
    public static boolean allowed(Context c){return android.os.Build.VERSION.SDK_INT<31 || ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    private static PendingIntent operation(Context c,String id,int flags){
        Intent i=new Intent(c,AlarmReceiver.class).setAction("app.daybreak.RING").setData(Uri.parse("daybreak://alarm/"+Uri.encode(id))).putExtra("id",id);
        return PendingIntent.getBroadcast(c,0,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void cancel(Context c,String id){
        PendingIntent pi=operation(c,id,PendingIntent.FLAG_NO_CREATE);
        if(pi!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}
    }
    public static synchronized void reconcile(Context c,JSONObject desired)throws Exception {
        AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);JSONObject old=Store.object(c,"scheduled");
        JSONObject installed=new JSONObject();
        for(Iterator<String> it=old.keys();it.hasNext();) {String id=it.next();if(!desired.has(id)){
            JSONObject pending=old.optJSONObject(id);
            if(deliveryInProgress(c,id,pending))installed.put(id,pending);else cancel(c,id);
        }}
        if(allowed(c))for(Iterator<String> it=desired.keys();it.hasNext();) {
            String id=it.next();JSONObject a=desired.getJSONObject(id);
            Intent show=new Intent(c,MainActivity.class).setData(Uri.parse("daybreak://show/"+Uri.encode(id)));
            PendingIntent display=PendingIntent.getActivity(c,0,show,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            try {m.setAlarmClock(new AlarmManager.AlarmClockInfo(a.getLong("at"),display),operation(c,id,PendingIntent.FLAG_UPDATE_CURRENT));installed.put(id,a);}
            catch(SecurityException e){Store.error(c,"Allow Alarms & reminders in Android settings.");break;}
        }
        Store.put(c,"scheduled",installed);
    }
    private static boolean deliveryInProgress(Context c,String id,JSONObject alarm){
        if(alarm==null)return false;long age=System.currentTimeMillis()-alarm.optLong("at");if(age<0||age>30_000)return false;
        String kind=alarm.optString("kind");
        if("manual".equals(kind))return Store.object(c,"manual").has(id);
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
        all.remove(id);Store.put(c,"scheduled",all);
        if(alarm.optString("kind").equals("shift")){JSONObject skipped=Store.object(c,"skipped");skipped.put(id,true);Store.put(c,"skipped",skipped);}
        for(String key:new String[]{"manual","snoozes"}){JSONObject set=Store.object(c,key);set.remove(id);Store.put(c,key,set);}
        return alarm;
    }
    public static synchronized void snooze(Context c,JSONObject alarm)throws Exception {
        String id="snooze:"+UUID.randomUUID();long at=System.currentTimeMillis()+Store.p(c).getInt("snooze",9)*60_000L;
        JSONObject a=new JSONObject(alarm.toString()).put("id",id).put("at",at).put("kind","snooze");
        JSONObject set=Store.object(c,"snoozes");set.put(id,a);Store.put(c,"snoozes",set);Sync.run(c,false);
    }
}
