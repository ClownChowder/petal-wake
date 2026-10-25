package app.daybreak;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/** A corrupted snooze setting must leave the active alarm ringing. */
public final class InvalidSnoozeTest extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();int prior=Store.p(c).getInt("snooze",9);
  try{
   Store.put(c,"ringing",new JSONObject());Store.put(c,"snoozes",new JSONObject());Store.p(c).edit().putInt("snooze",0).putBoolean("vibrate",false).commit();
   JSONObject alarm=new JSONObject().put("id","qa-invalid-snooze").put("kind","manual").put("title","Synthetic invalid snooze").put("at",System.currentTimeMillis());
   c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
   long deadline=System.currentTimeMillis()+20_000;while(!Store.object(c,"ringing").has("qa-invalid-snooze")&&System.currentTimeMillis()<deadline)Thread.sleep(100);
   if(!Store.object(c,"ringing").has("qa-invalid-snooze"))throw new Exception("Synthetic alarm did not begin");
   c.startService(new Intent(c,AlarmService.class).setAction("SNOOZE"));Thread.sleep(1800);
   if(!Store.object(c,"ringing").has("qa-invalid-snooze")||Store.object(c,"snoozes").length()!=0)throw new Exception("Invalid snooze setting silenced or rescheduled the alarm");
   result.putString("summary","Invalid snooze duration leaves the alarm ringing without scheduling a snooze");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
  finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));Store.p(c).edit().putInt("snooze",prior).commit();}
 }
}
