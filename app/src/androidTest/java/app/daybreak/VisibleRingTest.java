package app.daybreak;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;

/** Disposable emulator only: a visible app must open the persistent alarm screen. */
public final class VisibleRingTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Context c=getTargetContext();Bundle result=new Bundle();Activity ring=null;
        try{
            ActivityMonitor monitor=addMonitor(RingActivity.class.getName(),null,false);
            startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.elapsedRealtime()+8_000L;
            while(!MainActivity.visible&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            if(!MainActivity.visible)throw new Exception("Main screen never became visible");
            JSONObject alarm=new JSONObject().put("id","qa-visible-ring").put("title","Visible ring test").put("kind","test").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            ring=waitForMonitorWithTimeout(monitor,8_000L);
            if(!(ring instanceof RingActivity)||!Store.object(c,"ringing").has("qa-visible-ring"))throw new Exception("Visible alarm did not open its full-screen controls");
            runOnMainSync(ring::onBackPressed);
            if(!Store.object(c,"ringing").has("qa-visible-ring"))throw new Exception("Back dismissed a ringing alarm");
            result.putString("summary","Visible alarm opened full-screen controls; Back did not dismiss it");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP_TEST"));if(ring!=null){Activity done=ring;runOnMainSync(done::finish);}}
    }
}
