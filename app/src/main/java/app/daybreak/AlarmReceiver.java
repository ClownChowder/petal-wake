package app.daybreak;
import android.content.*;
import org.json.JSONObject;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        String id=i==null?null:i.getStringExtra("id");
        if(id==null)return;
        try {JSONObject a=Scheduler.claim(c,id);if(a==null)return;
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",a.toString()));
        } catch(Exception e){if(Store.object(c,"inFlight").has(id)){Scheduler.retryFailedDelivery(c,id);Store.error(c,"Alarm start failed. Rouse will retry once if Android allows it.");}else Scheduler.failureNotice(c);}
    }
}
