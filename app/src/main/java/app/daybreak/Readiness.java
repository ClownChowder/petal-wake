package app.daybreak;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import org.json.JSONObject;
import java.util.Iterator;

/** Alerts when a configured alarm loses an essential phone setting. */
public final class Readiness {
    private static final String CHANNEL="alarm_readiness";private static final int NOTICE=203;
    public static void createChannel(Context c){NotificationChannel ch=new NotificationChannel(CHANNEL,"Alarm readiness",NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("Warnings when phone settings prevent alarms");ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);c.getSystemService(NotificationManager.class).createNotificationChannel(ch);}
    private static boolean hasFuturePersonal(Context c){long now=System.currentTimeMillis();for(String key:new String[]{"manual","snoozes"}){JSONObject alarms=Store.object(c,key);for(Iterator<String> ids=alarms.keys();ids.hasNext();){JSONObject alarm=alarms.optJSONObject(ids.next());if(alarm!=null&&alarm.optLong("at")>now)return true;}}return false;}
    static boolean hasRelevantAlarms(Context c){return Store.p(c).getBoolean("enabled",false)&&CalendarSources.hasPrimary(c)||hasFuturePersonal(c);}
    public static String issue(Context c){
        if(!hasRelevantAlarms(c))return "";
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!Scheduler.allowed(c))return "Exact alarm access is off. Alarms may not ring on time.";
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED||!nm.areNotificationsEnabled())return "Alarm notifications are off. Allow them before relying on alarms.";
        NotificationChannel alarms=nm.getNotificationChannel(AlarmService.CHANNEL);if(alarms!=null&&alarms.getImportance()<NotificationManager.IMPORTANCE_HIGH)return "The wake-up alarm channel is muted or lowered.";
        if(Build.VERSION.SDK_INT>=34&&!nm.canUseFullScreenIntent())return "Lock-screen alarm display is off. Open Petal Wake settings to allow it.";
        AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);if(audio.getStreamVolume(AudioManager.STREAM_ALARM)==0)return "Phone alarm volume is muted.";
        return "";
    }
    public static void update(Context c){
        createChannel(c);String issue=issue(c);var p=Store.p(c);NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(issue.isEmpty()){nm.cancel(NOTICE);p.edit().remove("readinessIssue").commit();return;}
        if(issue.equals(p.getString("readinessIssue","")))return;
        PendingIntent open=PendingIntent.getActivity(c,203,new Intent(c,MainActivity.class).putExtra("page","settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_alarm_notification).setColor(Appearance.current(c).accent).setContentTitle("Alarm needs attention").setContentText(issue).setStyle(new Notification.BigTextStyle().bigText(issue)).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try{nm.notify(NOTICE,notice);}catch(SecurityException ignored){}
        p.edit().putString("readinessIssue",issue).commit();
    }
    private Readiness(){}
}
