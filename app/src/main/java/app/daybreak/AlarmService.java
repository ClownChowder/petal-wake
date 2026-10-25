package app.daybreak;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlarmService extends Service {
    public static final String CHANNEL="wake_alarms";
    private MediaPlayer player;private Vibrator vibrator;private PowerManager.WakeLock lock;
    private int fadeSeconds,rampTargetStep,rampStartPercent,rampLastStep=-1;private long playbackBegan;
    private static volatile AlarmService active;
    private final AtomicBoolean snoozePending=new AtomicBoolean();
    private final AtomicBoolean snoozeCancelled=new AtomicBoolean();
    // Accessed only by the alarm-control executor, including the queued cancellation.
    private final ArrayList<String> pendingSnoozeIds=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject current; private AudioManager audio;private AudioFocusRequest focus;private boolean fallbackActive;private volatile boolean destroyed;
    @Override public void onCreate(){super.onCreate();active=this;}
    public static int playbackPercent(){AlarmService service=active;if(service==null||service.player==null||service.audio==null)return -1;int maximum=service.audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);return maximum<=0?-1:Math.round(100f*service.audio.getStreamVolume(AudioManager.STREAM_ALARM)/maximum);}
    @Override protected void dump(FileDescriptor fd,PrintWriter writer,String[] args){
        writer.println("Petal Wake alarm volume");writer.println("elapsedMs="+(playbackBegan==0?0:SystemClock.elapsedRealtime()-playbackBegan));writer.println("riseSeconds="+fadeSeconds);writer.println("currentAlarmStep="+(audio==null?-1:audio.getStreamVolume(AudioManager.STREAM_ALARM)));writer.println("displayedPercent="+playbackPercent());
    }
    public static void createChannel(Context c){
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Wake-up alarms",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Scheduled work and personal alarms");ch.setSound(null,null);ch.enableVibration(false);ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
    private PendingIntent action(String action){return PendingIntent.getService(this,action.hashCode(),new Intent(this,AlarmService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    @Override public int onStartCommand(Intent i,int flags,int id){
        String action=i==null?"":i.getAction();
        if("SNOOZE".equals(action)) {
            if(!snoozePending.compareAndSet(false,true))return START_STICKY;
            DaybreakApp.ALARM_IO.execute(()->{
                try {
                    if(destroyed||snoozeCancelled.get())return;
                    if(!Scheduler.allowed(this))throw new IllegalStateException("Alarm permission is off. Allow Alarms & reminders before snoozing.");
                    JSONObject batch=Store.object(this,"ringing");
                    for(java.util.Iterator<String> it=batch.keys();it.hasNext()&&!snoozeCancelled.get();){
                        String snoozeId=Scheduler.snooze(this,batch.getJSONObject(it.next()),()->destroyed||snoozeCancelled.get());
                        if(snoozeId!=null)pendingSnoozeIds.add(snoozeId);
                    }
                    if(!destroyed&&!snoozeCancelled.get())handler.post(()->{if(!destroyed&&!snoozeCancelled.get())finish("snoozed");});
                } catch(Exception e){snoozePending.set(false);Store.error(this,"Snooze failed. Allow Alarms & reminders in Android settings.");if(!destroyed)handler.post(()->{if(!destroyed)android.widget.Toast.makeText(this,"Snooze failed. The alarm will keep ringing.",android.widget.Toast.LENGTH_LONG).show();});}
            });
            return START_STICKY;
        }
        if("STOP".equals(action)){finish("dismissed");return START_NOT_STICKY;}
        if("STOP_TEST".equals(action)){
            JSONObject ringing=Store.object(this,"ringing");
            JSONObject only=current==null?null:ringing.optJSONObject(current.optString("id"));
            if(ringing.length()==1&&only!=null&&"test".equals(only.optString("kind")))finish("dismissed");
            else if(current==null)stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if(i!=null&&i.hasExtra("alarm")){
                JSONObject incoming=new JSONObject(i.getStringExtra("alarm"));
                JSONObject batch=Store.object(this,"ringing");batch.put(incoming.getString("id"),incoming);Store.put(this,"ringing",batch);
                AlarmHistory.record(this,"rang",incoming);
                if(current==null)current=incoming;
            } else {JSONObject batch=Store.object(this,"ringing");java.util.Iterator<String> keys=batch.keys();if(keys.hasNext())current=batch.getJSONObject(keys.next());}
            if(current==null){stopSelf();return START_NOT_STICKY;}
            Store.put(this,"current",current);
            Notification n=alarmNotification();
            if(Build.VERSION.SDK_INT>=29)startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(1,n);
            // Android shows only a heads-up notification for a full-screen intent while the
            // phone is unlocked. When our own screen is visible, open the alarm controls now.
            if(MainActivity.visible){try{startActivity(new Intent(this,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP));}catch(RuntimeException ignored){/* The ongoing notification remains available. */}}
            if(lock==null){lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"daybreak:ring");lock.acquire((ringMinutes()+1L)*60_000L);}
            if(player==null)play();
            JSONObject playing=Store.object(this,"ringing");for(java.util.Iterator<String> ids=playing.keys();ids.hasNext();)try{Scheduler.acknowledge(this,ids.next());}catch(Exception ignored){}
            ((DaybreakApp)getApplication()).changed();
            return START_STICKY;
        }catch(Exception e){Store.error(this,"Alarm playback failed. Check the phone’s alarm volume.");finish("playback failed");return START_NOT_STICKY;}
    }
    private Notification alarmNotification(){
        Intent open=new Intent(this,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent full=PendingIntent.getActivity(this,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        int count=Store.object(this,"ringing").length();
        boolean testOnly=count==1&&"test".equals(current.optString("kind"));
        String title=count>1?count+" alarms · "+current.optString("title","Time to wake up"):current.optString("title","Time to wake up");
        android.view.accessibility.AccessibilityManager access=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        String guidance=testOnly?"Test alarm sounding · choose Stop test to finish"
            :access!=null&&access.isEnabled()?(fallbackActive?"Backup alert sounding · choose I’m up to confirm dismissal":"Time to wake up · choose I’m up to confirm or Snooze")
            :(fallbackActive?"Backup alert sounding · hold I’m up to dismiss":"Time to wake up · hold I’m up to dismiss or tap Snooze");
        return new Notification.Builder(this,CHANNEL).setSmallIcon(app.daybreak.R.drawable.ic_alarm_notification).setColor(Appearance.current(this).surface).setColorized(true).setOnlyAlertOnce(true).setContentTitle(title).setContentText(guidance).setCategory(Notification.CATEGORY_ALARM).setVisibility(Store.p(this).getBoolean("privateLockScreen",true)?Notification.VISIBILITY_PRIVATE:Notification.VISIBILITY_PUBLIC).setPublicVersion(new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_alarm_notification).setContentTitle("Petal Wake alarm").setContentText("Alarm sounding").build()).setOngoing(true).setContentIntent(full).setFullScreenIntent(full,true)
            .addAction(new Notification.Action.Builder(null,"Snooze",action("SNOOZE")).build())
            .addAction(new Notification.Action.Builder(null,testOnly?"Stop test":"I’m up",testOnly?action("STOP_TEST"):full).build()).build();
    }
    public static void appearanceChanged(){
        AlarmService service=active;
        if(service!=null&&service.current!=null)((NotificationManager)service.getSystemService(NOTIFICATION_SERVICE)).notify(1,service.alarmNotification());
    }
    private void play()throws Exception {
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{}).build();audio.requestAudioFocus(focus);
        String sound=SoundLibrary.playable(this);
        try{player=preparedPlayer(attributes,sound,false);}
        catch(Exception selectedFailure){
            try{player=preparedPlayer(attributes,"tone_12.wav",false);}
            catch(Exception bundledFailure){player=preparedPlayer(attributes,null,true);}
            fallbackActive=true;AlarmHistory.record(this,"audio fallback",current);
            Store.error(this,"Selected sound could not play. The standard backup alert is sounding.");
        }
        fadeSeconds=fallbackActive?0:Math.max(0,Math.min(120,Store.p(this).getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS)));
        rampTargetStep=audio.getStreamVolume(AudioManager.STREAM_ALARM);
        rampStartPercent=Store.p(this).getInt("riseStartPercent",AlarmVolumeRamp.DEFAULT_START_PERCENT);
        player.setVolume(1f,1f);
        int minimum=Build.VERSION.SDK_INT>=28?audio.getStreamMinVolume(AudioManager.STREAM_ALARM):0;
        if(fadeSeconds>0&&rampTargetStep>minimum){
            int first=AlarmVolumeRamp.step(rampTargetStep,minimum,audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),rampStartPercent,fadeSeconds,0);
            if(first<rampTargetStep&&markRampStep(rampTargetStep,first)){
                try{audio.setStreamVolume(AudioManager.STREAM_ALARM,first,0);rampLastStep=audio.getStreamVolume(AudioManager.STREAM_ALARM);if(rampLastStep!=first)restoreAlarmVolume();}
                catch(RuntimeException unavailable){try{rampLastStep=audio.getStreamVolume(AudioManager.STREAM_ALARM);}catch(RuntimeException ignored){}restoreAlarmVolume();Store.error(this,"Phone alarm volume could not rise automatically. Check the alarm level.");}
            }
        }
        playbackBegan=SystemClock.elapsedRealtime();player.start();if(rampLastStep>=0)handler.postDelayed(rampTick,1000);
        if(Store.p(this).getBoolean("vibrate",true)){vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);vibrator.vibrate(VibrationEffect.createWaveform(new long[]{650,200,650,600},new int[]{255,0,255,0},0),attributes);}
        int fallbackMinutes=Store.p(this).getInt("fallbackMinutes",0);
        if(fallbackMinutes>=1&&fallbackMinutes<=20)handler.postDelayed(this::startFallback,fallbackMinutes*60_000L);
        // Keep the ringing service finite if a phone is left unattended.
        handler.postDelayed(this::finish,ringMinutes()*60_000L);
    }
    private int ringMinutes(){int selected=Store.p(this).getInt("maxRingMinutes",30);return selected==15||selected==60?selected:30;}
    private final Runnable rampTick=new Runnable(){@Override public void run(){
        if(audio==null||rampLastStep<0||destroyed)return;
        int actual=audio.getStreamVolume(AudioManager.STREAM_ALARM);
        if(actual!=rampLastStep){rampLastStep=-1;clearRampMarker(AlarmService.this);return;}
        long elapsed=SystemClock.elapsedRealtime()-playbackBegan;
        int minimum=Build.VERSION.SDK_INT>=28?audio.getStreamMinVolume(AudioManager.STREAM_ALARM):0;
        int next=AlarmVolumeRamp.step(rampTargetStep,minimum,audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),rampStartPercent,fadeSeconds,elapsed);
        if(next!=actual&&!markRampStep(actual,next)){restoreAlarmVolume();return;}
        try{if(next!=actual)audio.setStreamVolume(AudioManager.STREAM_ALARM,next,0);rampLastStep=audio.getStreamVolume(AudioManager.STREAM_ALARM);if(rampLastStep!=next){restoreAlarmVolume();return;}}
        catch(RuntimeException error){try{rampLastStep=audio.getStreamVolume(AudioManager.STREAM_ALARM);}catch(RuntimeException ignored){}restoreAlarmVolume();Store.error(AlarmService.this,"Phone alarm volume rise stopped. Check the alarm level.");return;}
        if(elapsed>=fadeSeconds*1000L)restoreAlarmVolume();else handler.postDelayed(this,1000);
    }};
    private boolean markRampStep(int previous,int next){return Store.p(this).edit().putInt("alarmRampRestoreStep",rampTargetStep).putInt("alarmRampPreviousStep",previous).putInt("alarmRampLastStep",next).commit();}
    private static void clearRampMarker(Context c){Store.p(c).edit().remove("alarmRampRestoreStep").remove("alarmRampPreviousStep").remove("alarmRampLastStep").commit();}
    private void restoreAlarmVolume(){
        if(rampLastStep<0)return;
        boolean restored=true;
        if(audio!=null&&audio.getStreamVolume(AudioManager.STREAM_ALARM)==rampLastStep){
            try{audio.setStreamVolume(AudioManager.STREAM_ALARM,rampTargetStep,0);restored=audio.getStreamVolume(AudioManager.STREAM_ALARM)==rampTargetStep;}
            catch(RuntimeException error){restored=false;}
            if(!restored)Store.error(this,"Could not restore phone alarm volume. Check Android sound settings.");
        }
        rampLastStep=-1;if(restored)clearRampMarker(this);
    }
    public static void recoverAlarmVolume(Context c){
        int target=Store.p(c).getInt("alarmRampRestoreStep",-1);if(target<0)return;
        int last=Store.p(c).getInt("alarmRampLastStep",-1),previous=Store.p(c).getInt("alarmRampPreviousStep",-1);
        try{AudioManager audio=(AudioManager)c.getSystemService(AUDIO_SERVICE);int current=audio.getStreamVolume(AudioManager.STREAM_ALARM);if(current!=last&&current!=previous){clearRampMarker(c);return;}int desired=Math.min(target,audio.getStreamMaxVolume(AudioManager.STREAM_ALARM));if(current!=desired)audio.setStreamVolume(AudioManager.STREAM_ALARM,desired,0);if(audio.getStreamVolume(AudioManager.STREAM_ALARM)==desired)clearRampMarker(c);else Store.error(c,"Could not restore phone alarm volume after restart. Check Android sound settings.");}
        catch(RuntimeException error){Store.error(c,"Could not restore phone alarm volume after restart. Check Android sound settings.");}
    }
    private MediaPlayer preparedPlayer(AudioAttributes attributes,String sound,boolean systemSound)throws Exception {
        MediaPlayer candidate=new MediaPlayer();
        try{
            candidate.setAudioAttributes(attributes);candidate.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);candidate.setLooping(true);
            if(systemSound){
                android.net.Uri uri=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                if(uri==null)throw new FileNotFoundException("No system alarm sound is available.");
                candidate.setDataSource(this,uri);
            }else SoundLibrary.setDataSource(candidate,this,sound);
            candidate.prepare();return candidate;
        }catch(Exception error){candidate.release();throw error;}
    }
    private void startFallback(){
        if(fallbackActive||current==null||player==null)return;
        MediaPlayer backup=new MediaPlayer();
        try{
            backup.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            backup.setLooping(true);SoundLibrary.setDataSource(backup,this,"tone_12.wav");backup.prepare();backup.setVolume(1f,1f);backup.start();
            handler.removeCallbacks(rampTick);restoreAlarmVolume();player.release();player=backup;fadeSeconds=0;fallbackActive=true;playbackBegan=SystemClock.elapsedRealtime();
            AlarmHistory.record(this,"backup alert",current);
            try{getSystemService(NotificationManager.class).notify(1,alarmNotification());}catch(SecurityException ignored){}
            ((DaybreakApp)getApplication()).changed();
        }catch(Exception e){backup.release();Store.error(this,"Backup alert could not play. The original alarm keeps ringing.");}
    }
    private void finish(){finish("timed out");}
    private void finish(String event){
        if(destroyed)return;
        if(!"snoozed".equals(event)&&snoozePending.get()){
            snoozeCancelled.set(true);
            // This runs after the in-flight snooze task; dismissal wins even if it
            // happened while that task was inside Scheduler.snooze/Sync.run.
            DaybreakApp.ALARM_IO.execute(()->{
                try{Scheduler.cancelSnoozes(this,pendingSnoozeIds);}
                catch(Exception e){Store.error(this,"Could not cancel an interrupted snooze. Check upcoming alarms.");}
                finally{pendingSnoozeIds.clear();}
            });
        }
        snoozePending.set(false);
        handler.removeCallbacksAndMessages(null);
        if(!"snoozed".equals(event)){
            JSONObject batch=Store.object(this,"ringing");
            if(batch.length()==0&&current!=null)AlarmHistory.record(this,event,current);
            for(java.util.Iterator<String> keys=batch.keys();keys.hasNext();){JSONObject alarm=batch.optJSONObject(keys.next());if(alarm!=null)AlarmHistory.record(this,event,alarm);}
        }
        restoreAlarmVolume();current=null;
        Store.put(this,"ringing",new JSONObject());Store.put(this,"current",new JSONObject());
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();((DaybreakApp)getApplication()).changed();
        sendBroadcast(new Intent("app.daybreak.RING_DONE").setPackage(getPackageName()));
    }
    @Override public void onDestroy(){
        destroyed=true;handler.removeCallbacksAndMessages(null);restoreAlarmVolume();if(active==this)active=null;
        if(player!=null){try{player.release();}catch(RuntimeException ignored){}player=null;}
        if(vibrator!=null){try{vibrator.cancel();}catch(RuntimeException ignored){}vibrator=null;}
        if(lock!=null){try{if(lock.isHeld())lock.release();}catch(RuntimeException ignored){}lock=null;}
        if(audio!=null&&focus!=null){try{audio.abandonAudioFocusRequest(focus);}catch(RuntimeException ignored){}focus=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
