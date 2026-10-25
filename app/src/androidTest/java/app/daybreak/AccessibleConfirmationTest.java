package app.daybreak;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import org.json.JSONObject;

/** Verify the two-step assistive dismissal without relying on a timed touch hold. */
public final class AccessibleConfirmationTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();RingActivity activity=null;
        try{
            String id="qa-accessible-confirm";
            JSONObject alarm=new JSONObject().put("id",id).put("title","Synthetic accessibility alarm")
                .put("kind","manual").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            long deadline=System.currentTimeMillis()+8000;
            while(!Store.object(c,"ringing").has(id)&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            if(!Store.object(c,"ringing").has(id))throw new Exception("Synthetic alarm did not ring");
            AccessibilityManager access=(AccessibilityManager)c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if(access==null||!access.isEnabled())throw new Exception("Requires an enabled accessibility service");
            activity=(RingActivity)startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ViewGroup layout=(ViewGroup)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            Button awake=(Button)layout.getChildAt(1);RingActivity screen=activity;
            if(!awake.getText().toString().contains("Select to confirm dismissal"))
                throw new Exception("Assistive screen does not explain the confirmation action");
            runOnMainSync(awake::performClick);
            if(!screen.dismissConfirmationShowing())throw new Exception("Accessibility activation did not open confirmation");
            clickConfirmationButton(screen,AlertDialog.BUTTON_NEGATIVE,"Keep ringing");
            if(!Store.object(c,"ringing").has(id))throw new Exception("Canceling confirmation stopped the alarm");
            runOnMainSync(awake::performClick);
            if(!screen.dismissConfirmationShowing())throw new Exception("Confirmation did not reopen");
            clickConfirmationButton(screen,AlertDialog.BUTTON_POSITIVE,"Dismiss alarm");
            deadline=System.currentTimeMillis()+8000;
            while(Store.object(c,"ringing").has(id)&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            if(Store.object(c,"ringing").has(id))throw new Exception("Accessible confirmation did not stop the alarm");
            result.putString("summary","Assistive click opened confirmation; Keep ringing canceled; Dismiss alarm stopped ring");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{c.startService(new Intent(c,AlarmService.class).setAction("STOP"));if(activity!=null&&!activity.isFinishing()){RingActivity screen=activity;runOnMainSync(screen::finish);}}
    }
    private void clickConfirmationButton(RingActivity screen,int which,String label)throws Exception{
        AlertDialog dialog=screen.dismissConfirmationDialog();
        if(dialog==null||!dialog.isShowing()||dialog.getButton(which)==null||
            !label.contentEquals(dialog.getButton(which).getText()))
            throw new Exception("Confirmation action missing: "+label);
        runOnMainSync(()->dialog.getButton(which).performClick());
    }
}
