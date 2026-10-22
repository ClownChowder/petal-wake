package app.daybreak;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Disposable owned emulator only: checks real UI actions, compact layout and preserved preferences. */
public class QuietFocusTest extends Instrumentation {
 private Activity activity;private WebView web;private int checks;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);checks++;Bundle b=new Bundle();b.putString("stage","PASS: "+label);sendStatus(0,b);}
 private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=find(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
 private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] out={null};runOnMainSync(()->web.evaluateJavascript(code,value->{out[0]=value;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("JS timeout");return out[0];}
 private void await(Callable<Boolean> test,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if(test.call())return;Thread.sleep(100);}throw new Exception("UI state timeout");}
 private void io(Runnable work)throws Exception{DaybreakApp.IO.submit(work).get(15,TimeUnit.SECONDS);}
 private String stamp(long ms){return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));}
 private void capture(String name)throws Exception{Thread.sleep(250);var bitmap=getUiAutomation().takeScreenshot();var dir=new java.io.File(getTargetContext().getFilesDir(),"qa");dir.mkdirs();try(var out=new java.io.FileOutputStream(new java.io.File(dir,"quiet-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
 private void layout(String page)throws Exception{
  js("page('"+page+"')");Thread.sleep(120);
  String result=js("JSON.stringify({width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth+1,buttons:Array.from(document.querySelectorAll('#"+page+" button,nav button')).filter(b=>b.getBoundingClientRect().height>0).filter(b=>b.getBoundingClientRect().height<43).map(b=>b.id||b.textContent.trim()),font:getComputedStyle(document.querySelector('#"+page+" h1')).fontFamily})");
  JSONObject state=new JSONObject(new JSONArray("["+result+"]").getString(0));
  check(!state.getBoolean("overflow")&&state.getJSONArray("buttons").length()==0,"No horizontal overflow and all visible buttons have 44px targets: "+page+" at "+state.getInt("width")+"px");
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();io(()->{Store.p(c).edit().putBoolean("enabled",true).putInt("lead",90).putString("appearance","violet").putString("iconDesign","petal").remove("customThemes").commit();Appearance.syncLauncher(c);try{long base=System.currentTimeMillis()+86400000;StringBuilder ics=new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Petal Wake QA//EN\r\n");for(int n=0;n<5;n++)ics.append("BEGIN:VEVENT\r\nUID:quiet-").append(n).append("\r\nDTSTART:").append(stamp(base+n*86400000L)).append("\r\nDURATION:PT8H\r\nSUMMARY:").append(n==4?"A long work shift title to check wrapping on compact screens":"Work shift").append("\r\nEND:VEVENT\r\n");ics.append("END:VCALENDAR\r\n");Sync.importFile(c,ics.toString());}catch(Exception e){throw new RuntimeException(e);}});
  activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->web=find(activity.findViewById(android.R.id.content)));await(()->"true".equals(js("!!st&&st.shifts.length===5")),15);
  check("true".equals(js("document.querySelector('.alarm-time')&&document.querySelector('.hero-work').textContent.includes('90 minutes')&&getComputedStyle(document.querySelector('.hero')).backgroundImage==='none'")),"Quiet Focus hero shows the real next alarm and lead time without decorative background");
  check("true".equals(js("document.querySelectorAll('#shiftList .alarm-row').length===3&&!$('viewAllShifts').hidden")),"Home shows three upcoming rows with a path to the full calendar");capture("alarms");
  String first=Store.object(c,"scheduled").keys().next();String nextId=new JSONArray("["+js("Object.values(st.scheduled).sort((a,b)=>a.at-b.at)[0].id")+"]").getString(0);
  js("$('heroAction').click()");await(()->!Store.object(c,"scheduled").has(nextId)&&Store.object(c,"skipped").has(nextId),8);await(()->"true".equals(js("!busy&&$('shiftList').querySelector('[data-alarm-action=unskip]')!==null")),8);check(true,"Hero Skip once cancels the next work alarm through the native scheduler");
  js("$('shiftList').querySelector('[data-alarm-action=unskip]').click()");await(()->Store.object(c,"scheduled").has(nextId),8);check(!Store.object(c,"skipped").has(nextId),"Restore in the upcoming list re-arms the skipped native alarm");
  js("$('viewAllShifts').click()");check("true".equals(js("!$('calendar').hidden&&document.querySelectorAll('#calendarShiftList .alarm-row').length===5")),"View all shifts opens Calendar with every fixture shift");capture("calendar");
  js("page('settings');$('lead').value='47';$('settingsForm').dispatchEvent(new Event('input',{bubbles:true}));page('sounds');page('settings')");check("\"47\"".equals(js("$('lead').value"))&&Store.p(c).getInt("lead",0)==90,"Navigation preserves an unsaved preference without silently applying it");capture("settings");
  js("dirty=false;send('state');page('sounds');category='Gentle';renderSounds();$('soundSearch').value='piano';$('soundSearch').dispatchEvent(new Event('input',{bubbles:true}))");check("true".equals(js("document.querySelectorAll('.sound-row').length>0&&Array.from(document.querySelectorAll('.play-button')).every(b=>b.querySelector('svg'))")),"Sound search filters real recordings and keeps accessible SVG preview controls");js("$('soundSearch').value='';renderSounds()");capture("sounds");
  for(String page:new String[]{"today","calendar","sounds","settings","appearance","themeEditor"}){if(page.equals("themeEditor"))js("openThemeEditor()");layout(page);if(page.equals("appearance"))capture("appearance");if(page.equals("themeEditor"))capture("editor");}
  int densityWidth=Math.round(320*c.getResources().getDisplayMetrics().density);runOnMainSync(()->{web.setLayoutParams(new FrameLayout.LayoutParams(densityWidth,-1));web.getSettings().setTextZoom(130);});await(()->"true".equals(js("innerWidth<=321")),5);
  for(String page:new String[]{"today","calendar","sounds","settings","appearance","themeEditor"})layout(page);capture("compact");
  runOnMainSync(()->{web.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));web.getSettings().setTextZoom(100);});
  js("page('today');$('addManual').click()");check("true".equals(js("$('manualDialog').open&&$('manualAt').value.length>0")),"Add alarm opens the personal alarm dialog with a future time");js("$('manualDialog').querySelector('.closeDialog').click();page('settings')");
  check(Store.p(c).getInt("lead",0)==90&&Store.p(c).getString("appearance","").equals("violet")&&Store.object(c,"scheduled").length()==5,"Design navigation retains the chosen theme and all five scheduled shift alarms");
  result.putString("summary",checks+" Quiet Focus checks passed");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
