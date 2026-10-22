package app.daybreak;

import android.app.*;
import android.content.*;
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

/** Exercises the real Connect form and native HTTP reader with isolated fake HTTPS responses. */
public class CalendarConnectionTest extends Instrumentation {
    private WebView web;private Context context;private int checks;
    private final String day=LocalDate.now().plusDays(3).format(DateTimeFormatter.BASIC_ISO_DATE);
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private String calendar(String body){return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Daybreak connection fixtures//EN\r\n"+body+"\r\nEND:VCALENDAR\r\n";}
    private String fixture(String path){
        if(path.equals("/timed.ics"))return calendar("BEGIN:VEVENT\r\nUID:timed\r\nDTSTART:"+day+"T150000Z\r\nDTEND:"+day+"T230000Z\r\nSUMMARY:Work fixture\r\nEND:VEVENT");
        if(path.equals("/allday.ics"))return calendar("BEGIN:VEVENT\r\nUID:all-day\r\nDTSTART;VALUE=DATE:"+day+"\r\nSUMMARY:Work fixture\r\nEND:VEVENT");
        if(path.equals("/availability.ics"))return calendar("BEGIN:VFREEBUSY\r\nUID:availability\r\nDTSTART:"+day+"T150000Z\r\nDTEND:"+day+"T230000Z\r\nFREEBUSY:"+day+"T150000Z/"+day+"T230000Z\r\nEND:VFREEBUSY");
        return calendar("");
    }
    private void installFixtures(){URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return url.getPath().equals("/forbidden.ics")?403:200;}public InputStream getInputStream(){return new ByteArrayInputStream(fixture(url.getPath()).getBytes(StandardCharsets.UTF_8));}};}});}
    private WebView find(View view){if(view instanceof WebView)return (WebView)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){WebView w=find(g.getChildAt(n));if(w!=null)return w;}}return null;}
    private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result={null};runOnMainSync(()->web.evaluateJavascript(code,value->{result[0]=value;latch.countDown();}));if(!latch.await(5,TimeUnit.SECONDS))throw new Exception("WebView did not respond");return result[0];}
    private void await(Callable<Boolean> condition)throws Exception{long end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){if(condition.call())return;Thread.sleep(100);}throw new Exception("Expected calendar result was not shown");}
    private void check(boolean pass,String message)throws Exception{if(!pass)throw new Exception(message);checks++;Bundle b=new Bundle();b.putString("passed",message);sendStatus(0,b);}
    private void connect(String path)throws Exception{js("page('calendar');$('calendarUrl').value='https://fixture.invalid"+path+"';$('connectForm').requestSubmit();if($('confirmDialog').open)$('yesConfirm').click()");await(()->"false".equals(js("busy")));}
    private void io(Runnable action)throws Exception{DaybreakApp.IO.submit(action).get(10,TimeUnit.SECONDS);}
    private void capture(String name)throws Exception{Thread.sleep(200);android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();java.io.File folder=new java.io.File(context.getFilesDir(),"qa");folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void updateUI()throws Exception{js("send('state')");await(()->"true".equals(js("!!st")));Thread.sleep(150);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();installFixtures();
        io(()->{try{for(java.util.Iterator<String> it=Store.object(context,"scheduled").keys();it.hasNext();)Scheduler.cancel(context,it.next());Store.p(context).edit().clear().commit();Store.cache(context,"");}catch(Exception e){throw new RuntimeException(e);}});
        Activity activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->web=find(activity.findViewById(android.R.id.content)));
        await(()->"true".equals(js("typeof window.receive==='function'&&!!st")));
        connect("/timed.ics");check(Store.array(context,"shifts").length()==1,"Connect form downloads and displays timed shift");
        check("1".equals(js("document.querySelectorAll('#calendarShiftList .alarm-row').length")),"Shift appears directly on Calendar screen");
        check("\"\"".equals(js("$('calendarUrl').value")),"Successful connection clears typed link");
        js("$('addCalendar').checked=true;$('calendarName').value='Second job';$('calendarUrl').value='https://fixture.invalid/timed.ics';$('connectForm').requestSubmit()");await(()->Store.array(context,"shifts").length()==2);
        check(CalendarSources.extras(context).length()==1&&"true".equals(js("st.calendars.length===2")),"A second calendar keeps the first connection and both shifts");
        String extraId=CalendarSources.extras(context).getJSONObject(0).getString("id");
        io(()->{try{CalendarSources.update(context,extraId,"Second job","Work",30,false,480);}catch(Exception e){throw new RuntimeException(e);}});
        JSONArray both=Store.array(context,"shifts");check(both.getJSONObject(0).optInt("leadMinutes")!=both.getJSONObject(1).optInt("leadMinutes"),"Each calendar can schedule with its own wake-up lead time");
        io(()->{try{JSONArray sources=CalendarSources.extras(context);sources.getJSONObject(0).put("url","https://fixture.invalid/forbidden.ics");Store.put(context,"extraCalendars",sources);Sync.run(context,true);}catch(Exception e){throw new RuntimeException(e);}});
        check(Store.array(context,"shifts").length()==2&&"failed".equals(CalendarConfidence.snapshot(context).getString("status")),"A failed second feed keeps its cached alarm and shows a calendar warning");
        io(()->{try{JSONArray sources=CalendarSources.extras(context);sources.getJSONObject(0).put("url","https://fixture.invalid/timed.ics");Store.put(context,"extraCalendars",sources);Sync.run(context,true);}catch(Exception e){throw new RuntimeException(e);}});
        check("fresh".equals(CalendarConfidence.snapshot(context).getString("status")),"A recovered second feed clears the calendar warning");
        io(()->{try{CalendarSources.remove(context,extraId);}catch(Exception e){throw new RuntimeException(e);}});updateUI();check(Store.array(context,"shifts").length()==1&&CalendarSources.extras(context).length()==0,"Removing one calendar preserves the first calendar's shift");
        io(()->{Store.p(context).edit().putString("filter","unmatched").commit();Sync.run(context,false);});updateUI();
        check(Store.object(context,"calendarInfo").optInt("filtered")==1,"Counts identify entries hidden by title filter");
        check("true".equals(js("$('calendarFeedback').textContent.includes('title filter')")),"Empty-result reason is visible on Calendar screen");
        js("send('clearFilter')");await(()->Store.array(context,"shifts").length()==1);check(true,"Clear-filter action restores hidden shift");
        connect("/allday.ics");check(Store.array(context,"shifts").length()==1&&Store.array(context,"shifts").getJSONObject(0).optBoolean("needsTime"),"All-day shift stays visible with no assumed start time");
        check(Store.object(context,"scheduled").length()==0,"All-day shift has no alarm until time is chosen");
        check("true".equals(js("$('calendarShiftList').textContent.includes('Start time needed')")),"All-day row clearly requests a start time");capture("calendar-all-day-fix");
        js("page('settings');$('allDayEnabled').checked=true;$('allDayTime').value='09:00';$('settingsForm').requestSubmit()");await(()->Store.p(context).getBoolean("allDayEnabled",false));
        js("send('enable',{value:true})");await(()->Store.object(context,"scheduled").length()==1);
        JSONObject alarm=Store.array(context,"shifts").getJSONObject(0);java.util.Calendar when=java.util.Calendar.getInstance();when.setTimeInMillis(alarm.getLong("start"));
        check(!alarm.optBoolean("needsTime")&&when.get(java.util.Calendar.HOUR_OF_DAY)==9,"Chosen all-day start schedules native alarm at correct time");
        connect("/empty.ics");check(Store.array(context,"shifts").length()==0&&Store.object(context,"scheduled").length()==0,"Empty replacement cancels previous work alarm");
        check("true".equals(js("$('calendarFeedback').textContent.includes('contains no events')")),"Empty download has a visible explanation");capture("calendar-empty-fix");
        connect("/availability.ics");check(Store.object(context,"calendarInfo").optInt("availability")==1,"Availability-only feed identified");
        check("true".equals(js("$('calendarFeedback').textContent.includes('Full view')")),"Availability-only feed offers Full view guidance");
        connect("/forbidden.ics");check("true".equals(js("$('calendarFeedback').textContent.includes('403')")),"HTTP failure stays visible on Calendar screen");
        check("true".equals(js("$('calendarUrl').value.endsWith('/forbidden.ics')")),"Failed connection retains link for correction");
        check(Store.url(context).endsWith("/availability.ics"),"Failed replacement preserves working connection");
        io(()->{Store.p(context).edit().clear().commit();try{Store.cache(context,"");Sync.run(context,false);}catch(Exception e){throw new RuntimeException(e);}});
        result.putString("summary",checks+" calendar connection regression checks passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
