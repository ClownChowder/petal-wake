package app.daybreak;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Disposable emulator only. Changes all bundled palettes/shapes and fires one native test alarm. */
public class AppearanceTest extends Instrumentation {
    private Activity activity;private WebView web;private int checks,combinations;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void check(boolean value,String message)throws Exception{if(!value)throw new Exception(message);checks++;Bundle b=new Bundle();b.putString("stage","PASS: "+message);sendStatus(0,b);}
    private WebView findWeb(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){for(int n=0;n<((ViewGroup)v).getChildCount();n++){WebView w=findWeb(((ViewGroup)v).getChildAt(n));if(w!=null)return w;}}return null;}
    private String js(String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] out={null};runOnMainSync(()->web.evaluateJavascript(script,value->{out[0]=value;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("JS timeout");return out[0];}
    private void await(Callable<Boolean> test,int seconds)throws Exception{long end=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<end){if(test.call())return;Thread.sleep(100);}throw new Exception("App state timeout");}
    private void launch()throws Exception{activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->web=findWeb(activity.findViewById(android.R.id.content)));await(()->"true".equals(js("typeof st==='object'&&st!==null&&!!st.settings")),15);}
    private void capture(String name)throws Exception{Thread.sleep(500);var bitmap=getUiAutomation().takeScreenshot();java.io.File dir=new java.io.File(getTargetContext().getFilesDir(),"qa");dir.mkdirs();try(var out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void select(Appearance p,String shape)throws Exception{
        js("document.querySelector('.theme-option[data-profile="+p.id+"]').click()");await(()->Appearance.current(getTargetContext())==p,5);
        js("document.querySelector('.icon-option[data-icon="+shape+"]').click()");
        await(()->Appearance.icon(getTargetContext()).equals(shape)&&"true".equals(js("document.documentElement.dataset.profile==='"+p.id+"'&&document.documentElement.dataset.icon==='"+shape+"'")),5);
    }
    private boolean launcherMatches(Appearance p,String shape){Context c=getTargetContext();var entries=c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName()),0);return entries.size()==1&&entries.get(0).activityInfo.name.equals("app.daybreak."+p.launcher(shape))&&p.iconResource(c,shape)!=0&&entries.get(0).activityInfo.getIconResource()==p.iconResource(c,shape);}
    private boolean renderedMatches(Appearance p,String shape)throws Exception{
        String rgb="rgb("+Color.red(p.background)+", "+Color.green(p.background)+", "+Color.blue(p.background)+")";
        String script="getComputedStyle(document.documentElement).backgroundColor==='"+rgb+"'&&getComputedStyle(document.documentElement).colorScheme==='"+(p.dark?"dark":"light")+"'&&document.getElementById('brandMark').getAttribute('src')===iconPath('"+p.id+"','"+shape+"')&&document.querySelectorAll('.theme-option[aria-pressed=true]').length===1&&document.querySelectorAll('.icon-option[aria-pressed=true]').length===1&&Array.from(document.querySelectorAll('.icon-option img')).every(i=>i.getAttribute('src')===iconPath('"+p.id+"',i.closest('.icon-option').dataset.icon))&&Array.from(document.querySelectorAll('.theme-option img')).every(i=>i.getAttribute('src')===iconPath(i.closest('.theme-option').dataset.profile,'"+shape+"'))";
        return "true".equals(js(script))&&((ColorDrawable)activity.getWindow().getDecorView().getBackground()).getColor()==p.background;
    }
    private Button button(Activity a,int n){return (Button)((ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(n);}
    private int background(Button b){return ((GradientDrawable)((RippleDrawable)b.getBackground()).getDrawable(0)).getColor().getDefaultColor();}
    private Map<String,Object> preserved(Context c){Map<String,?> all=Store.p(c).getAll();Map<String,Object> selected=new HashMap<>();for(String key:new String[]{"url","source","enabled","lead","filter","snooze","volume","fade","vibrate","sound","allDayEnabled","allDayMinutes","manual","scheduled","snoozes"})selected.put(key,all.get(key));return selected;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();launch();check(Appearance.current(c)==Appearance.ROSE&&Appearance.icon(c).equals("petal"),"Upgrade preserves Rose & Ivory and defaults to the previously selected Petal icon");
        js("page('settings');document.getElementById('openAppearance').click()");check("true".equals(js("!document.getElementById('appearance').hidden&&document.querySelector('nav [data-page=settings]').getAttribute('aria-current')==='page'")),"Settings opens a dedicated Appearance screen with Settings navigation selected");
        check("16".equals(js("document.querySelectorAll('.theme-option').length"))&&"6".equals(js("document.querySelectorAll('.icon-option').length")),"Exactly 16 themes and all six original icon designs are available");capture("twenty-picker-rose");
        Map<String,Object> before=preserved(c);js("document.getElementById('lead').value='37';document.getElementById('lead').dispatchEvent(new Event('input',{bubbles:true}))");
        for(Appearance p:Appearance.ALL){
            for(String shape:Appearance.ICONS){select(p,shape);if(!launcherMatches(p,shape)||!renderedMatches(p,shape))throw new Exception("Theme/icon mismatch: "+p.id+"/"+shape);combinations++;}
            check(true,"All six shapes use the correct single Android launcher icon and rendered palette: "+p.id);
            if(Arrays.asList("navy","rose","ocean","lavender","forest","graphite").contains(p.id)){js("window.scrollTo(0,0)");capture("twenty-"+p.id);}
        }
        check(combinations==96,"All 96 theme and launcher-icon combinations passed");check("\"37\"".equals(js("document.getElementById('lead').value")),"Theme and icon changes preserve unsaved alarm preferences");check(before.equals(preserved(c)),"All saved calendar, alarm and playback preferences remain unchanged");
        boolean[] rejected={false};runOnMainSync(()->{try{Appearance.chooseIcon(c,"../unknown");}catch(IllegalArgumentException expected){rejected[0]=true;}});check(rejected[0]&&Appearance.icon(c).equals("calendar"),"Unknown icon choices are rejected without changing the selection");
        runOnMainSync(activity::finish);
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);Appearance saved=Appearance.current(c);String savedShape=Appearance.icon(c);
        c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(c.getPackageName(),"app.daybreak."+saved.launcher(savedShape))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activity=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(activity instanceof MainActivity,"Selected custom launcher alias opens the real app");runOnMainSync(()->web=findWeb(activity.findViewById(android.R.id.content)));await(()->"true".equals(js("typeof st==='object'&&st!==null&&!!st.settings")),15);check(renderedMatches(saved,savedShape),"Independent theme and shape persist after reopening from the launcher");boolean correctTask=false;for(var task:((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getAppTasks()){var info=task.getTaskInfo();if(info.taskId==activity.getTaskId()&&info.baseIntent.getComponent()!=null&&info.baseIntent.getComponent().getClassName().equals(MainActivity.class.getName()))correctTask=true;}check(correctTask,"Real app task is rooted in MainActivity rather than a switchable icon alias");
        Store.put(c,"current",new JSONObject().put("title","Morning work shift"));Activity ring=startActivitySync(new Intent(c,RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(Appearance p:Appearance.ALL){runOnMainSync(()->Appearance.choose(c,p.id));await(()->background(button(ring,0))==p.surface&&background(button(ring,1))==p.action,5);if(button(ring,0).getCurrentTextColor()!=p.text||button(ring,1).getCurrentTextColor()!=p.onAction)throw new Exception("Alarm text colors: "+p.id);}
        check(true,"Native full-screen alarm recolors correctly across all 16 themes");runOnMainSync(()->Appearance.choose(c,"ocean"));Thread.sleep(200);capture("twenty-ring-light");runOnMainSync(()->Appearance.choose(c,"rose"));Thread.sleep(200);capture("twenty-ring-dark");runOnMainSync(ring::finish);Store.put(c,"current",new JSONObject());
        js("send('state');page('settings');document.getElementById('testAlarm').click()");await(()->Store.object(c,"manual").length()>0,5);js("page('appearance')");select(Appearance.NAVY,"moon");select(Appearance.ALL[15],"dawn");await(()->Store.object(c,"current").length()>0,22);check(true,"Native exact alarm fires after changing both theme and icon while armed");
        var nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);await(()->nm.getActiveNotifications().length==1&&nm.getActiveNotifications()[0].getNotification().color==Appearance.current(c).surface,5);check(true,"Alarm notification uses the selected light theme");runOnMainSync(()->Appearance.choose(c,"rose"));await(()->nm.getActiveNotifications()[0].getNotification().color==Appearance.ROSE.surface,5);check(AlarmService.playbackPercent()>0,"Notification recolors from light to dark without interrupting native playback");c.startService(new Intent(c,AlarmService.class).setAction("STOP"));await(()->Store.object(c,"current").length()==0,5);
        runOnMainSync(()->Appearance.chooseIcon(c,"petal"));js("send('state');page('appearance');document.getElementById('appearanceBack').click()");check("true".equals(js("!document.getElementById('settings').hidden")),"Appearance back button returns to Settings");
        result.putString("summary",checks+" appearance checks passed; "+combinations+" launcher/theme combinations verified");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
