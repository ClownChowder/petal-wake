package app.daybreak;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    static volatile boolean visible;
    private WorkAlarmAuthorization workAlarmAuthorization;
    private char[] pendingMovePassword;
    private boolean pageReady,receiverRegistered,destroying,iconPromptShown;private String pendingPage,pendingSoundGenre="Other";private WebView web;private MediaPlayer preview;private VolumeShaper previewShaper;private android.widget.FrameLayout host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean PREVIEW_BUSY=new AtomicBoolean();
    private int previewToken;
    private WebViewAssetLoader assetLoader;
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){state();}};
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 receivers require the signature permission; 33+ also use NOT_EXPORTED.
    @Override public void onCreate(Bundle saved){
        setTheme(Appearance.current(this).theme);super.onCreate(saved);
        if(saved!=null)pendingSoundGenre=saved.getString("pendingSoundGenre","Other");
        int background=Appearance.current(this).background;getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
            android.widget.TextView unavailable=new android.widget.TextView(this);unavailable.setText("Rouse needs an updated Android System WebView. Update it in your app store, then reopen Rouse.");unavailable.setTextSize(18);unavailable.setPadding(48,48,48,48);setContentView(unavailable);return;
        }
        web=new WebView(this);web.setBackgroundColor(background);
        host=new android.widget.FrameLayout(this);host.setBackgroundColor(background);host.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=35)host.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);s.setDomStorageEnabled(false);s.setTextZoom(Math.max(100,Math.min(200,Math.round(getResources().getConfiguration().fontScale*100))));
        assetLoader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        WebViewCompat.addWebMessageListener(web,"Native",Collections.singleton("https://appassets.androidplatform.net"),(view,message,origin,mainFrame,reply)->{
            if(!mainFrame||!"https".equals(origin.getScheme())||!"appassets.androidplatform.net".equals(origin.getHost()))return;
            String json=message.getData();if(json==null||json.length()>65536)return;
            runOnUiThread(()->{JSONObject request=null;try{request=new JSONObject(json);dispatch(request);}catch(Exception e){if(request!=null&&"saveCustomTheme".equals(request.optString("action"))&&web!=null)web.evaluateJavascript("window.customThemeSaveFailed&&window.customThemeSaveFailed()",null);tell(e.getMessage()==null?"Could not complete that action.":e.getMessage());}});
        });
        web.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return assetLoader==null?null:assetLoader.shouldInterceptRequest(r.getUrl());}@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}@Override public void onPageFinished(WebView v,String url){if(destroying||web!=v||!url.startsWith("https://appassets.androidplatform.net/assets/index.html"))return;pageReady=true;state();if(!iconPromptShown&&FourWagsWakeIcon.pending(MainActivity.this)){iconPromptShown=true;FourWagsWakeIcon.showPicker(MainActivity.this);}if(pendingPage!=null){web.evaluateJavascript("window.openPage&&window.openPage("+JSONObject.quote(pendingPage)+")",null);pendingPage=null;}}});
        setContentView(host);if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);applyAppearance();web.loadUrl("https://appassets.androidplatform.net/assets/index.html#"+Appearance.current(this).id+"/"+Appearance.icon(this));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(DaybreakApp.CHANGED),DaybreakApp.INTERNAL_PERMISSION,null,RECEIVER_NOT_EXPORTED);else registerReceiver(updates,new IntentFilter(DaybreakApp.CHANGED),DaybreakApp.INTERNAL_PERMISSION,null);receiverRegistered=true;
        workAlarmAuthorization=new WorkAlarmAuthorization(this,this::state,this::tell);
        incoming(getIntent());
    }
    @Override protected void onResume(){super.onResume();visible=true;if(web!=null){Readiness.update(this);state();((DaybreakApp)getApplication()).refresh(true);}}
    @Override protected void onPause(){visible=false;stopPreview();super.onPause();}
    @Override public void onBackPressed(){if(web!=null&&pageReady){web.evaluateJavascript("window.handleAndroidBack&&window.handleAndroidBack()",null);return;}super.onBackPressed();}
    @Override protected void onDestroy(){
        destroying=true;pageReady=false;
        clearMovePassword();
        if(workAlarmAuthorization!=null)workAlarmAuthorization.destroy();
        if(receiverRegistered){unregisterReceiver(updates);receiverRegistered=false;}
        stopPreview();
        if(web!=null){WebView old=web;web=null;if(host!=null)host.removeView(old);old.destroy();}
        assetLoader=null;
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putString("pendingSoundGenre",pendingSoundGenre);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);incoming(i);}
    private void incoming(Intent i){if(i==null)return;if("calendar".equals(i.getStringExtra("page"))){pendingPage="calendar";if(web!=null&&pageReady){web.evaluateJavascript("window.openPage&&window.openPage(\"calendar\")",null);pendingPage=null;}}Uri uri=null;if(Intent.ACTION_SEND.equals(i.getAction()))uri=i.getParcelableExtra(Intent.EXTRA_STREAM);else if(Intent.ACTION_VIEW.equals(i.getAction()))uri=i.getData();if(uri!=null&&"content".equals(uri.getScheme()))confirmImport(uri);else if(Intent.ACTION_SEND.equals(i.getAction())){String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null){String link=text.trim();if(link.startsWith("https://")||link.startsWith("webcal://"))new AlertDialog.Builder(this).setTitle("Connect shared calendar?").setMessage("Rouse will download this calendar link and replace the current calendar connection. Review the shifts before enabling work alarms.").setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w)->background(()->Sync.connect(this,link),"Calendar connected. Review shifts before enabling alarms.")).show();}}}
    private void confirmImport(Uri uri){new AlertDialog.Builder(this).setTitle("Import work calendar?").setMessage("This replaces your current calendar connection with a one-time snapshot. Files do not automatically refresh.").setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->importUri(uri)).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(workAlarmAuthorization!=null&&workAlarmAuthorization.onActivityResult(request,result))return;
        if(request==22){char[] password=pendingMovePassword;pendingMovePassword=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&password!=null)exportMove(data.getData(),password);else if(password!=null)Arrays.fill(password,'\0');return;}
        if(result!=RESULT_OK||data==null)return;if(request==30||request==31){addPersonalSounds(data,request==31);return;}if(data.getData()==null)return;
        if(request==10)confirmImport(data.getData());else if(request==20)exportSettings(data.getData());else if(request==21)confirmSettingsImport(data.getData());else if(request==23)confirmMoveImport(data.getData());}
    private void addPersonalSounds(Intent data,boolean link){
        ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;String genre=pendingSoundGenre;
        background(()->{int added=0;String failure=null;for(Uri uri:uris)try{CustomSounds.add(this,uri,link,genre);added++;}catch(Exception error){failure=Store.safeError(error.getMessage()==null?"Could not add a selected file.":error.getMessage());}
            int count=added;String message=failure==null?count+(count==1?" sound added. ":" sounds added. ")+(link?"Keep the original file available for alarms.":"Saved offline on this phone."):count+" added. "+failure;tell(message);
            if(count>0)runOnUiThread(()->{if(!destroying&&web!=null)web.evaluateJavascript("window.customSoundAdded&&window.customSoundAdded()",null);});
        },null);
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);state();}
    private void importUri(Uri uri){background(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("File could not be opened.");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4_000_000)throw new IOException("Calendar file is too large.");}Sync.importFile(this,out.toString(StandardCharsets.UTF_8.name()));}},"Calendar imported. Review your shifts, then enable alarms.");}
    private void exportSettings(Uri uri){background(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Settings file could not be created.");out.write(SettingsTransfer.exportJson(this).getBytes(StandardCharsets.UTF_8));}},"Settings saved. Calendar links, events, and alarms were excluded.");}
    private void confirmSettingsImport(Uri uri){new AlertDialog.Builder(this).setTitle("Restore Rouse settings?").setMessage("This replaces alarm preferences and themes from the file. Calendar links, work alarms, and personal alarms stay as they are. Review your next alarm afterward.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->background(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("Settings file could not be opened.");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>262_144)throw new IOException("Settings file is too large.");}SettingsTransfer.importJson(this,out.toString(StandardCharsets.UTF_8.name()));}},"Settings restored. Review your next alarm and Android permissions.")).show();}
    private static char[] secret(android.widget.EditText field){android.text.Editable value=field.getText();char[] result=new char[value.length()];value.getChars(0,value.length(),result,0);return result;}
    private android.widget.EditText secretField(String hint){android.widget.EditText field=new android.widget.EditText(this);field.setSingleLine(true);field.setHint(hint);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);return field;}
    private void clearMovePassword(){if(pendingMovePassword!=null){Arrays.fill(pendingMovePassword,'\0');pendingMovePassword=null;}}
    private void chooseMoveExport(){
        android.widget.LinearLayout fields=new android.widget.LinearLayout(this);fields.setOrientation(android.widget.LinearLayout.VERTICAL);int pad=Math.round(getResources().getDisplayMetrics().density*20);fields.setPadding(pad,0,pad,0);
        android.widget.EditText first=secretField("Password: at least 12 characters"),again=secretField("Confirm password");fields.addView(first);fields.addView(again);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Save a private move package").setMessage("Includes calendar sharing links, personal alarms, themes, history, and your music. Keep the password separately; it cannot be recovered.").setView(fields).setNegativeButton("Cancel",(d,w)->{first.setText("");again.setText("");}).setPositiveButton("Choose file",null).create();
        dialog.setOnDismissListener(ignored->{first.setText("");again.setText("");});
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            char[] a=secret(first),b=secret(again);
            if(a.length<12||!Arrays.equals(a,b)){Arrays.fill(a,'\0');Arrays.fill(b,'\0');again.setError("Use matching passwords of at least 12 characters.");return;}
            Arrays.fill(b,'\0');clearMovePassword();pendingMovePassword=a;first.setText("");again.setText("");dialog.dismiss();
            String date=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date());
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"rouse-move-"+date+".rouse");
            try{startActivityForResult(save,22);}catch(RuntimeException unavailable){clearMovePassword();tell("No file picker is available for the move package.");}
        }));dialog.show();
    }
    private void exportMove(Uri uri,char[] password){tell("Saving move package. Keep Rouse open; a large music library may take a few minutes.");background(()->{
        try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Move package file could not be created.");MovePackage.exportTo(this,out,password);}
        finally{Arrays.fill(password,'\0');}
    },"Move package saved. Keep its password separately and move the file to your new phone.",null,DaybreakApp.PACK_IO);}
    private void confirmMoveImport(Uri uri){
        android.widget.EditText passwordField=secretField("Move-package password");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Restore Rouse on this phone?").setMessage("Use a new or empty Rouse setup. The encrypted package restores calendars, personal alarms, themes, history, and music. Work alarms stay paused until you check permissions and wake times.").setView(passwordField).setNegativeButton("Cancel",(d,w)->passwordField.setText("")).setPositiveButton("Restore",null).create();
        dialog.setOnDismissListener(ignored->passwordField.setText(""));
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            char[] password=secret(passwordField);if(password.length<12){Arrays.fill(password,'\0');passwordField.setError("Enter the package password.");return;}
            passwordField.setText("");dialog.dismiss();tell("Checking the move package. Keep Rouse open until restore finishes.");background(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Move package could not be opened.");MovePackage.importFrom(this,in,password);}finally{Arrays.fill(password,'\0');}},"Move package restored. Grant Android alarm permissions, enable work alarms, then check your next wake time and phone alarm volume.",null,DaybreakApp.PACK_IO);
        }));dialog.show();
    }
    private void tell(String message){runOnUiThread(()->{if(destroying||isDestroyed()||web==null){Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();return;}web.evaluateJavascript("window.toast&&window.toast("+JSONObject.quote(message)+")",null);});}
    private interface Work {void run()throws Exception;}
    private void background(Work work,String message){background(work,message,null);}
    private void background(Work work,String message,String successCallback){background(work,message,successCallback,DaybreakApp.IO);}
    private void background(Work work,String message,String successCallback,java.util.concurrent.ExecutorService executor){if(destroying||web==null)return;web.evaluateJavascript("window.setBusy(true)",null);executor.execute(()->{try{work.run();if(successCallback!=null)runOnUiThread(()->{if(!destroying&&!isDestroyed()&&web!=null)web.evaluateJavascript(successCallback,null);});if(message!=null)tell(message);}catch(Exception e){String error=Store.safeError(e.getMessage()==null?"That action could not be completed.":e.getMessage());Store.error(this,error);tell(error);}finally{runOnUiThread(()->{if(!destroying&&!isDestroyed()&&web!=null){web.evaluateJavascript("window.setBusy&&window.setBusy(false)",null);state();}});}});}
    private boolean notifyAllowed(){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel ch=nm.getNotificationChannel(AlarmService.CHANNEL);return (Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&nm.areNotificationsEnabled()&&(ch==null||ch.getImportance()>=NotificationManager.IMPORTANCE_HIGH);}
    private boolean fullAllowed(){return Build.VERSION.SDK_INT<34||((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).canUseFullScreenIntent();}
    private void state(){
        if(web==null||destroying||isDestroyed())return;
        try {
            applyAppearance();var p=Store.p(this);JSONObject settings=new JSONObject().put("appearance",Appearance.current(this).id).put("iconDesign",Appearance.icon(this)).put("enabled",p.getBoolean("enabled",false)).put("lead",p.getInt("lead",90)).put("filter",p.getString("filter","")).put("snooze",p.getInt("snooze",9)).put("volume",p.getInt("volume",100)).put("fade",p.getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS)).put("riseStartPercent",p.getInt("riseStartPercent",AlarmVolumeRamp.DEFAULT_START_PERCENT)).put("vibrate",p.getBoolean("vibrate",true)).put("sound",SoundLibrary.playable(this)).put("requestedSound",SoundLibrary.current(this)).put("soundPackInstalled",SoundPacks.installed(this)).put("allDayEnabled",p.getBoolean("allDayEnabled",false)).put("allDayMinutes",p.getInt("allDayMinutes",480)).put("quietPreset",p.getBoolean("quietPreset",false)).put("canRestorePlayback",Store.object(this,"previousPlayback").length()>0).put("shiftRulesEnabled",p.getBoolean("shiftRulesEnabled",false)).put("earlyBefore",p.getInt("earlyBefore",600)).put("earlyLead",p.getInt("earlyLead",90)).put("lateAfter",p.getInt("lateAfter",720)).put("lateLead",p.getInt("lateLead",45)).put("eveningCheck",p.getBoolean("eveningCheck",false)).put("eveningMinutes",p.getInt("eveningMinutes",1140));
            settings.put("fallbackMinutes",p.getInt("fallbackMinutes",0)).put("maxRingMinutes",p.getInt("maxRingMinutes",30));
            AudioManager alarmAudio=(AudioManager)getSystemService(AUDIO_SERVICE);
            JSONObject permissions=new JSONObject().put("exact",Scheduler.allowed(this)).put("notifications",notifyAllowed()&&((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()).put("fullscreen",fullAllowed()).put("systemVolume",alarmAudio.getStreamVolume(AudioManager.STREAM_ALARM)).put("systemVolumeMin",Build.VERSION.SDK_INT>=28?alarmAudio.getStreamMinVolume(AudioManager.STREAM_ALARM):0).put("systemVolumeMax",alarmAudio.getStreamMaxVolume(AudioManager.STREAM_ALARM));
            JSONArray calendars=new JSONArray();if(CalendarSources.hasPrimary(this))calendars.put(new JSONObject().put("id","primary").put("name",p.getString("primaryName","Work calendar")).put("kind",Store.url(this).isEmpty()?"Imported snapshot":"Shared link").put("lastSync",p.getLong("lastSync",0)).put("lead",p.getInt("lead",90)).put("filter",p.getString("filter","")).put("allDayEnabled",p.getBoolean("allDayEnabled",false)).put("allDayMinutes",p.getInt("allDayMinutes",480)));
            JSONArray extras=CalendarSources.extras(this);for(int i=0;i<extras.length();i++){JSONObject extra=extras.getJSONObject(i);calendars.put(new JSONObject().put("id",extra.optString("id")).put("name",extra.optString("name")).put("kind","Shared link").put("lastSync",extra.optLong("lastSync")).put("error",extra.optString("error")).put("lead",extra.optInt("lead",90)).put("filter",extra.optString("filter")).put("allDayEnabled",extra.optBoolean("allDayEnabled")).put("allDayMinutes",extra.optInt("allDayMinutes",480)));}
            settings.put("privateLockScreen",p.getBoolean("privateLockScreen",true));
            JSONObject health=CalendarConfidence.snapshot(this);JSONObject state=new JSONObject().put("settings",settings).put("permissions",permissions).put("readinessIssue",Readiness.issue(this)).put("calendars",calendars).put("source",p.getString("source","")).put("linked",!Store.url(this).trim().isEmpty()||extras.length()>0).put("lastSync",health.optLong("lastSuccess",p.getLong("lastSync",0))).put("error",p.getString("error","")).put("late",p.getInt("late",0)).put("shifts",Store.array(this,"shifts")).put("manual",Store.object(this,"manual")).put("snoozes",Store.object(this,"snoozes")).put("testPending",Store.object(this,"testAlarms").length()>0).put("scheduled",Store.object(this,"scheduled")).put("calendarInfo",Store.object(this,"calendarInfo")).put("customThemes",Appearance.customThemes(this)).put("customSounds",CustomSounds.forUi(this)).put("calendarHealth",health);
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
    private void dispatch(JSONObject q)throws Exception {
        String action=q.getString("action");
        if(workAlarmAuthorization!=null&&workAlarmAuthorization.isPending()&&!"state".equals(action))return;
        switch(action) {
            case "finish":finish();break;
            case "state":state();break;
            case "syncAlerts":Store.p(this).edit().putBoolean("syncAlerts",q.getBoolean("value")).commit();CalendarConfidence.update(this);state();break;
            case "privateLockScreen":Store.p(this).edit().putBoolean("privateLockScreen",q.getBoolean("value")).commit();state();tell("Lock-screen privacy updated.");break;
            case "copyCalendarLink":workAlarmAuthorization.request("Copy private calendar link?","Confirm before placing your calendar link on the clipboard.",()->{
                try{String link="primary".equals(q.optString("id","primary"))?Store.url(this):CalendarSources.link(this,q.getString("id"));if(link.isEmpty()){tell("This calendar has no sharing link.");return;}
                    ClipData clip=ClipData.newPlainText("Private calendar link",link);android.os.PersistableBundle flags=new android.os.PersistableBundle();flags.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(flags);((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);tell("Private link copied. Clear your clipboard after using it.");
                }catch(Exception e){tell("Could not copy this calendar link.");}
            },"Calendar link was not copied.");break;
            case "eveningCheck":int evening=q.getInt("minutes");if(evening<0||evening>1439)throw new Exception("Choose a valid evening time.");Store.p(this).edit().putBoolean("eveningCheck",q.getBoolean("value")).putInt("eveningMinutes",evening).commit();EveningCheck.update(this);state();break;
            case "quietPreset":PlaybackPresets.quiet(this);state();tell("Quiet wake-up selected: 70% ceiling with a one-minute rise.");break;
            case "restorePlayback":PlaybackPresets.restore(this);state();tell("Previous playback settings restored.");break;
            case "saveCustomTheme":Appearance.saveCustom(this,q.getJSONObject("theme"));state();web.evaluateJavascript("window.customThemeSaved&&window.customThemeSaved()",null);tell("Custom theme saved and selected.");break;
            case "deleteCustomTheme":Appearance.deleteCustom(this,q.getString("id"));state();tell("Custom theme removed.");break;
            case "appearance":Appearance.choose(this,q.getString("profile"));state();tell((!Appearance.current(this).id.startsWith("custom:")&&!Appearance.current(this).id.startsWith("shiba_"))?"Theme saved. Your app icon colors match.":"Theme saved. The app icon uses the closest bundled palette.");break;
            case "iconDesign":Appearance.chooseIcon(this,q.getString("design"));state();tell(q.getString("design").startsWith("rouse_")?"Icon saved.":!Appearance.current(this).id.startsWith("custom:")?"Icon saved. It will follow your theme colors.":"Icon saved. The app icon uses the closest built-in palette.");break;
            case "connect":background(()->{if(q.optBoolean("add",false))CalendarSources.add(this,q.optString("name","Work calendar"),q.getString("url"));else{Sync.connect(this,q.getString("url"));Store.p(this).edit().putString("primaryName",CalendarSources.cleanName(q.optString("name","Work calendar"))).commit();}runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.connectionSucceeded&&window.connectionSucceeded()",null);});},"Calendar downloaded. Check the upcoming shifts and wake times.");break;
            case "updateCalendar":background(()->{String id=q.getString("id");if("primary".equals(id)){Store.p(this).edit().putString("primaryName",CalendarSources.cleanName(q.getString("name"))).putString("filter",q.getString("filter").trim()).putInt("lead",q.getInt("lead")).putBoolean("allDayEnabled",q.getBoolean("allDayEnabled")).putInt("allDayMinutes",q.getInt("allDayMinutes")).commit();Sync.run(this,false);}else CalendarSources.update(this,id,q.getString("name"),q.getString("filter"),q.getInt("lead"),q.getBoolean("allDayEnabled"),q.getInt("allDayMinutes"));},"Calendar rules saved.","window.calendarRulesSaved&&window.calendarRulesSaved()");break;
            case "removeCalendar":String removeId=q.getString("id");Runnable removeCalendar=()->background(()->CalendarSources.remove(this,removeId),"Calendar removed. Its work alarms were updated.");if(Store.p(this).getBoolean("enabled",false))workAlarmAuthorization.request("Remove work calendar?","This removes the calendar and its work alarms. Other calendars stay connected.",removeCalendar);else removeCalendar.run();break;
            case "clearFilter":background(()->{Store.p(this).edit().putString("filter","").commit();Sync.run(this,false);},"Title filter cleared.");break;
            case "copyCalendarCheck":JSONObject info=Store.object(this,"calendarInfo");StringBuilder check=new StringBuilder("Rouse calendar check\n");for(String key:new String[]{"events","timed","allDay","cancelled","missingStart","availability","upcomingTimed","upcomingAllDay","filtered","needsTime","windowDays"})check.append(key).append(": ").append(info.optInt(key)).append('\n');check.append("Title filter active: ").append(!Store.p(this).getString("filter","").trim().isEmpty());((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Calendar check",check.toString()));tell("Calendar counts copied. This includes no calendar link, event titles, or event details.");break;
            case "refresh":if(!CalendarRefresh.request(this,()->true,()->runOnUiThread(()->{if(!destroying&&!isDestroyed()){state();tell("Calendar check finished. Review its status above.");}})))tell("A calendar check is already running.");else tell("Checking calendars in the background. You can keep using Rouse.");break;
            case "import":Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(choose,10);break;
            case "exportSettings":Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"rouse-settings.json");startActivityForResult(save,20);break;
            case "importSettings":Intent restore=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(restore,21);break;
            case "exportMove":workAlarmAuthorization.request("Save private move package?","Confirm before exporting private calendar links and audio.",this::chooseMoveExport,"Move package was not saved.");break;
            case "importMove":Intent move=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(move,23);break;
            case "clearHistory":AlarmHistory.clear(this);state();tell("Alarm activity cleared from this phone.");break;
            case "disconnect":Runnable disconnect=()->background(()->{CalendarSources.clear(this);Store.setUrl(this,"");Store.p(this).edit().remove("source").remove("lastSync").remove("primaryName").putBoolean("enabled",false).commit();CalendarConfidence.reset(this);Store.cache(this,"");Sync.run(this,false);},"Calendars disconnected.");if(Store.p(this).getBoolean("enabled",false))workAlarmAuthorization.request("Disconnect work calendars?","This removes all calendar connections and turns off their work alarms.",disconnect);else disconnect.run();break;
            case "exact":if(Build.VERSION.SDK_INT>=31)startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));break;
            case "notifications":if(Build.VERSION.SDK_INT>=33&&!notifyAllowed())requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));break;
            case "fullscreen":if(Build.VERSION.SDK_INT>=34)startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));break;
            case "volumeSettings":startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));break;
            case "setPhoneAlarmVolume":{AudioManager alarmAudio=(AudioManager)getSystemService(AUDIO_SERVICE);int level=q.getInt("level"),minimum=Build.VERSION.SDK_INT>=28?alarmAudio.getStreamMinVolume(AudioManager.STREAM_ALARM):0,maximum=alarmAudio.getStreamMaxVolume(AudioManager.STREAM_ALARM);if(level<minimum||level>maximum)throw new Exception("Choose a phone alarm volume within the available range.");try{alarmAudio.setStreamVolume(AudioManager.STREAM_ALARM,level,0);}catch(SecurityException|IllegalArgumentException error){state();throw new Exception("Android could not change phone alarm volume. Open Android sound settings to check it.");}int actual=alarmAudio.getStreamVolume(AudioManager.STREAM_ALARM);Readiness.update(this);state();if(actual!=level)tell("Android kept phone alarm volume at "+actual+" of "+maximum+". Try the alarm to confirm it.");else tell("Phone alarm volume set to "+actual+" of "+maximum+". Try an alarm to hear it.");break;}
            case "batterySettings":startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));break;
            case "enable":boolean enabled=q.getBoolean("value");if(enabled&&(!Scheduler.allowed(this)||!notifyAllowed())){tell("Open Settings in Rouse and allow alarms and notifications first.");state();break;}Runnable change=()->background(()->{Store.p(this).edit().putBoolean("enabled",enabled).commit();Sync.run(this,false);},null);if(!enabled&&Store.p(this).getBoolean("enabled",false))workAlarmAuthorization.request("Turn off work alarms?","Your work alarms will be paused. Your calendar and settings stay saved.",change);else change.run();break;
            case "settings":background(()->{int lead=q.getInt("lead"),snooze=q.getInt("snooze"),fade=q.getInt("fade"),riseStartPercent=q.getInt("riseStartPercent");if(lead<0||lead>1440||snooze<1||snooze>60||fade<0||fade>120||riseStartPercent<10||riseStartPercent>100)throw new Exception("Settings are outside the allowed range.");if(q.optBoolean("allDayEnabled",false)&&q.isNull("allDayMinutes"))throw new Exception("Choose a start time for all-day shifts.");int allDayMinutes=q.optInt("allDayMinutes",480),earlyBefore=q.optInt("earlyBefore",600),earlyLead=q.optInt("earlyLead",90),lateAfter=q.optInt("lateAfter",720),lateLead=q.optInt("lateLead",45),eveningMinutes=q.optInt("eveningMinutes",1140),fallbackMinutes=q.optInt("fallbackMinutes",0),maxRingMinutes=q.optInt("maxRingMinutes",30);if(allDayMinutes<0||allDayMinutes>1439||earlyBefore<0||earlyBefore>1439||lateAfter<=earlyBefore||lateAfter>1439||earlyLead<0||earlyLead>1440||lateLead<0||lateLead>1440||eveningMinutes<0||eveningMinutes>1439||!(fallbackMinutes==0||fallbackMinutes==5||fallbackMinutes==10||fallbackMinutes==15)||!(maxRingMinutes==15||maxRingMinutes==30||maxRingMinutes==60))throw new Exception("Choose valid shift rules and times.");Store.p(this).edit().putBoolean("allDayEnabled",q.optBoolean("allDayEnabled",false)).putInt("allDayMinutes",allDayMinutes).putInt("lead",lead).putInt("snooze",snooze).putInt("fade",fade).putInt("riseStartPercent",riseStartPercent).putBoolean("quietPreset",false).remove("previousPlayback").putBoolean("vibrate",q.getBoolean("vibrate")).putString("filter",q.getString("filter").trim()).putBoolean("shiftRulesEnabled",q.optBoolean("shiftRulesEnabled",false)).putInt("earlyBefore",earlyBefore).putInt("earlyLead",earlyLead).putInt("lateAfter",lateAfter).putInt("lateLead",lateLead).putBoolean("eveningCheck",q.optBoolean("eveningCheck",false)).putInt("eveningMinutes",eveningMinutes).putInt("fallbackMinutes",fallbackMinutes).putInt("maxRingMinutes",maxRingMinutes).commit();Sync.run(this,false);},"Preferences saved.","window.settingsSaved&&window.settingsSaved()");break;
            case "sound":String file=q.getString("file");if(!SoundLibrary.available(this,file))throw new Exception("Download the orchestra pack before choosing this sound.");Store.p(this).edit().putString("sound",file).commit();state();break;
            case "downloadPack":background(()->SoundPacks.install(this),"Orchestra pack installed. Its recordings are ready offline.",null,DaybreakApp.PACK_IO);break;
            case "removePack":background(()->SoundPacks.remove(this),"Orchestra pack removed. Bundled sounds remain available.",null,DaybreakApp.PACK_IO);break;
            case "addCustomSound":String genre=q.optString("genre","Other");boolean knownGenre=false;for(String choice:CustomSounds.GENRES)if(choice.equals(genre))knownGenre=true;if(!knownGenre)throw new Exception("Choose a music genre.");pendingSoundGenre=genre;boolean link=q.optBoolean("link",false);Intent audioFiles=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);audioFiles.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,!link);startActivityForResult(audioFiles,link?31:30);break;
            case "removeCustomSound":background(()->CustomSounds.remove(this,q.getString("file")),"Personal sound removed.");break;
            case "customSoundGenre":background(()->CustomSounds.changeGenre(this,q.getString("file"),q.getString("genre")),"Genre updated.");break;
            case "preview":preview(q.getString("file"));break;
            case "stopPreview":stopPreview();break;
            case "skip":background(()->Scheduler.skip(this,q.getString("id")),"Shift alarm skipped.");break;
            case "unskip":background(()->{JSONObject skip=Store.object(this,"skipped");skip.remove(q.getString("id"));Store.put(this,"skipped",skip);Sync.run(this,false);},"Shift alarm restored.");break;
            case "manual":case "test":if(!Scheduler.allowed(this)||!notifyAllowed()){tell("Allow alarms and notifications in Settings first.");break;}background(()->{boolean test="test".equals(action);long at=test?System.currentTimeMillis()+15_000:q.getLong("at");if(at<=System.currentTimeMillis())throw new Exception("Choose a future time.");String key=test?"testAlarms":"manual";JSONObject list=Store.object(this,key);if(!test&&list.length()>=32)throw new Exception("Keep at most 32 personal alarms.");if(test){for(java.util.Iterator<String> ids=list.keys();ids.hasNext();)Scheduler.cancel(this,ids.next());list=new JSONObject();}String id=(test?"test:":"manual:")+UUID.randomUUID();list.put(id,new JSONObject().put("id",id).put("title",test?"Rouse sound check":q.optString("title","Personal alarm")).put("at",at).put("kind",test?"test":"manual"));Store.put(this,key,list);Sync.run(this,false);},"test".equals(action)?"Test alarm set for 15 seconds from now. Lock your screen to try it.":"Personal alarm added.");break;
            case "cancelTest":background(()->{JSONObject tests=Store.object(this,"testAlarms");for(java.util.Iterator<String> ids=tests.keys();ids.hasNext();)Scheduler.cancel(this,ids.next());Store.put(this,"testAlarms",new JSONObject());Sync.run(this,false);},"Test alarm cancelled.");break;
            case "delete":background(()->{String id=q.getString("id");for(String key:new String[]{"manual","snoozes"}){JSONObject items=Store.object(this,key);items.remove(id);Store.put(this,key,items);}Scheduler.cancel(this,id);Sync.run(this,false);},"Alarm removed.");break;
        }
    }
    private void preview(String file)throws Exception {preview(file,false);}
    private void preview(String file,boolean fullRamp)throws Exception {preview(file,fullRamp,true);}
    private void previewFailed(String file,boolean fullRamp,boolean allowFallback){
        stopPreview();
        if(allowFallback&&!"tone_12.wav".equals(file)){
            tell("That recording could not play. Previewing the standard backup tone instead.");
            try{preview("tone_12.wav",fullRamp,false);}catch(Exception error){tell("The backup tone could not play. Check your phone’s audio settings.");}
        }else tell("This sound could not play. Choose another recording.");
    }
    private void preview(String file,boolean fullRamp,boolean allowFallback)throws Exception {
        if(!SoundLibrary.available(this,file))throw new Exception("Download the orchestra pack to preview this recording.");
        stopPreview();
        int request=previewToken;
        if(CustomSounds.linked(this,file)){
            if(!PREVIEW_BUSY.compareAndSet(false,true)){previewFailed(file,fullRamp,allowFallback);return;}
            DaybreakApp.PREVIEW_IO.execute(()->{
                MediaPlayer ready=null;boolean prepared=false;
                try{
                    ready=new MediaPlayer();
                    ready.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                    ready.setLooping(fullRamp);SoundLibrary.setDataSource(ready,this,file);ready.prepare();prepared=true;
                }catch(Exception ignored){}finally{PREVIEW_BUSY.set(false);}
                MediaPlayer result=ready;
                if(!prepared){if(result!=null)result.release();runOnUiThread(()->{if(!destroying&&request==previewToken)previewFailed(file,fullRamp,allowFallback);});return;}
                runOnUiThread(()->{
                    if(destroying||request!=previewToken){result.release();return;}
                    preview=result;startPreparedPreview(result,file,fullRamp,allowFallback);
                    handler.postDelayed(this::stopPreview,fullRamp?Math.max(20_000,Store.p(this).getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS)*1000L+5_000):20_000);
                });
            });
            handler.postDelayed(()->{if(!destroying&&request==previewToken&&preview==null)previewFailed(file,fullRamp,allowFallback);},5_000);
            return;
        }
        MediaPlayer candidate=new MediaPlayer();preview=candidate;
        try{
            candidate.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());candidate.setLooping(fullRamp);
            SoundLibrary.setDataSource(candidate,this,file);
            int fade=fullRamp?Store.p(this).getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS):0;
            candidate.setOnPreparedListener(p->startPreparedPreview(p,file,fullRamp,allowFallback));
            candidate.setOnCompletionListener(p->{if(preview==p)stopPreview();});
            candidate.setOnErrorListener((p,w,e)->{if(preview==p)previewFailed(file,fullRamp,allowFallback);return true;});
            candidate.prepareAsync();handler.postDelayed(this::stopPreview,fullRamp?Math.max(20_000,fade*1000L+5_000):20_000);
        }catch(Exception error){stopPreview();if(allowFallback&&!"tone_12.wav".equals(file)){tell("That recording could not play. Previewing the standard backup tone instead.");preview("tone_12.wav",fullRamp,false);}else throw error;}
    }
    private void startPreparedPreview(MediaPlayer p,String file,boolean fullRamp,boolean allowFallback){
        if(destroying||preview!=p)return;
        try{
            int fade=fullRamp?Store.p(this).getInt("fade",AlarmEnvelope.DEFAULT_FADE_SECONDS):0;
            float level=fullRamp?Store.p(this).getInt("volume",80)/100f:Store.p(this).getBoolean("quietPreset",false)?.42f:.6f;
            p.setVolume(level,level);
            if(fullRamp&&fade>0){
                float[] times=new float[16],gains=new float[16];
                for(int point=0;point<16;point++){times[point]=point/15f;gains[point]=AlarmEnvelope.volume(1f,fade,Math.round(times[point]*fade*1000));}
                VolumeShaper.Configuration curve=new VolumeShaper.Configuration.Builder().setDuration(fade*1000L).setCurve(times,gains).setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR).build();
                try{previewShaper=p.createVolumeShaper(curve);}catch(RuntimeException unavailable){previewShaper=null;tell("Volume rise is unavailable on this phone. Previewing at your selected level.");}
            }
            p.start();if(previewShaper!=null)previewShaper.apply(VolumeShaper.Operation.PLAY);
        }catch(RuntimeException error){previewFailed(file,fullRamp,allowFallback);}
    }
    private void stopPreview(){
        ++previewToken;
        handler.removeCallbacksAndMessages(null);
        if(previewShaper!=null){try{previewShaper.close();}catch(RuntimeException ignored){}previewShaper=null;}
        if(preview!=null){try{preview.release();}catch(RuntimeException ignored){}preview=null;}
        if(!destroying&&web!=null)web.evaluateJavascript("window.previewStopped&&window.previewStopped()",null);
    }
}
