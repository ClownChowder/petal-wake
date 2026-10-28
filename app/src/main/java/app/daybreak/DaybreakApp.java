package app.daybreak;
import android.app.Application;
import android.content.Intent;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class DaybreakApp extends Application {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    public static final ExecutorService NETWORK_IO=Executors.newSingleThreadExecutor();
    // Long music and move-package I/O must not delay calendar refresh or saved settings.
    public static final ExecutorService PACK_IO=Executors.newSingleThreadExecutor();
    public static final ExecutorService PREVIEW_IO=Executors.newSingleThreadExecutor();
    // Alarm controls must not wait behind calendar network requests on IO.
    public static final ExecutorService ALARM_IO=Executors.newSingleThreadExecutor();
    public static final String CHANGED="app.daybreak.CHANGED";
    public static final String INTERNAL_PERMISSION="app.daybreak.permission.INTERNAL_BROADCAST";
    @Override public void onCreate(){super.onCreate();MovePackage.clearAbandoned(this);FourWagsWakeIcon.initialize(this);if(!FourWagsWakeIcon.pending(this))Appearance.syncLauncher(this);SoundLibrary.migrate(this);upgradePlayback();AlarmService.recoverAlarmVolume(this);AlarmService.createChannel(this);CalendarConfidence.createChannel(this);EveningCheck.createChannel(this);Readiness.createChannel(this);ShiftChanges.createChannel(this);SyncJob.install(this);}
    private void upgradePlayback(){
        var preferences=Store.p(this);
        int revision=preferences.getInt("playbackRevision",0);
        if(revision<2){var edit=preferences.edit().putInt("volume",100).putInt("fade",0).putBoolean("quietPreset",false).putInt("playbackRevision",2);if(revision<1)edit.putBoolean("vibrate",true);edit.commit();}
        if(revision<3)preferences.edit().putInt("volume",100).putBoolean("quietPreset",false).remove("previousPlayback").putInt("playbackRevision",3).commit();
    }
    public void refresh(boolean fetch){
        if(fetch)CalendarRefresh.request(this,()->true,this::changed);
        else IO.execute(()->{try{Sync.run(this,false);}catch(RuntimeException error){Store.error(this,"Calendar refresh failed. Saved alarms remain in place.");}finally{changed();}});
    }
    public void changed(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
}
