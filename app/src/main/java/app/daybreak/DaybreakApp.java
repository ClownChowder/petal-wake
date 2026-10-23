package app.daybreak;
import android.app.Application;
import android.content.Intent;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class DaybreakApp extends Application {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    public static final String CHANGED="app.daybreak.CHANGED";
    public static final String INTERNAL_PERMISSION="app.daybreak.permission.INTERNAL_BROADCAST";
    private final AtomicBoolean refreshPending=new AtomicBoolean();
    @Override public void onCreate(){super.onCreate();Appearance.syncLauncher(this);SoundLibrary.migrate(this);upgradePlayback();AlarmService.createChannel(this);CalendarConfidence.createChannel(this);EveningCheck.createChannel(this);Readiness.createChannel(this);ShiftChanges.createChannel(this);SyncJob.install(this);}
    private void upgradePlayback(){
        var preferences=Store.p(this);
        if(preferences.getInt("playbackRevision",0)<1)preferences.edit().putInt("volume",100).putInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS).putBoolean("vibrate",true).putInt("playbackRevision",1).commit();
    }
    public void refresh(boolean fetch){
        if(!refreshPending.compareAndSet(false,true))return;
        IO.execute(()->{try{Sync.run(this,fetch);}catch(RuntimeException error){Store.error(this,"Calendar refresh failed. Saved alarms remain in place.");}finally{refreshPending.set(false);changed();}});
    }
    public void changed(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
}
