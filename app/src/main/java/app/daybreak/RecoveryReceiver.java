package app.daybreak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** One bounded retry when the scheduled delivery did not reach audio playback. */
public final class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent!=null&&"app.daybreak.RECOVER".equals(intent.getAction()))
            Scheduler.retryFailedDelivery(context,intent.getStringExtra("id"));
    }
}
