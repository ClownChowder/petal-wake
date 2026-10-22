package app.daybreak;
import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/** Owned disposable emulator only. Real HTTP/scheduler/notices with isolated fixture data. */
public class ConfidenceTest extends Instrumentation {
 private Context c;private Activity activity;private WebView web;private int checks;private volatile int code=200;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);checks++;Bundle b=new Bundle();b.putString("stage","PASS: "+label);sendStatus(0,b);}
 private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){WebView w=find(((ViewGroup)v).getChildAt(n));if(w!=null)return w;}return null;}
 private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] out={null};runOnMainSync(()->web.evaluateJavascript(code,value->{out[0]=value;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("JS timeout");return out[0];}
 private void await(Callable<Boolean> predicate,int seconds)throws Exception{long end=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<end){if(predicate.call())return;Thread.sleep(100);}throw new Exception("Expected state timeout");}
 private void io(Runnable work)throws Exception{DaybreakApp.IO.submit(work).get(15,TimeUnit.SECONDS);}
 private void launch()throws Exception{activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->web=find(activity.findViewById(android.R.id.content)));await(()->"true".equals(js("!!st&&!!st.calendarHealth")),15);io(()->{});}
 private Notification notice(){for(var n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==CalendarConfidence.NOTICE)return n.getNotification();return null;}
 private void update()throws Exception{js("send('state')");Thread.sleep(150);}
 private void capture(String name)throws Exception{Thread.sleep(300);var b=getUiAutomation().takeScreenshot();var dir=new File(c.getFilesDir(),"qa");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,"confidence-"+name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  c=getTargetContext();String day=LocalDate.now().plusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE);String calendar="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Petal Wake Confidence QA//EN\r\nBEGIN:VEVENT\r\nUID:confidence\r\nDTSTART:"+day+"T120000Z\r\nDURATION:PT8H\r\nSUMMARY:Private work fixture\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
  URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return code;}public InputStream getInputStream(){return new ByteArrayInputStream(calendar.getBytes(StandardCharsets.UTF_8));}};}});
  io(()->{var scheduled=Store.object(c,"scheduled");for(var ids=scheduled.keys();ids.hasNext();)Scheduler.cancel(c,ids.next());Store.p(c).edit().clear().putString("appearance","violet").putString("iconDesign","petal").putBoolean("enabled",true).putInt("lead",90).putInt("volume",100).putInt("fade",60).putBoolean("vibrate",true).putInt("playbackRevision",1).commit();Appearance.syncLauncher(c);try{Sync.connect(c,"https://fixture.invalid/private-token.ics");}catch(Exception e){throw new RuntimeException(e);}});launch();
  check(CalendarConfidence.snapshot(c).getString("status").equals("fresh")&&Store.object(c,"scheduled").length()==1,"Successful download records confidence and schedules the fixture shift");
  check("true".equals(js("!$('calendarConfidence').hidden&&$('confidenceTitle').textContent==='Calendar downloaded'&&$('confidenceTime').textContent.includes('Last download')")),"Home places download confidence beside the next alarm");capture("home");
  String armed=Store.object(c,"scheduled").toString();code=403;io(()->Sync.run(c,true));long last=Store.p(c).getLong("lastSync",0);check(CalendarConfidence.snapshot(c).getString("status").equals("failed")&&notice()==null,"One temporary failure is visible without immediately notifying");
  io(()->Sync.run(c,false));check(CalendarConfidence.snapshot(c).getString("status").equals("failed")&&Store.p(c).getLong("lastSync",0)==last,"Local alarm/settings reconciliation cannot erase failed-download confidence");check(armed.equals(Store.object(c,"scheduled").toString()),"Failed downloads preserve every saved scheduled fixture alarm");
  Store.p(c).edit().putLong("syncFirstFailure",System.currentTimeMillis()-16*60_000L).commit();io(()->Sync.run(c,true));await(()->notice()!=null,5);Notification n=notice();check(n!=null,"Repeated failures after the grace period post a calendar notice");
  var channel=c.getSystemService(NotificationManager.class).getNotificationChannel(CalendarConfidence.CHANNEL);check(channel.getImportance()==NotificationManager.IMPORTANCE_LOW&&channel.getSound()==null&&!channel.shouldVibrate(),"Calendar notices use a separate silent non-vibrating channel");String visible=String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE))+n.extras.getCharSequence(Notification.EXTRA_TEXT)+n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);check(!visible.contains("private-token")&&!visible.contains("Private work fixture")&&n.visibility==Notification.VISIBILITY_PRIVATE,"Calendar notice contains neither sharing link nor shift title and is private on the lock screen");
  c.getSystemService(NotificationManager.class).cancel(CalendarConfidence.NOTICE);io(()->Sync.run(c,false));check(notice()==null&&Store.p(c).getBoolean("syncAlertActive",false),"A dismissed notice is not reposted for the same unresolved issue");
  code=200;io(()->Sync.run(c,true));check(CalendarConfidence.snapshot(c).getString("status").equals("fresh")&&!Store.p(c).getBoolean("syncAlertActive",true),"A successful refresh clears failure state and rearms future issue notifications");
  Store.p(c).edit().putLong("lastSync",System.currentTimeMillis()-9*60*60_000L).commit();io(()->Sync.run(c,false));check(CalendarConfidence.snapshot(c).getString("status").equals("stale")&&notice()!=null,"An eight-hour stale download is identified and produces one notice");
  update();js("page('settings');$('syncAlerts').checked=false;$('syncAlerts').dispatchEvent(new Event('change',{bubbles:true}))");await(()->!Store.p(c).getBoolean("syncAlerts",true)&&notice()==null,5);check(true,"The in-app notice preference immediately cancels sync notices");
  js("$('syncAlerts').checked=true;$('syncAlerts').dispatchEvent(new Event('change',{bubbles:true}))");await(()->notice()!=null,5);Notification open=notice();code=403;open.contentIntent.send();await(()->"true".equals(js("!$('calendar').hidden")),5);check(true,"Tapping a calendar notice opens the Calendar screen instead of the home page");capture("calendar");
  code=200;io(()->Sync.run(c,true));check(notice()==null,"Recovery removes an active stale/failure notification");
  String sound=SoundLibrary.current(c);boolean vibration=Store.p(c).getBoolean("vibrate",false);int system=c.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM);update();
  js("page('settings');$('lead').value='47';$('settingsForm').dispatchEvent(new Event('input',{bubbles:true}));$('useQuietPreset').click()");await(()->Store.p(c).getBoolean("quietPreset",false)&&"true".equals(js("$('volume').value==='70'&&$('fade').value==='60'")),5);
  check(Store.p(c).getInt("volume",0)==70&&Store.p(c).getInt("fade",0)==60&&Store.p(c).getInt("lead",0)==90&&"\"47\"".equals(js("$('lead').value")),"Quiet preset saves its ceiling/rise while preserving an unsaved work preference");check(SoundLibrary.current(c).equals(sound)&&Store.p(c).getBoolean("vibrate",false)==vibration&&c.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM)==system,"Quiet preset preserves the chosen sound, vibration and Android alarm-stream volume");capture("quiet-preset");
  js("$('restorePlayback').click()");await(()->!Store.p(c).getBoolean("quietPreset",true)&&Store.p(c).getInt("volume",0)==100,5);check(Store.object(c,"previousPlayback").length()==0,"Previous playback can be restored without changing calendar settings");
  js("dirty=false;send('state');$('useQuietPreset').click()");await(()->Store.p(c).getBoolean("quietPreset",false),5);js("page('settings');$('testAlarm').click()");await(()->Store.object(c,"current").length()>0,22);await(()->AlarmService.playbackPercent()>=28&&AlarmService.playbackPercent()<40,10);
  var f=AlarmService.class.getDeclaredField("active");f.setAccessible(true);Object service=f.get(null);var target=AlarmService.class.getDeclaredField("targetVolume");target.setAccessible(true);check(Math.abs(target.getFloat(service)-.70f)<.001f&&AlarmService.playbackPercent()<40,"A real quiet-preset alarm starts at its relative 40% level and respects the 70% ceiling");c.startService(new Intent(c,AlarmService.class).setAction("STOP"));await(()->Store.object(c,"current").length()==0,5);
  PlaybackPresets.restore(c);io(()->{try{Sync.importFile(c,calendar);}catch(Exception e){throw new RuntimeException(e);}});check(CalendarConfidence.snapshot(c).getString("status").equals("imported")&&notice()==null,"Imported snapshots are labelled explicitly and never get stale-download notices");update();js("page('today')");check("true".equals(js("$('confidenceTitle').textContent==='Imported calendar'&&$('confidenceTime').textContent.includes('importing again')")),"Snapshot status explains that importing again is required");
  io(()->{Store.p(c).edit().remove("url").remove("source").remove("lastSync").putBoolean("enabled",false).commit();try{Store.cache(c,"");}catch(Exception e){throw new RuntimeException(e);}CalendarConfidence.reset(c);Sync.run(c,false);});check(CalendarConfidence.snapshot(c).getString("status").equals("unlinked")&&notice()==null,"Disconnect clears confidence and its notification");
  result.putString("summary",checks+" calendar confidence and quiet-preset checks passed");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
