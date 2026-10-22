package app.daybreak;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;

/** A separate silent channel, private text, and one notice per unresolved sync episode. */
public final class CalendarConfidence {
 public static final String CHANNEL="calendar_health";public static final int NOTICE=200;
 public static void createChannel(Context c){NotificationChannel channel=new NotificationChannel(CHANNEL,"Calendar sync notices",NotificationManager.IMPORTANCE_LOW);channel.setDescription("Silent notices when the work calendar cannot refresh");channel.setSound(null,null);channel.enableVibration(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);c.getSystemService(NotificationManager.class).createNotificationChannel(channel);}
 public static void attempted(Context c){Store.p(c).edit().putLong("syncAttempt",System.currentTimeMillis()).commit();}
 public static void succeeded(Context c){Store.p(c).edit().putInt("syncFailures",0).remove("syncFirstFailure").remove("syncLastFailure").commit();}
 public static void failed(Context c){var p=Store.p(c);long now=System.currentTimeMillis();p.edit().putInt("syncFailures",Math.min(1000,p.getInt("syncFailures",0)+1)).putLong("syncFirstFailure",p.getLong("syncFirstFailure",now)).putLong("syncLastFailure",now).commit();}
 public static void reset(Context c){Store.p(c).edit().remove("syncAttempt").remove("syncFailures").remove("syncFirstFailure").remove("syncLastFailure").commit();update(c);}
 public static boolean canNotify(Context c){var nm=c.getSystemService(NotificationManager.class);var channel=nm.getNotificationChannel(CHANNEL);return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)&&nm.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);}
 public static JSONObject snapshot(Context c)throws JSONException {
  var p=Store.p(c);boolean primary=!Store.url(c).trim().isEmpty(),linked=primary,imported=!primary&&!p.getString("source","").isEmpty();long now=System.currentTimeMillis(),success=primary?p.getLong("lastSync",0):0,attempt=p.getLong("syncAttempt",0),failure=p.getLong("syncLastFailure",0);int failures=primary?p.getInt("syncFailures",0):0;
  boolean anyFailed=primary&&failures>0,anyStale=primary&&success>0&&now-success>=CalendarHealthPolicy.STALE_MS;
  JSONArray extras=CalendarSources.extras(c);for(int i=0;i<extras.length();i++){JSONObject extra=extras.optJSONObject(i);if(extra==null)continue;linked=true;long downloaded=extra.optLong("lastSync"),lastAttempt=extra.optLong("attempt");success=success==0?downloaded:downloaded==0?0:Math.min(success,downloaded);attempt=Math.max(attempt,lastAttempt);int count=extra.optInt("failures");failures+=count;anyFailed|=count>0;anyStale|=downloaded>0&&now-downloaded>=CalendarHealthPolicy.STALE_MS;if(count>0)failure=Math.max(failure,lastAttempt);}
  String status=!linked?imported?"imported":"unlinked":anyFailed?"failed":anyStale?"stale":success<=0?"never":"fresh";
  return new JSONObject().put("status",status).put("lastSuccess",success).put("lastAttempt",attempt).put("lastFailure",failure).put("failures",failures).put("alertsEnabled",p.getBoolean("syncAlerts",true)).put("canNotify",canNotify(c));
 }
 public static void update(Context c){
  createChannel(c);var p=Store.p(c);var nm=c.getSystemService(NotificationManager.class);boolean primary=!Store.url(c).trim().isEmpty(),linked=primary;int failures=p.getInt("syncFailures",0);long success=p.getLong("lastSync",0),now=System.currentTimeMillis();boolean enabled=p.getBoolean("syncAlerts",true)&&canNotify(c);
  boolean due=CalendarHealthPolicy.alertDue(primary,success,failures,p.getLong("syncFirstFailure",0),now),problem=primary&&!"fresh".equals(CalendarHealthPolicy.status(true,false,success,failures,now));
  JSONArray extras=CalendarSources.extras(c);for(int i=0;i<extras.length();i++){JSONObject extra=extras.optJSONObject(i);if(extra==null)continue;linked=true;int count=extra.optInt("failures");long last=extra.optLong("lastSync");due|=CalendarHealthPolicy.alertDue(true,last,count,extra.optLong("firstFailure"),now);problem|=!"fresh".equals(CalendarHealthPolicy.status(true,false,last,count,now));failures+=count;}
  if(!linked||!enabled||!problem){nm.cancel(NOTICE);p.edit().putBoolean("syncAlertActive",false).commit();return;}
  if(!due||p.getBoolean("syncAlertActive",false))return;
  PendingIntent open=PendingIntent.getActivity(c,201,new Intent(c,MainActivity.class).putExtra("page","calendar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  String text=failures>0?"A work calendar could not refresh. Saved alarms remain in place. Tap to check.":"A work calendar has not downloaded in over 8 hours. Saved alarms remain in place. Tap to check.";
  Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_alarm_notification).setColor(Appearance.current(c).accent).setContentTitle("Calendar needs attention").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setCategory(Notification.CATEGORY_STATUS).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(true).build();
  try{nm.notify(NOTICE,n);p.edit().putBoolean("syncAlertActive",true).commit();}catch(SecurityException ignored){}
 }
 private CalendarConfidence(){}
}
