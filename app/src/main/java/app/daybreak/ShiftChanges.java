package app.daybreak;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;

/** Reports changes that appeared in a successfully downloaded work calendar. */
public final class ShiftChanges {
    private static final String CHANNEL="shift_changes";

    public static void createChannel(Context c){
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Work shift changes",NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Notices when a downloaded shift changes a wake-up alarm");
        ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    public static JSONArray diff(JSONArray before,JSONArray after,long now){
        LinkedHashMap<String,JSONObject> old=new LinkedHashMap<>(),updated=new LinkedHashMap<>();
        for(int i=0;i<before.length();i++){JSONObject a=before.optJSONObject(i);if(a!=null&&a.optLong("at")>now&&!a.optBoolean("skipped"))old.put(a.optString("id"),a);}
        for(int i=0;i<after.length();i++){JSONObject a=after.optJSONObject(i);if(a!=null&&a.optLong("at")>now&&!a.optBoolean("skipped"))updated.put(a.optString("id"),a);}
        JSONArray changes=new JSONArray();
        try{
            for(String id:old.keySet()){
                JSONObject a=old.get(id),b=updated.get(id);
                if(b==null)changes.put(new JSONObject().put("event","canceled").put("at",a.optLong("at")));
                else if(a.optLong("at")!=b.optLong("at"))changes.put(new JSONObject().put("event","moved").put("at",a.optLong("at")).put("to",b.optLong("at")));
            }
            for(String id:updated.keySet())if(!old.containsKey(id))changes.put(new JSONObject().put("event","added").put("to",updated.get(id).optLong("at")));
        }catch(Exception ignored){}
        return changes;
    }

    public static void publish(Context c,JSONArray changes){
        if(changes.length()==0)return;
        for(int i=0;i<changes.length()&&i<20;i++){
            JSONObject change=changes.optJSONObject(i);if(change!=null)AlarmHistory.change(c,change.optString("event"),change.optLong("at"),change.optLong("to"));
        }
        JSONObject first=changes.optJSONObject(0);
        String detail="Open Petal Wake to review the new wake-up times.";
        if(changes.length()==1&&first!=null){
            DateFormat format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);
            String event=first.optString("event");
            if("moved".equals(event))detail="Wake-up moved: "+format.format(new Date(first.optLong("at")))+" → "+format.format(new Date(first.optLong("to")));
            else if("canceled".equals(event))detail="Wake-up removed: "+format.format(new Date(first.optLong("at")));
            else if("added".equals(event))detail="New wake-up: "+format.format(new Date(first.optLong("to")));
        }
        Intent open=new Intent(c,MainActivity.class).putExtra("page","calendar");
        PendingIntent pending=PendingIntent.getActivity(c,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(changes.length()==1?"Work alarm changed":changes.length()+" work alarm changes")
            .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try{c.getSystemService(NotificationManager.class).notify(41,notification);}catch(SecurityException ignored){}
    }
    private ShiftChanges(){}
}
