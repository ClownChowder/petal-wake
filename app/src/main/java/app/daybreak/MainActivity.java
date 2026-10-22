package app.daybreak;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private WorkAlarmAuthorization workAlarmAuthorization;
    private boolean pageReady;private String pendingPage;private WebView web;private MediaPlayer preview;private VolumeShaper previewShaper;private android.widget.FrameLayout host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){state();}};
    @Override public void onCreate(Bundle saved){
        setTheme(Appearance.current(this).theme);super.onCreate(saved);
        int background=Appearance.current(this).background;getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        web=new WebView(this);web.setBackgroundColor(background);
        host=new android.widget.FrameLayout(this);host.setBackgroundColor(background);host.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=35)host.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);s.setDomStorageEnabled(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}@Override public void onPageFinished(WebView v,String url){pageReady=true;state();if(pendingPage!=null){web.evaluateJavascript("window.openPage&&window.openPage("+JSONObject.quote(pendingPage)+")",null);pendingPage=null;}}});
        setContentView(host);if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);applyAppearance();web.loadUrl("file:///android_asset/index.html#"+Appearance.current(this).id+"/"+Appearance.icon(this));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(DaybreakApp.CHANGED),RECEIVER_NOT_EXPORTED);else registerReceiver(updates,new IntentFilter(DaybreakApp.CHANGED));
        workAlarmAuthorization=new WorkAlarmAuthorization(this,this::state,this::tell);
        incoming(getIntent());
    }
    @Override protected void onResume(){super.onResume();if(web!=null){Readiness.update(this);state();((DaybreakApp)getApplication()).refresh(true);}}
    @Override protected void onPause(){stopPreview();super.onPause();}
    @Override protected void onDestroy(){if(workAlarmAuthorization!=null)workAlarmAuthorization.destroy();unregisterReceiver(updates);stopPreview();web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);incoming(i);}
    private void incoming(Intent i){if(i==null)return;if("calendar".equals(i.getStringExtra("page"))){pendingPage="calendar";if(web!=null&&pageReady){web.evaluateJavascript("window.openPage&&window.openPage(\"calendar\")",null);pendingPage=null;}}Uri uri=null;if(Intent.ACTION_SEND.equals(i.getAction()))uri=i.getParcelableExtra(Intent.EXTRA_STREAM);else if(Intent.ACTION_VIEW.equals(i.getAction()))uri=i.getData();if(uri!=null&&"content".equals(uri.getScheme()))confirmImport(uri);else if(Intent.ACTION_SEND.equals(i.getAction())){String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null){String link=text.trim();if(link.startsWith("https://")||link.startsWith("webcal://"))new AlertDialog.Builder(this).setTitle("Connect shared calendar?").setMessage("Petal Wake will download this calendar link and replace the current calendar connection. Review the shifts before enabling work alarms.").setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w)->background(()->Sync.connect(this,link),"Calendar connected. Review shifts before enabling alarms.")).show();}}}
    private void confirmImport(Uri uri){new AlertDialog.Builder(this).setTitle("Import work calendar?").setMessage("This replaces your current calendar connection with a one-time snapshot. Files do not automatically refresh.").setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->importUri(uri)).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(workAlarmAuthorization!=null&&workAlarmAuthorization.onActivityResult(request,result))return;if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request==10)confirmImport(data.getData());else if(request==20)exportSettings(data.getData());else if(request==21)confirmSettingsImport(data.getData());}
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);state();}
    private void importUri(Uri uri){background(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("File could not be opened.");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4_000_000)throw new IOException("Calendar file is too large.");}Sync.importFile(this,out.toString(StandardCharsets.UTF_8.name()));}},"Calendar imported. Review your shifts, then enable alarms.");}
    private void exportSettings(Uri uri){background(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Settings file could not be created.");out.write(SettingsTransfer.exportJson(this).getBytes(StandardCharsets.UTF_8));}},"Settings saved. Calendar links, events, and alarms were excluded.");}
    private void confirmSettingsImport(Uri uri){new AlertDialog.Builder(this).setTitle("Restore Petal Wake settings?").setMessage("This replaces alarm preferences and themes from the file. Calendar links, work alarms, and personal alarms stay as they are. Review your next alarm afterward.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->background(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("Settings file could not be opened.");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>262_144)throw new IOException("Settings file is too large.");}SettingsTransfer.importJson(this,out.toString(StandardCharsets.UTF_8.name()));}},"Settings restored. Review your next alarm and Android permissions.")).show();}
    private void tell(String message){runOnUiThread(()->{if(isDestroyed()){Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();return;}web.evaluateJavascript("window.toast&&window.toast("+JSONObject.quote(message)+")",null);});}
    private interface Work {void run()throws Exception;}
    private void background(Work work,String message){web.evaluateJavascript("window.setBusy(true)",null);DaybreakApp.IO.execute(()->{try{work.run();if(message!=null)tell(message);}catch(Exception e){String error=e.getMessage()==null?"That action could not be completed.":e.getMessage();Store.error(this,error);tell(error);}finally{runOnUiThread(()->{if(!isDestroyed()){web.evaluateJavascript("window.setBusy&&window.setBusy(false)",null);state();}});}});}
    private boolean notifyAllowed(){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel ch=nm.getNotificationChannel(AlarmService.CHANNEL);return (Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&nm.areNotificationsEnabled()&&(ch==null||ch.getImportance()>=NotificationManager.IMPORTANCE_HIGH);}
    private boolean fullAllowed(){return Build.VERSION.SDK_INT<34||((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).canUseFullScreenIntent();}
    private void state(){
        if(web==null||isDestroyed())return;
        try {
            applyAppearance();var p=Store.p(this);JSONObject settings=new JSONObject().put("appearance",Appearance.current(this).id).put("iconDesign",Appearance.icon(this)).put("enabled",p.getBoolean("enabled",false)).put("lead",p.getInt("lead",90)).put("filter",p.getString("filter","")).put("snooze",p.getInt("snooze",9)).put("volume",p.getInt("volume",80)).put("fade",p.getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS)).put("vibrate",p.getBoolean("vibrate",true)).put("sound",SoundLibrary.playable(this)).put("requestedSound",SoundLibrary.current(this)).put("soundPackInstalled",SoundPacks.installed(this)).put("allDayEnabled",p.getBoolean("allDayEnabled",false)).put("allDayMinutes",p.getInt("allDayMinutes",480)).put("quietPreset",p.getBoolean("quietPreset",false)).put("canRestorePlayback",Store.object(this,"previousPlayback").length()>0).put("shiftRulesEnabled",p.getBoolean("shiftRulesEnabled",false)).put("earlyBefore",p.getInt("earlyBefore",600)).put("earlyLead",p.getInt("earlyLead",90)).put("lateAfter",p.getInt("lateAfter",720)).put("lateLead",p.getInt("lateLead",45)).put("eveningCheck",p.getBoolean("eveningCheck",false)).put("eveningMinutes",p.getInt("eveningMinutes",1140));
            settings.put("fallbackMinutes",p.getInt("fallbackMinutes",0));
            JSONObject permissions=new JSONObject().put("exact",Scheduler.allowed(this)).put("notifications",notifyAllowed()&&((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()).put("fullscreen",fullAllowed()).put("systemVolume",((AudioManager)getSystemService(AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_ALARM));
            JSONArray calendars=new JSONArray();if(CalendarSources.hasPrimary(this))calendars.put(new JSONObject().put("id","primary").put("name",p.getString("primaryName","Work calendar")).put("kind",p.getString("url","").isEmpty()?"Imported snapshot":"Shared link").put("lastSync",p.getLong("lastSync",0)).put("lead",p.getInt("lead",90)).put("filter",p.getString("filter","")).put("allDayEnabled",p.getBoolean("allDayEnabled",false)).put("allDayMinutes",p.getInt("allDayMinutes",480)));
            JSONArray extras=CalendarSources.extras(this);for(int i=0;i<extras.length();i++){JSONObject extra=extras.getJSONObject(i);calendars.put(new JSONObject().put("id",extra.optString("id")).put("name",extra.optString("name")).put("kind","Shared link").put("lastSync",extra.optLong("lastSync")).put("error",extra.optString("error")).put("lead",extra.optInt("lead",90)).put("filter",extra.optString("filter")).put("allDayEnabled",extra.optBoolean("allDayEnabled")).put("allDayMinutes",extra.optInt("allDayMinutes",480)));}
            JSONObject health=CalendarConfidence.snapshot(this);JSONObject state=new JSONObject().put("settings",settings).put("permissions",permissions).put("readinessIssue",Readiness.issue(this)).put("calendars",calendars).put("source",p.getString("source","")).put("linked",!p.getString("url","").trim().isEmpty()||extras.length()>0).put("lastSync",health.optLong("lastSuccess",p.getLong("lastSync",0))).put("error",p.getString("error","")).put("late",p.getInt("late",0)).put("shifts",Store.array(this,"shifts")).put("manual",Store.object(this,"manual")).put("snoozes",Store.object(this,"snoozes")).put("scheduled",Store.object(this,"scheduled")).put("calendarInfo",Store.object(this,"calendarInfo")).put("customThemes",Appearance.customThemes(this)).put("calendarHealth",health);
            state.put("history",AlarmHistory.entries(this));
            state.put("authorizing",workAlarmAuthorization!=null&&workAlarmAuthorization.isPending());
            web.evaluateJavascript("window.receive&&window.receive("+state+")",null);
        } catch(Exception e){tell("Could not load saved settings.");}
    }
    private void applyAppearance(){
        Appearance profile=Appearance.current(this);setTheme(profile.theme);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(profile.background));
        getWindow().setStatusBarColor(profile.background);getWindow().setNavigationBarColor(profile.background);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        web.setBackgroundColor(profile.background);host.setBackgroundColor(profile.background);
        int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int appearance=Appearance.light(profile.background)?mask:0;
        if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().setSystemBarsAppearance(Appearance.light(profile.background)?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        else getWindow().getDecorView().setSystemUiVisibility((getWindow().getDecorView().getSystemUiVisibility()&~mask)|appearance);
    }
    private class Bridge {
        @JavascriptInterface public String appearanceData(){try{return new JSONObject().put("customThemes",Appearance.customThemes(MainActivity.this)).toString();}catch(Exception e){return "{\"customThemes\":[]}";}}
        @JavascriptInterface public void post(String json){if(json==null||json.length()>65536)return;runOnUiThread(()->{try{dispatch(new JSONObject(json));}catch(Exception e){tell(e.getMessage()==null?"Could not complete that action.":e.getMessage());}});}
    }
    private void dispatch(JSONObject q)throws Exception {
        String action=q.getString("action");
        if(workAlarmAuthorization!=null&&workAlarmAuthorization.isPending()&&!"state".equals(action))return;
        switch(action) {
            case "state":state();break;
            case "syncAlerts":Store.p(this).edit().putBoolean("syncAlerts",q.getBoolean("value")).commit();CalendarConfidence.update(this);state();break;
            case "eveningCheck":int evening=q.getInt("minutes");if(evening<0||evening>1439)throw new Exception("Choose a valid evening time.");Store.p(this).edit().putBoolean("eveningCheck",q.getBoolean("value")).putInt("eveningMinutes",evening).commit();EveningCheck.update(this);state();break;
            case "quietPreset":PlaybackPresets.quiet(this);state();tell("Quiet wake-up selected: 70% ceiling with a one-minute rise.");break;
            case "restorePlayback":PlaybackPresets.restore(this);state();tell("Previous playback settings restored.");break;
            case "saveCustomTheme":Appearance.saveCustom(this,q.getJSONObject("theme"));state();web.evaluateJavascript("window.customThemeSaved&&window.customThemeSaved()",null);tell("Custom theme saved and selected.");break;
            case "deleteCustomTheme":Appearance.deleteCustom(this,q.getString("id"));state();tell("Custom theme removed.");break;
            case "appearance":Appearance.choose(this,q.getString("profile"));state();tell(!Appearance.current(this).id.startsWith("custom:")?"Theme saved. Your app icon colors match.":"Theme saved. The app icon uses the closest built-in palette.");break;
            case "iconDesign":Appearance.chooseIcon(this,q.getString("design"));state();tell(!Appearance.current(this).id.startsWith("custom:")?"Icon saved. It will follow your theme colors.":"Icon saved. The app icon uses the closest built-in palette.");break;
            case "connect":background(()->{if(q.optBoolean("add",false))CalendarSources.add(this,q.optString("name","Work calendar"),q.getString("url"));else{Sync.connect(this,q.getString("url"));Store.p(this).edit().putString("primaryName",CalendarSources.cleanName(q.optString("name","Work calendar"))).commit();}runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.connectionSucceeded&&window.connectionSucceeded()",null);});},"Calendar downloaded. Check the upcoming shifts and wake times.");break;
            case "updateCalendar":background(()->{String id=q.getString("id");if("primary".equals(id)){Store.p(this).edit().putString("primaryName",CalendarSources.cleanName(q.getString("name"))).putString("filter",q.getString("filter").trim()).putInt("lead",q.getInt("lead")).putBoolean("allDayEnabled",q.getBoolean("allDayEnabled")).putInt("allDayMinutes",q.getInt("allDayMinutes")).commit();Sync.run(this,false);}else CalendarSources.update(this,id,q.getString("name"),q.getString("filter"),q.getInt("lead"),q.getBoolean("allDayEnabled"),q.getInt("allDayMinutes"));},"Calendar rules saved.");break;
            case "removeCalendar":String removeId=q.getString("id");Runnable removeCalendar=()->background(()->CalendarSources.remove(this,removeId),"Calendar removed. Its work alarms were updated.");if(Store.p(this).getBoolean("enabled",false))workAlarmAuthorization.request("Remove work calendar?","This removes the calendar and its work alarms. Other calendars stay connected.",removeCalendar);else removeCalendar.run();break;
            case "clearFilter":background(()->{Store.p(this).edit().putString("filter","").commit();Sync.run(this,false);},"Title filter cleared.");break;
            case "copyCalendarCheck":JSONObject info=Store.object(this,"calendarInfo");StringBuilder check=new StringBuilder("Petal Wake 0.1.15 calendar check\n");for(String key:new String[]{"events","timed","allDay","cancelled","missingStart","availability","upcomingTimed","upcomingAllDay","filtered","needsTime","windowDays"})check.append(key).append(": ").append(info.optInt(key)).append('\n');check.append("Title filter active: ").append(!Store.p(this).getString("filter","").trim().isEmpty());((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Calendar check",check.toString()));tell("Calendar counts copied. This includes no calendar link, event titles, or event details.");break;
            case "refresh":background(()->Sync.run(this,true),null);break;
            case "import":Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(choose,10);break;
            case "exportSettings":Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"petal-wake-settings.json");startActivityForResult(save,20);break;
            case "importSettings":Intent restore=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(restore,21);break;
            case "clearHistory":AlarmHistory.clear(this);state();tell("Alarm activity cleared from this phone.");break;
            case "disconnect":Runnable disconnect=()->background(()->{CalendarSources.clear(this);Store.p(this).edit().remove("url").remove("source").remove("lastSync").remove("primaryName").putBoolean("enabled",false).commit();CalendarConfidence.reset(this);Store.cache(this,"");Sync.run(this,false);},"Calendars disconnected.");if(Store.p(this).getBoolean("enabled",false))workAlarmAuthorization.request("Disconnect work calendars?","This removes all calendar connections and turns off their work alarms.",disconnect);else disconnect.run();break;
            case "exact":startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));break;
            case "notifications":if(Build.VERSION.SDK_INT>=33&&!notifyAllowed())requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));break;
            case "fullscreen":if(Build.VERSION.SDK_INT>=34)startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));break;
            case "volumeSettings":startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));break;
            case "batterySettings":startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));break;
            case "enable":boolean enabled=q.getBoolean("value");if(enabled&&(!Scheduler.allowed(this)||!notifyAllowed())){tell("Open Settings in Petal Wake and allow alarms and notifications first.");state();break;}Runnable change=()->background(()->{Store.p(this).edit().putBoolean("enabled",enabled).commit();Sync.run(this,false);},null);if(!enabled&&Store.p(this).getBoolean("enabled",false))workAlarmAuthorization.request("Turn off work alarms?","Your work alarms will be paused. Your calendar and settings stay saved.",change);else change.run();break;
            case "settings":background(()->{int lead=q.getInt("lead"),snooze=q.getInt("snooze"),volume=q.getInt("volume"),fade=q.getInt("fade");if(lead<0||lead>1440||snooze<1||snooze>60||volume<10||volume>100||fade<0||fade>120)throw new Exception("Settings are outside the allowed range.");if(q.optBoolean("allDayEnabled",false)&&q.isNull("allDayMinutes"))throw new Exception("Choose a start time for all-day shifts.");int allDayMinutes=q.optInt("allDayMinutes",480),earlyBefore=q.optInt("earlyBefore",600),earlyLead=q.optInt("earlyLead",90),lateAfter=q.optInt("lateAfter",720),lateLead=q.optInt("lateLead",45),eveningMinutes=q.optInt("eveningMinutes",1140),fallbackMinutes=q.optInt("fallbackMinutes",0);if(allDayMinutes<0||allDayMinutes>1439||earlyBefore<0||earlyBefore>1439||lateAfter<=earlyBefore||lateAfter>1439||earlyLead<0||earlyLead>1440||lateLead<0||lateLead>1440||eveningMinutes<0||eveningMinutes>1439||!(fallbackMinutes==0||fallbackMinutes==5||fallbackMinutes==10||fallbackMinutes==15))throw new Exception("Choose valid shift rules and times.");Store.p(this).edit().putBoolean("allDayEnabled",q.optBoolean("allDayEnabled",false)).putInt("allDayMinutes",allDayMinutes).putInt("lead",lead).putInt("snooze",snooze).putInt("volume",volume).putInt("fade",fade).putBoolean("quietPreset",Store.p(this).getBoolean("quietPreset",false)&&volume==PlaybackPresets.QUIET_VOLUME&&fade==PlaybackPresets.QUIET_FADE).putBoolean("vibrate",q.getBoolean("vibrate")).putString("filter",q.getString("filter").trim()).putBoolean("shiftRulesEnabled",q.optBoolean("shiftRulesEnabled",false)).putInt("earlyBefore",earlyBefore).putInt("earlyLead",earlyLead).putInt("lateAfter",lateAfter).putInt("lateLead",lateLead).putBoolean("eveningCheck",q.optBoolean("eveningCheck",false)).putInt("eveningMinutes",eveningMinutes).putInt("fallbackMinutes",fallbackMinutes).commit();Sync.run(this,false);},"Preferences saved.");break;
            case "sound":String file=q.getString("file");if(!SoundLibrary.available(this,file))throw new Exception("Download the orchestra pack before choosing this sound.");Store.p(this).edit().putString("sound",file).commit();state();break;
            case "downloadPack":background(()->SoundPacks.install(this),"Orchestra pack installed. Its recordings are ready offline.");break;
            case "removePack":background(()->SoundPacks.remove(this),"Orchestra pack removed. Bundled sounds remain available.");break;
            case "preview":preview(q.getString("file"));break;
            case "previewWake":preview(SoundLibrary.playable(this),true);break;
            case "stopPreview":stopPreview();break;
            case "skip":background(()->Scheduler.skip(this,q.getString("id")),"Shift alarm skipped.");break;
            case "unskip":background(()->{JSONObject skip=Store.object(this,"skipped");skip.remove(q.getString("id"));Store.put(this,"skipped",skip);Sync.run(this,false);},"Shift alarm restored.");break;
            case "manual":case "test":if(!Scheduler.allowed(this)||!notifyAllowed()){tell("Allow alarms and notifications in Settings first.");break;}background(()->{long at="test".equals(action)?System.currentTimeMillis()+15_000:q.getLong("at");if(at<=System.currentTimeMillis())throw new Exception("Choose a future time.");JSONObject list=Store.object(this,"manual");if(list.length()>=32)throw new Exception("Keep at most 32 personal alarms.");String id="manual:"+UUID.randomUUID();list.put(id,new JSONObject().put("id",id).put("title","test".equals(action)?"Petal Wake sound check":q.optString("title","Personal alarm")).put("at",at).put("kind","manual"));Store.put(this,"manual",list);Sync.run(this,false);},"test".equals(action)?"Test alarm set for 15 seconds from now. Lock your screen to try it.":"Personal alarm added.");break;
            case "delete":background(()->{String id=q.getString("id");for(String key:new String[]{"manual","snoozes"}){JSONObject items=Store.object(this,key);items.remove(id);Store.put(this,key,items);}Scheduler.cancel(this,id);Sync.run(this,false);},"Alarm removed.");break;
        }
    }
    private void preview(String file)throws Exception {preview(file,false);}
    private void preview(String file,boolean fullRamp)throws Exception {
        if(!SoundLibrary.available(this,file))throw new Exception("Download the orchestra pack to preview this recording.");
        handler.removeCallbacksAndMessages(null);if(previewShaper!=null){previewShaper.close();previewShaper=null;}if(preview!=null){preview.release();preview=null;}preview=new MediaPlayer();preview.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());preview.setLooping(fullRamp);
        SoundLibrary.setDataSource(preview,this,file);
        int fade=fullRamp?Store.p(this).getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS):0;
        preview.setOnPreparedListener(p->{
            float level=fullRamp?Store.p(this).getInt("volume",80)/100f:Store.p(this).getBoolean("quietPreset",false)?.42f:.6f;
            p.setVolume(level,level);
            if(fullRamp&&fade>0){
                float[] times=new float[16],gains=new float[16];
                for(int point=0;point<16;point++){times[point]=point/15f;gains[point]=AlarmEnvelope.volume(1f,fade,Math.round(times[point]*fade*1000));}
                VolumeShaper.Configuration curve=new VolumeShaper.Configuration.Builder().setDuration(fade*1000L).setCurve(times,gains).setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR).build();
                previewShaper=p.createVolumeShaper(curve);
            }
            p.start();if(previewShaper!=null)previewShaper.apply(VolumeShaper.Operation.PLAY);
        });
        preview.setOnCompletionListener(p->stopPreview());preview.setOnErrorListener((p,w,e)->{tell("This sound could not play.");stopPreview();return true;});preview.prepareAsync();handler.postDelayed(this::stopPreview,fullRamp?Math.max(20_000,fade*1000L+5_000):20_000);
    }
    private void stopPreview(){handler.removeCallbacksAndMessages(null);if(previewShaper!=null){previewShaper.close();previewShaper=null;}if(preview!=null){preview.release();preview=null;}if(web!=null)web.evaluateJavascript("window.previewStopped&&window.previewStopped()",null);}
}
