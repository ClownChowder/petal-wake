package app.daybreak;
import android.content.*;
public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null)return;
        String action=i.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)&&!Intent.ACTION_TIME_CHANGED.equals(action)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(action)&&!"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action))return;
        PendingResult done=goAsync();DaybreakApp.IO.execute(()->{try{
            boolean attention=false;
            try{if(Intent.ACTION_BOOT_COMPLETED.equals(action))attention=Scheduler.recoverMissedAfterBoot(c);}catch(Exception failure){attention=true;}
            try{Scheduler.recoverOutstanding(c);}catch(Exception failure){attention=true;}
            // Android may have discarded alarm registrations during reboot,
            // replacement, clock changes, or an exact-alarm permission change.
            try{Sync.run(c,false,true);}catch(Exception failure){attention=true;}
            try{SyncJob.install(c);}catch(Exception failure){attention=true;}
            if(attention)Scheduler.failureNotice(c);
        }finally{done.finish();}});
    }
}
