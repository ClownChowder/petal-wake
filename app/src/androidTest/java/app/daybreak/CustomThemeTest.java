package app.daybreak;

import dev.fourwags.rouse.R;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Owned disposable emulator only; creates/removes custom theme fixtures. */
public class CustomThemeTest extends Instrumentation {
 private Activity activity;private WebView web;private int checks;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);checks++;Bundle status=new Bundle();status.putString("stage","PASS: "+label);sendStatus(0,status);}
 private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){WebView w=find(((ViewGroup)v).getChildAt(n));if(w!=null)return w;}return null;}
 private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] value={null};runOnMainSync(()->web.evaluateJavascript(code,out->{value[0]=out;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("JS timeout");return value[0];}
 private void await(Callable<Boolean> predicate,int seconds)throws Exception{long end=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<end){if(predicate.call())return;Thread.sleep(150);}throw new Exception("App state timeout");}
 private void launch()throws Exception{if(activity!=null&&!activity.isDestroyed())runOnMainSync(activity::finish);activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->web=find(activity.findViewById(android.R.id.content)));await(()->"true".equals(js("typeof st==='object'&&!!st")),15);}
 private Map<String,Object> preferences(Context c){Map<String,Object> copy=new HashMap<>();var all=Store.p(c).getAll();for(String k:new String[]{"url","source","manual","scheduled","snoozes","sound","enabled","lead","filter","snooze","fade","volume","vibrate","allDayEnabled","allDayMinutes"})copy.put(k,all.get(k));return copy;}
 private void capture(String name)throws Exception{Thread.sleep(400);var bitmap=getUiAutomation().takeScreenshot();var dir=new java.io.File(getTargetContext().getFilesDir(),"qa");dir.mkdirs();try(var out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();runOnMainSync(()->{Store.p(c).edit().remove("customThemes").putString("appearance","rose").putString("iconDesign","petal").commit();Appearance.syncLauncher(c);});DaybreakApp.IO.submit(()->{Store.put(c,"manual",new JSONObject());Store.put(c,"snoozes",new JSONObject());Store.put(c,"current",new JSONObject());Store.put(c,"ringing",new JSONObject());Sync.run(c,false);}).get(15,TimeUnit.SECONDS);launch();
  String race="manual:delivery-race";JSONObject queued=new JSONObject().put("id",race).put("title","Queued delivery").put("kind","manual").put("at",System.currentTimeMillis()-50);JSONObject pending=new JSONObject().put(race,queued);Store.put(c,"manual",pending);Store.put(c,"scheduled",pending);DaybreakApp.IO.submit(()->Sync.run(c,false)).get(10,TimeUnit.SECONDS);check(Store.object(c,"scheduled").has(race)&&Scheduler.claim(c,race)!=null,"A simultaneous refresh preserves an alarm already queued for delivery");
  Map<String,Object> before=preferences(c);
  js("page('appearance');document.getElementById('newCustomTheme').click()");check("true".equals(js("!document.getElementById('themeEditor').hidden")),"Create opens the custom theme editor");
  js("document.getElementById('customThemeName').value='Night Current';document.getElementById('hexBackground').value='#102A36';document.getElementById('hexPrimary').value='#43C7BD';document.getElementById('hexSecondary').value='#D79ABD';document.getElementById('hexText').value='#F7F1E8';colorParts.forEach(part=>document.getElementById('hex'+part).dispatchEvent(new Event('input',{bubbles:true})));updateThemePreview()");
  check("true".equals(js("getComputedStyle(document.getElementById('customThemePreview')).backgroundColor==='rgb(16, 42, 54)'")),"Live preview uses the chosen background");check(Appearance.current(c)==Appearance.ROSE,"Unsaved color preview does not change the saved theme");capture("custom-editor");
  check("true".equals(js("page('appearance');!$('themeEditor').hidden&&!$('themeUnsavedBar').hidden")),"Unsaved custom theme cannot leave through navigation");
  check("true".equals(js("handleAndroidBack();!$('themeEditor').hidden")),"Android Back keeps the unsaved custom theme editor open");
  js("document.getElementById('saveCustomTheme').click()");await(()->Appearance.current(c).customColors!=null,5);await(()->"true".equals(js("!document.getElementById('appearance').hidden&&customThemes.length===1")),5);String id=Appearance.current(c).id;
  check("Night Current".equals(Appearance.current(c).name),"Named custom theme is saved and selected");check("true".equals(js("getComputedStyle(document.documentElement).backgroundColor==='rgb(16, 42, 54)'&&document.getElementById('brandMark').src.startsWith('data:image/svg+xml')")),"Whole app and in-app icon use the exact saved custom colors");
  check("true".equals(js("JSON.stringify(draftTheme().tokens)===JSON.stringify(customThemes[0].tokens)")),"Live preview and native saved color calculations agree");
  for(String shape:Appearance.ICONS){js("document.querySelector('[data-icon="+shape+"].icon-option').click()");await(()->Appearance.icon(c).equals(shape)&&"true".equals(js("document.documentElement.dataset.icon==='"+shape+"'")),5);check(Appearance.current(c).id.equals(id)&&"true".equals(js("decodeURIComponent(document.getElementById('brandMark').src).includes('#43C7BD')")),"Custom palette is retained by icon design "+shape);}
  var entries=c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName()),0);check(entries.size()==1&&entries.get(0).activityInfo.name.equals("app.daybreak."+Appearance.current(c).launcher("calendar")),"One app-drawer icon uses the documented closest built-in palette");
  launch();js("page('appearance');document.querySelector('[data-custom-edit]').click();document.getElementById('hexPrimary').value='#FFA861';document.getElementById('customThemeName').value='Night Current edited';updateThemePreview();document.getElementById('saveCustomTheme').click()");await(()->Appearance.current(c).name.equals("Night Current edited"),5);check(Appearance.customThemes(c).length()==1&&Appearance.current(c).customColors.primary.equals("#FFA861"),"Editing updates the same saved theme instead of creating a duplicate");
  js("document.querySelector('[data-icon=moon].icon-option').click()");await(()->Appearance.icon(c).equals("moon"),5);
  Thread.sleep(800);runOnMainSync(activity::finish);launch();check(Appearance.current(c).id.equals(id)&&Appearance.icon(c).equals("moon"),"Custom theme and icon design persist after reopening");check("true".equals(js("document.documentElement.dataset.profile===st.settings.appearance&&getComputedStyle(document.documentElement).backgroundColor==='rgb(16, 42, 54)'")),"Saved custom colors restore at launch");
  js("page('appearance');document.querySelector('[data-custom-edit]').click();document.getElementById('hexPrimary').value='bad';document.getElementById('hexPrimary').dispatchEvent(new Event('input'));document.getElementById('saveCustomTheme').click()");Thread.sleep(250);check(Appearance.current(c).customColors.primary.equals("#FFA861"),"Invalid hex input cannot overwrite the saved theme");
  check("true".equals(js("$('cancelCustomTheme').click();!$('themeEditor').hidden&&!$('themeUnsavedBar').hidden")),"Invalid unsaved theme cannot be abandoned with Cancel");
  check("true".equals(js("$('hexPrimary').value='#FFA861';$('hexPrimary').dispatchEvent(new Event('input'));$('cancelCustomTheme').click();!$('appearance').hidden")),"Restoring original theme values permits leaving");
  check(before.equals(preferences(c)),"Custom theme and icon operations preserve alarm and calendar settings");
  Bundle diagnostic=new Bundle();diagnostic.putString("stage","ALARM_SETUP "+js("JSON.stringify({permissions:st.permissions,busy,disabled:document.getElementById('testAlarm').disabled})"));sendStatus(0,diagnostic);js("page('settings');document.getElementById('testAlarm').click()");await(()->Store.object(c,"scheduled").length()>0,10);check(true,"Custom-theme test alarm is armed in Android");js("page('appearance');document.querySelector('[data-icon=petal].icon-option').click()");await(()->Store.object(c,"current").length()>0,22);await(()->c.getSystemService(NotificationManager.class).getActiveNotifications().length==1&&c.getSystemService(NotificationManager.class).getActiveNotifications()[0].getNotification().color==Appearance.current(c).surface,5);check(true,"A scheduled native alarm fires with the custom notification palette");c.startService(new Intent(c,AlarmService.class).setAction("STOP"));await(()->Store.object(c,"current").length()==0,5);
  launch();js("page('appearance');document.querySelector('[data-custom-delete]').click();document.getElementById('yesConfirm').click()");await(()->Appearance.customThemes(c).length()==0&&Appearance.current(c)==Appearance.ROSE,5);check(Appearance.current(c)==Appearance.ROSE,"Deleting the selected custom theme restores the Rose palette");capture("custom-saved-section");
  result.putString("summary",checks+" custom-theme checks passed");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
