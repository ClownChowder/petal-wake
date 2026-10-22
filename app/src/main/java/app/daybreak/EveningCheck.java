package app.daybreak;

import android.app.*;
import android.content.*;
import java.util.*;
import org.json.*;

/** Optional once-per-day preview of tomorrow's work alarm. */
public final class EveningCheck {
    public static final String CHANNEL="evening_check";private static final int NOTICE=202;
    public static void createChannel(Context c){NotificationChannel ch=new NotificationChannel(CHANNEL,"Tomorrow's alarms",NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("Optional evening preview of tomorrow's work alarm");ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);c.getSystemService(NotificationManager.class).createNotificationChannel(ch);}
    public static void update(Context c){
        var p=Store.p(c);NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!p.getBoolean("eveningCheck",false)||!p.getBoolean("enabled",false)){nm.cancel(NOTICE);return;}
        Calendar now=Calendar.getInstance();int chosen=p.getInt("eveningMinutes",19*60),minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
        if(minute<chosen||minute>=chosen+180)return;
        int today=now.get(Calendar.YEAR)*1000+now.get(Calendar.DAY_OF_YEAR);
        if(p.getInt("eveningSentDay",-1)==today)return;
        Calendar tomorrow=(Calendar)now.clone();tomorrow.add(Calendar.DAY_OF_YEAR,1);
        JSONObject first=null;JSONArray shifts=Store.array(c,"shifts");
        for(int i=0;i<shifts.length();i++){JSONObject shift=shifts.optJSONObject(i);if(shift==null)continue;Calendar date=Calendar.getInstance();date.setTimeInMillis(shift.optLong("start"));if(date.get(Calendar.YEAR)==tomorrow.get(Calendar.YEAR)&&date.get(Calendar.DAY_OF_YEAR)==tomorrow.get(Calendar.DAY_OF_YEAR)){first=shift;break;}}
        if(first==null)return;
        boolean scheduled=Store.object(c,"scheduled").has(first.optString("id"));
        String text=scheduled?"Wake at "+android.text.format.DateFormat.getTimeFormat(c).format(new Date(first.optLong("at")))+" for "+first.optString("title","work")+".":"A shift is on tomorrow's calendar, but its wake-up alarm is not set. Tap to check.";
        long last=p.getLong("lastSync",0);try{last=CalendarConfidence.snapshot(c).optLong("lastSuccess",last);}catch(Exception ignored){}if(last>0){long hours=Math.max(0,(System.currentTimeMillis()-last)/3_600_000L);text+=" Oldest calendar download was "+(hours<1?"less than an hour":"about "+hours+" hours")+" ago.";}
        Intent intent=new Intent(c,MainActivity.class).putExtra("page","calendar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(c,202,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_alarm_notification).setColor(Appearance.current(c).accent).setContentTitle(scheduled?"Tomorrow's work alarm":"Check tomorrow's work alarm").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try{nm.notify(NOTICE,notice);p.edit().putInt("eveningSentDay",today).commit();}catch(SecurityException ignored){}
    }
    private EveningCheck(){}
}
