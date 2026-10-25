package app.daybreak;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/** Requires the separately signed qa.petal.attack fixture on a disposable emulator. */
public final class CrossUidRingDoneTest extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();RingActivity screen=null;
  try{
   Store.put(c,"ringing",new JSONObject());Store.p(c).edit().putBoolean("vibrate",false).putInt("fade",0).commit();
   JSONObject alarm=new JSONObject().put("id","qa-cross-uid-ring").put("kind","manual").put("title","Synthetic ring defense").put("at",System.currentTimeMillis());
   c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
   long deadline=System.currentTimeMillis()+20_000;while(!Store.object(c,"ringing").has("qa-cross-uid-ring")&&System.currentTimeMillis()<deadline)Thread.sleep(100);
   if(!Store.object(c,"ringing").has("qa-cross-uid-ring"))throw new Exception("Synthetic alarm did not begin");
   screen=(RingActivity)startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   c.startActivity(new Intent().setComponent(new ComponentName("qa.petal.attack","qa.petal.attack.ProbeActivity")).putExtra("probe","RING_DONE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Thread.sleep(1200);
   if(screen.isFinishing()||!Store.object(c,"ringing").has("qa-cross-uid-ring"))throw new Exception("Forged cross-UID RING_DONE dismissed the alarm");
   result.putString("summary","Differently signed RING_DONE did not close the ring screen or stop playback");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
  finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));if(screen!=null){RingActivity last=screen;runOnMainSync(last::finish);}}
 }
}
