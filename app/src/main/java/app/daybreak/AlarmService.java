package app.daybreak;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import org.json.*;
import java.io.*;

public class AlarmService extends Service {
    public static final String CHANNEL="wake_alarms";
    private MediaPlayer player;private Vibrator vibrator;private PowerManager.WakeLock lock;
    private VolumeShaper volumeShaper;private float targetVolume=1f;private int fadeSeconds;private long playbackBegan;
    private static volatile AlarmService active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject current; private AudioManager audio;private AudioFocusRequest focus;private boolean fallbackActive;
    @Override public void onCreate(){super.onCreate();active=this;}
    private float fadeGain(){try{return volumeShaper==null?1f:volumeShaper.getVolume();}catch(IllegalStateException e){return -1f;}}
    public static int playbackPercent(){AlarmService service=active;if(service==null||service.player==null)return -1;float gain=service.fadeGain();return gain<0?-1:Math.round(100f*service.targetVolume*(float)Math.sqrt(gain));}
    @Override protected void dump(FileDescriptor fd,PrintWriter writer,String[] args){
        writer.println("Petal Wake playback");writer.println("elapsedMs="+(playbackBegan==0?0:SystemClock.elapsedRealtime()-playbackBegan));writer.println("fadeSeconds="+fadeSeconds);writer.println("targetVolume="+targetVolume);writer.println("nativeFadeGain="+fadeGain());writer.println("displayedPercent="+playbackPercent());writer.println("nativeVolumeShaper="+(volumeShaper!=null));
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
            DaybreakApp.IO.execute(()->{
                try {
                    if(!Scheduler.allowed(this))throw new IllegalStateException("Alarm permission is off. Allow Alarms & reminders before snoozing.");
                    JSONObject batch=Store.object(this,"ringing");
                    for(java.util.Iterator<String> it=batch.keys();it.hasNext();)Scheduler.snooze(this,batch.getJSONObject(it.next()));
                    handler.post(()->finish("snoozed"));
                } catch(Exception e){Store.error(this,"Snooze failed. Allow Alarms & reminders in Android settings.");handler.post(()->android.widget.Toast.makeText(this,"Snooze failed. The alarm will keep ringing.",android.widget.Toast.LENGTH_LONG).show());}
            });
            return START_STICKY;
        }
        if("STOP".equals(action)){finish("dismissed");return START_NOT_STICKY;}
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
            if(lock==null){lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"daybreak:ring");lock.acquire(30*60_000L);}
            if(player==null)play();
            ((DaybreakApp)getApplication()).changed();
            return START_STICKY;
        }catch(Exception e){Store.error(this,"Alarm playback failed. Check the phone’s alarm volume.");finish("playback failed");return START_NOT_STICKY;}
    }
    private Notification alarmNotification(){
        Intent open=new Intent(this,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent full=PendingIntent.getActivity(this,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        int count=Store.object(this,"ringing").length();
        String title=count>1?count+" alarms · "+current.optString("title","Time to wake up"):current.optString("title","Time to wake up");
        return new Notification.Builder(this,CHANNEL).setSmallIcon(app.daybreak.R.drawable.ic_alarm_notification).setColor(Appearance.current(this).surface).setColorized(true).setOnlyAlertOnce(true).setContentTitle(title).setContentText(fallbackActive?"Backup alert sounding · hold I’m up to dismiss":"Time to wake up · hold I’m up to dismiss or tap Snooze").setCategory(Notification.CATEGORY_ALARM).setVisibility(Store.p(this).getBoolean("privateLockScreen",true)?Notification.VISIBILITY_PRIVATE:Notification.VISIBILITY_PUBLIC).setPublicVersion(new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_alarm_notification).setContentTitle("Petal Wake alarm").setContentText("Alarm sounding").build()).setOngoing(true).setContentIntent(full).setFullScreenIntent(full,true)
            .addAction(new Notification.Action.Builder(null,"Snooze",action("SNOOZE")).build())
            .addAction(new Notification.Action.Builder(null,"I’m up",full).build()).build();
    }
    public static void appearanceChanged(){
        AlarmService service=active;
        if(service!=null&&service.current!=null)((NotificationManager)service.getSystemService(NOTIFICATION_SERVICE)).notify(1,service.alarmNotification());
    }
    private void play()throws Exception {
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{}).build();audio.requestAudioFocus(focus);
        player=new MediaPlayer();player.setAudioAttributes(attributes);player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);player.setLooping(true);
        String sound=SoundLibrary.playable(this);
        try{SoundLibrary.setDataSource(player,this,sound);}
        catch(Exception e){player.setDataSource(this,android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM));}
        player.prepare();
        targetVolume=Math.max(0f,Math.min(1f,Store.p(this).getInt("volume",80)/100f));fadeSeconds=Store.p(this).getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS);
        player.setVolume(targetVolume,targetVolume);
        if(fadeSeconds>0){
            float[] times=new float[16],gains=new float[16];
            for(int point=0;point<times.length;point++){times[point]=point/(float)(times.length-1);gains[point]=AlarmEnvelope.volume(1f,fadeSeconds,Math.round(times[point]*fadeSeconds*1000));}
            VolumeShaper.Configuration curve=new VolumeShaper.Configuration.Builder().setDuration(fadeSeconds*1000L).setCurve(times,gains).setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR).build();
            volumeShaper=player.createVolumeShaper(curve);
        }
        playbackBegan=SystemClock.elapsedRealtime();player.start();if(volumeShaper!=null)volumeShaper.apply(VolumeShaper.Operation.PLAY);
        if(Store.p(this).getBoolean("vibrate",true)){vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);vibrator.vibrate(VibrationEffect.createWaveform(new long[]{650,200,650,600},new int[]{255,0,255,0},0),attributes);}
        int fallbackMinutes=Store.p(this).getInt("fallbackMinutes",0);
        if(fallbackMinutes>=1&&fallbackMinutes<=20)handler.postDelayed(this::startFallback,fallbackMinutes*60_000L);
        // Keep the ringing service finite if a phone is left unattended.
        handler.postDelayed(this::finish,30*60_000L);
    }
    private void startFallback(){
        if(fallbackActive||current==null||player==null)return;
        MediaPlayer backup=new MediaPlayer();
        try{
            backup.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            backup.setLooping(true);SoundLibrary.setDataSource(backup,this,"tone_12.wav");backup.prepare();backup.setVolume(1f,1f);backup.start();
            if(volumeShaper!=null)volumeShaper.close();player.release();player=backup;volumeShaper=null;targetVolume=1f;fadeSeconds=0;fallbackActive=true;playbackBegan=SystemClock.elapsedRealtime();
            AlarmHistory.record(this,"backup alert",current);
            try{getSystemService(NotificationManager.class).notify(1,alarmNotification());}catch(SecurityException ignored){}
            ((DaybreakApp)getApplication()).changed();
        }catch(Exception e){backup.release();Store.error(this,"Backup alert could not play. The original alarm keeps ringing.");}
    }
    private void finish(){finish("timed out");}
    private void finish(String event){
        if(!"snoozed".equals(event)){
            JSONObject batch=Store.object(this,"ringing");
            if(batch.length()==0&&current!=null)AlarmHistory.record(this,event,current);
            for(java.util.Iterator<String> keys=batch.keys();keys.hasNext();){JSONObject alarm=batch.optJSONObject(keys.next());if(alarm!=null)AlarmHistory.record(this,event,alarm);}
        }
        current=null;
        Store.put(this,"ringing",new JSONObject());Store.put(this,"current",new JSONObject());
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();((DaybreakApp)getApplication()).changed();
        sendBroadcast(new Intent("app.daybreak.RING_DONE").setPackage(getPackageName()));
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(active==this)active=null;if(player!=null){player.release();player=null;}volumeShaper=null;if(vibrator!=null)vibrator.cancel();if(lock!=null&&lock.isHeld())lock.release();if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
