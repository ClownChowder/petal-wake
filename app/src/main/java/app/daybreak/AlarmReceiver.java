package app.daybreak;
import android.content.*;
import org.json.JSONObject;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        try {JSONObject a=Scheduler.claim(c,i.getStringExtra("id"));if(a==null)return;
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",a.toString()));
        } catch(Exception e){Store.error(c,"Alarm could not start. Open Petal Wake and check Android alarm permissions.");}
    }
}
