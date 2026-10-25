package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import org.json.JSONObject;

/** Deliberate accessibility long-click dismisses; a normal click does not. */
public final class AccessibleDismissTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();RingActivity activity=null;
        try{
            JSONObject alarm=new JSONObject().put("id","qa-ring-persistence").put("title","Ring persistence test").put("kind","manual").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            long deadline=System.currentTimeMillis()+8_000L;while(!Store.object(c,"ringing").has("qa-ring-persistence")&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            if(!Store.object(c,"ringing").has("qa-ring-persistence"))throw new Exception("Test alarm did not start.");
            int snoozesBefore=Store.object(c,"snoozes").length();
            activity=(RingActivity)startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ViewGroup root=(ViewGroup)activity.findViewById(android.R.id.content);ViewGroup layout=(ViewGroup)root.getChildAt(0);Button awake=(Button)layout.getChildAt(1);
            RingActivity screen=activity;runOnMainSync(awake::performClick);
            if(screen.isFinishing())throw new Exception("A short accessibility click dismissed the alarm.");
            runOnMainSync(screen::onBackPressed);
            if(screen.isFinishing()||!Store.object(c,"ringing").has("qa-ring-persistence"))throw new Exception("Back ended the alarm.");
            runOnMainSync(()->{long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,awake.getWidth()/2f,awake.getHeight()/2f,0);awake.dispatchTouchEvent(down);down.recycle();screen.moveTaskToBack(true);});
            Thread.sleep(2500);
            if(!Store.object(c,"ringing").has("qa-ring-persistence")||Store.object(c,"snoozes").length()!=snoozesBefore)throw new Exception("Leaving the screen snoozed or stopped the alarm.");
            // A deliberate accessibility action happens on the visible screen.
            // Starting a service from the backgrounded Activity is restricted by Android.
            activity=(RingActivity)startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            ViewGroup focusedRoot=(ViewGroup)activity.findViewById(android.R.id.content);
            Button focusedAwake=(Button)((ViewGroup)focusedRoot.getChildAt(0)).getChildAt(1);
            runOnMainSync(focusedAwake::performLongClick);
            deadline=System.currentTimeMillis()+8_000L;while(Store.object(c,"ringing").has("qa-ring-persistence")&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            if(Store.object(c,"ringing").has("qa-ring-persistence"))throw new Exception("Accessibility long-click did not dismiss the alarm.");
            result.putString("summary","Back and leaving the screen preserved ringing; explicit long-click dismissed it");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));if(activity!=null&&!activity.isFinishing()){RingActivity screen=activity;runOnMainSync(screen::finish);}}
    }
}
