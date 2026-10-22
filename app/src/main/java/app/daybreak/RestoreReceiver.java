package app.daybreak;
import android.content.*;
public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){PendingResult done=goAsync();DaybreakApp.IO.execute(()->{try{Sync.run(c,false);SyncJob.install(c);}finally{done.finish();}});}
}
