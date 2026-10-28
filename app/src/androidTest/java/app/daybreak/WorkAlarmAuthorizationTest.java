package app.daybreak;

import dev.fourwags.rouse.R;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.accessibilityservice.AccessibilityService;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/** Only run on the owned emulator with its disposable test PIN 2468. */
public class WorkAlarmAuthorizationTest extends Instrumentation {
    private Context c;private Activity activity;private WebView web;private int checks;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);checks++;Bundle b=new Bundle();b.putString("stage","PASS: "+label);sendStatus(0,b);}
    private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){WebView w=find(((ViewGroup)v).getChildAt(n));if(w!=null)return w;}return null;}
    private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] out={null};runOnMainSync(()->web.evaluateJavascript(code,value->{out[0]=value;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("JS timeout");return out[0];}
    private void await(Callable<Boolean> test,int seconds)throws Exception{long end=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<end){if(test.call())return;Thread.sleep(100);}throw new Exception("Timed out waiting for authentication state");}
    private void io(Runnable action)throws Exception{DaybreakApp.IO.submit(action).get(15,TimeUnit.SECONDS);}
    private boolean enabled(){return Store.p(c).getBoolean("enabled",false);}
    private void command(String command)throws Exception{try(var fd=getUiAutomation().executeShellCommand(command);var in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){in.readAllBytes();}}
    private void pin(String value)throws Exception{Thread.sleep(800);command("input text "+value);command("input keyevent KEYCODE_ENTER");}
    private void pending()throws Exception{await(()->"true".equals(js("!!st.authorizing")),5);Thread.sleep(1000);}
    private void cancel()throws Exception{getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);Thread.sleep(400);if("true".equals(js("!!st.authorizing")))getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);await(()->"false".equals(js("!!st.authorizing")),8);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        c=getTargetContext();check(c.getSystemService(KeyguardManager.class).isDeviceSecure(),"Disposable emulator screen lock is configured");
        String day=LocalDate.now().plusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE);
        String calendar="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Petal Wake Auth QA//EN\r\nBEGIN:VEVENT\r\nUID:auth-qa\r\nDTSTART:"+day+"T120000Z\r\nDURATION:PT8H\r\nSUMMARY:Authentication work fixture\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        io(()->{try{for(var ids=Store.object(c,"scheduled").keys();ids.hasNext();)Scheduler.cancel(c,ids.next());Store.p(c).edit().clear().putBoolean("enabled",true).putInt("playbackRevision",1).commit();Sync.importFile(c,calendar);String id="manual:auth-qa";Store.put(c,"manual",new JSONObject().put(id,new JSONObject().put("id",id).put("kind","manual").put("title","Personal QA").put("at",System.currentTimeMillis()+3*86400000L)));Sync.run(c,false);}catch(Exception e){throw new RuntimeException(e);}});
        activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->web=find(activity.findViewById(android.R.id.content)));await(()->"true".equals(js("!!st&&st.settings.enabled")),15);
        String armed=Store.object(c,"scheduled").toString();check(Store.object(c,"scheduled").length()==2,"Fixture work and personal alarms are scheduled");
        js("$('enabled').click()");pending();check(enabled()&&armed.equals(Store.object(c,"scheduled").toString()),"Requesting disable leaves saved setting and native schedule intact before authentication");check("true".equals(js("$('enabled').checked&&$('enabled').disabled&&$('enabled').getAttribute('aria-checked')==='true'")),"Switch stays visibly and accessibly on while Android authentication is pending");
        cancel();check(enabled()&&armed.equals(Store.object(c,"scheduled").toString()),"Canceling authentication leaves work alarms scheduled");check("true".equals(js("$('enabled').checked&&!$('enabled').disabled")),"Cancel restores an operable enabled switch");
        js("send('enable',{value:false,authenticated:true})");pending();check(enabled(),"A caller-supplied authenticated flag cannot bypass native authentication");
        pin("2460");Thread.sleep(700);check(enabled(),"An incorrect device PIN cannot turn work alarms off");
        pin("2468");await(()->!enabled(),10);io(()->{});check(Store.object(c,"scheduled").length()==1&&Store.array(c,"shifts").length()==1&&Store.cache(c).equals(calendar),"Correct Android PIN pauses work alarms while retaining calendar and personal alarm");
        await(()->"true".equals(js("!$('enabled').disabled&&!$('enabled').checked")),5);js("$('enabled').click()");await(()->enabled()&&Store.object(c,"scheduled").length()==2,5);check("false".equals(js("!!st.authorizing")),"Enabling work alarms is immediate and requires no authentication");
        js("send('disconnect')");pending();check(enabled()&&Store.cache(c).equals(calendar),"Disconnecting an active work calendar requires a fresh Android authentication");cancel();check(enabled()&&Store.cache(c).equals(calendar),"Canceling calendar disconnect preserves connection and enabled work alarms");
        js("send('disconnect')");pending();pin("2468");await(()->!enabled()&&Store.p(c).getString("source","").isEmpty(),10);io(()->{});check(Store.cache(c).isEmpty()&&Store.object(c,"scheduled").length()==1,"Authenticated disconnect removes calendar and work alarms but keeps personal alarm");
        command("locksettings clear --old 2468");check(!c.getSystemService(KeyguardManager.class).isDeviceSecure(),"Disposable emulator screen lock removed for unavailable-auth test");io(()->{Store.p(c).edit().putBoolean("enabled",true).commit();try{Sync.importFile(c,calendar);}catch(Exception e){throw new RuntimeException(e);}});js("send('state')");await(()->"true".equals(js("st.settings.enabled")),5);js("$('enabled').click()");Thread.sleep(500);check(enabled()&&Store.object(c,"scheduled").length()==2,"Missing device screen lock cannot silently disable work alarms");check("true".equals(js("$('enabled').checked&&!$('enabled').disabled&&!st.authorizing")),"Unavailable authentication leaves the switch on and responsive");
        result.putString("summary",checks+" work alarm authentication checks passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
