package app.daybreak;
import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import org.json.*;
import java.util.concurrent.*;
import java.lang.reflect.Field;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Runs on an isolated emulator; never uses a person's calendar or device. */
public class NativeSmokeTest extends Instrumentation {
    private Activity activity;private WebView web;private int checks;private boolean uiOnly;
    private WebView findWeb(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){WebView found=findWeb(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private android.widget.Button findButton(View view,String text){if(view instanceof android.widget.Button&&((android.widget.Button)view).getText().toString().contains(text))return (android.widget.Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){android.widget.Button b=findButton(group.getChildAt(n),text);if(b!=null)return b;}}return null;}
    @Override public void onCreate(Bundle b){super.onCreate(b);uiOnly=b!=null&&"true".equals(b.getString("uiOnly"));start();}
    private void capture(String name)throws Exception{android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();java.io.File folder=new java.io.File(getTargetContext().getFilesDir(),"qa");folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void report(String stage){Bundle b=new Bundle();b.putString("stage",stage);sendStatus(0,b);}
    private void check(boolean value,String message)throws Exception{if(!value)throw new Exception(message);checks++;report("PASS: "+message);}
    private String js(String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] out={null};runOnMainSync(()->web.evaluateJavascript(script,v->{out[0]=v;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("WebView evaluation timed out");return out[0];}
    private void await(Callable<Boolean> test,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if(test.call())return;Thread.sleep(250);}throw new Exception("Timed out waiting for app state");}
    private long touchDown(android.widget.Button button)throws Exception{long down=SystemClock.uptimeMillis();runOnMainSync(()->{float x=button.getWidth()/2f,y=button.getHeight()/2f;button.dispatchTouchEvent(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0));});return down;}
    private void touchUp(android.widget.Button button,long down)throws Exception{long up=SystemClock.uptimeMillis();runOnMainSync(()->{float x=button.getWidth()/2f,y=button.getHeight()/2f;button.dispatchTouchEvent(MotionEvent.obtain(down,up,MotionEvent.ACTION_UP,x,y,0));});}
    private void io(Runnable work)throws Exception{DaybreakApp.IO.submit(work).get(15,TimeUnit.SECONDS);}
    private String at(long ms){return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));}
    private String calendar(long start){return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Daybreak QA//EN\r\nBEGIN:VEVENT\r\nUID:qa-work\r\nDTSTART:"+at(start)+"\r\nDURATION:PT8H\r\nSUMMARY:Work shift\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Context c=getTargetContext();
            Store.p(c).edit().putString("sound","classical_05.mid").commit();
            activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->web=findWeb(activity.findViewById(android.R.id.content)));
            await(()->"66".equals(js("window.SOUNDS&&window.SOUNDS.length")),15);
            check(Store.p(c).getString("sound","").equals(SoundLibrary.DEFAULT),"Upgrade replaces legacy MIDI selection with recorded orchestra");
            check("66".equals(js("window.SOUNDS.length")),"66 sound assets loaded in Android WebView");
            js("document.querySelector('[data-page=sounds]').click()");
            check("16".equals(js("document.querySelectorAll('.sound-row').length")),"16 gentle music choices displayed by default");
            js("document.querySelector('.play-button').click()");
            Field field=MainActivity.class.getDeclaredField("preview");field.setAccessible(true);
            await(()->{MediaPlayer p=(MediaPlayer)field.get(activity);return p!=null&&p.isPlaying();},10);
            check(true,"Recorded AAC orchestra playback works on Android");
            js("document.getElementById('standardTab').click();document.querySelector('.play-button').click()");
            await(()->{MediaPlayer p=(MediaPlayer)field.get(activity);return p!=null&&p.isPlaying();},10);
            check("12".equals(js("document.querySelectorAll('.sound-row').length")),"12 standard tones displayed and WAV playback works");
            js("document.getElementById('classicalTab').click()");check("48".equals(js("document.querySelectorAll('.sound-row').length")),"48 music choices displayed in All music");
            MediaPlayer beforePiano=(MediaPlayer)field.get(activity);js("document.getElementById('gentleTab').click();document.querySelectorAll('.play-button')[8].click()");
            await(()->{MediaPlayer p=(MediaPlayer)field.get(activity);return p!=null&&p!=beforePiano&&p.isPlaying();},10);check(true,"New gentle solo-piano AAC plays on Android");
            js("document.getElementById('natureTab').click()");check("6".equals(js("document.querySelectorAll('.sound-row').length")),"6 nature sounds displayed");
            MediaPlayer beforeOcean=(MediaPlayer)field.get(activity);js("document.querySelectorAll('.play-button')[0].click()");await(()->{MediaPlayer p=(MediaPlayer)field.get(activity);return p!=null&&p!=beforeOcean&&p.isPlaying();},10);check(true,"Ocean recording plays on Android");
            MediaPlayer beforeThunder=(MediaPlayer)field.get(activity);js("document.querySelectorAll('.play-button')[4].click()");await(()->{MediaPlayer p=(MediaPlayer)field.get(activity);return p!=null&&p!=beforeThunder&&p.isPlaying();},10);check(true,"Soft thunder recording plays on Android");
            js("stopPreview();page('today')");
            if(uiOnly){
                report("NAV_BOUNDS: "+js("JSON.stringify({bottom:document.querySelector('nav').getBoundingClientRect().bottom,height:innerHeight})"));
                Thread.sleep(400);capture("alarms");
                check("true".equals(js("document.querySelector('nav').getBoundingClientRect().bottom<=innerHeight+1")),"Bottom menu stays within WebView bounds");js("page('sounds');category='Gentle';renderSounds()");Thread.sleep(400);capture("sounds");js("category='Nature';renderSounds()");Thread.sleep(400);capture("nature");js("page('settings')");Thread.sleep(400);capture("settings");js("page('calendar')");Thread.sleep(400);capture("calendar");
                Store.put(c,"current",new JSONObject().put("title","Morning work shift"));Activity previewRing=startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(400);capture("ringing-preview");runOnMainSync(previewRing::finish);Store.put(c,"current",new JSONObject());
                result.putString("summary",checks+" UI/media checks passed");finish(Activity.RESULT_OK,result);return;
            }
            io(()->{try {Store.p(c).edit().putInt("lead",0).putBoolean("enabled",true).commit();Sync.importFile(c,calendar(System.currentTimeMillis()+180000));}catch(Exception e){throw new RuntimeException(e);}});
            JSONObject first=Store.object(c,"scheduled");String old=first.keys().next();check(first.length()==1,"Imported shift schedules exact native alarm");
            io(()->{try{Sync.importFile(c,calendar(System.currentTimeMillis()+360000));}catch(Exception e){throw new RuntimeException(e);}});
            check(!Store.object(c,"scheduled").has(old)&&Store.object(c,"scheduled").length()==1,"Moving shift cancels original alarm and schedules replacement");
            String id=Store.object(c,"scheduled").keys().next();io(()->{try{Scheduler.skip(c,id);}catch(Exception e){throw new RuntimeException(e);}});check(Store.object(c,"scheduled").length()==0,"Skipping shift removes scheduled alarm");
            io(()->{try{Store.cache(c,"");Store.p(c).edit().remove("source").putBoolean("enabled",false).commit();Sync.run(c,false);}catch(Exception e){throw new RuntimeException(e);}});
            js("send('test')");report("LOCK_SCREEN_TEST: 15-second alarm scheduled");
            await(()->Store.object(c,"current").length()>0,22);
            check(Store.object(c,"current").optString("title").equals("Petal Wake sound check"),"Alarm fires through native AlarmManager");
            check(Store.object(c,"ringing").length()==1,"Alarm audio service stays active");
            Activity ring=startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(400);capture("ringing");
            android.service.notification.StatusBarNotification activeAlarm=null;for(android.service.notification.StatusBarNotification notice:((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications())if(notice.getId()==1)activeAlarm=notice;
            check(activeAlarm!=null&&activeAlarm.getNotification().actions!=null&&activeAlarm.getNotification().actions.length==2,"Alarm notification exposes Snooze and I’m up actions");
            check(activeAlarm.getNotification().visibility==Notification.VISIBILITY_PRIVATE&&activeAlarm.getNotification().publicVersion!=null,"Locked-screen alarm notification hides shift details by default");
            check(activeAlarm.getNotification().actions[1].title.toString().equals("I’m up"),"Notification I’m up action is present");activeAlarm.getNotification().actions[1].actionIntent.send();Thread.sleep(400);
            check(Store.object(c,"current").length()>0,"Notification I’m up opens the hold screen without directly stopping the alarm");
            runOnMainSync(()->{android.view.ViewGroup root=(android.view.ViewGroup)((android.view.ViewGroup)ring.findViewById(android.R.id.content)).getChildAt(0);android.widget.Button upper=findButton(root,"Snooze"),lower=findButton(root,"I’m up");if(upper==null||lower==null||upper.getWidth()!=root.getWidth()||lower.getWidth()!=root.getWidth()||Math.abs(upper.getHeight()+lower.getHeight()-root.getHeight())>1)throw new AssertionError("Alarm buttons must fill the screen");});check(true,"Snooze and I’m up fill the full alarm screen width and height");
            Field activeField=AlarmService.class.getDeclaredField("active");activeField.setAccessible(true);AlarmService service=(AlarmService)activeField.get(null);
            Field shaperField=AlarmService.class.getDeclaredField("volumeShaper");shaperField.setAccessible(true);android.media.VolumeShaper shaper=(android.media.VolumeShaper)shaperField.get(service);
            Field beganField=AlarmService.class.getDeclaredField("playbackBegan");beganField.setAccessible(true);long began=(Long)beganField.get(service);
            check(shaper!=null&&shaper.getVolume()>=.15f&&shaper.getVolume()<.20f,"Native audio fade starts near squared 40% gain");report("NATIVE_FADE_START: "+shaper.getVolume());
            await(()->SystemClock.elapsedRealtime()-began>=30000,35);float middle=shaper.getVolume();report("NATIVE_FADE_30_SECONDS: "+middle);check(middle>.46f&&middle<.53f,"Native audio fade rises at its 30-second midpoint");capture("ringing-midpoint");
            await(()->SystemClock.elapsedRealtime()-began>=61000,35);report("NATIVE_FADE_61_SECONDS: "+shaper.getVolume());check(shaper.getVolume()>=.999f,"Native audio fade reaches full gain after one minute");capture("ringing-full-volume");
            android.widget.Button dismiss=findButton(ring.findViewById(android.R.id.content),"I’m up");check(dismiss!=null,"I’m up control is present");
            long earlyDown=touchDown(dismiss);Thread.sleep(600);runOnMainSync(()->checkButtonProgress(dismiss));touchUp(dismiss,earlyDown);Thread.sleep(300);
            check(Store.object(c,"current").length()>0,"Releasing I’m up early cancels dismissal");
            long holdDown=touchDown(dismiss);Thread.sleep(2200);touchUp(dismiss,holdDown);
            await(()->Store.object(c,"current").length()==0,5);check(true,"Holding full-screen I’m up for two seconds stops ringing");
            io(()->{try{JSONObject a=new JSONObject().put("id","qa-snooze").put("title","Snooze test").put("kind","manual").put("at",System.currentTimeMillis()+3000);JSONObject all=new JSONObject().put("qa-snooze",a);Store.p(c).edit().putInt("snooze",1).commit();Store.put(c,"manual",all);Sync.run(c,false);}catch(Exception e){throw new RuntimeException(e);}});
            await(()->Store.object(c,"current").length()>0,10);Activity snoozeScreen=startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{android.widget.Button snooze=findButton(snoozeScreen.findViewById(android.R.id.content),"Snooze");if(snooze!=null)snooze.performClick();});await(()->Store.object(c,"snoozes").length()==1&&Store.object(c,"current").length()==0,8);
            io(()->Sync.run(c,false));check(Store.object(c,"scheduled").length()==1,"Snooze survives calendar refresh");
            report("WAITING_FOR_SNOOZE: one minute");await(()->Store.object(c,"current").optString("title").equals("Snooze test"),70);check(true,"Snoozed alarm fires again");
            c.startService(new Intent(c,AlarmService.class).setAction("STOP"));await(()->Store.object(c,"current").length()==0,5);
            io(()->{Store.p(c).edit().clear().commit();Sync.run(c,false);});
            report("ALL_CHECKS_PASSED");result.putString("summary",checks+" Android smoke checks passed");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    private void checkButtonProgress(android.widget.Button button){if(!button.getText().toString().contains("Hold to stop")||!button.getText().toString().contains("seconds"))throw new AssertionError("Dismiss button must show live hold progress");}
}
