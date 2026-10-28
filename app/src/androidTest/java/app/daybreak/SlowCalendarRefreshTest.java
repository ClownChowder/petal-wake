package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A slow calendar server cannot hold settings, and stale results cannot restore a removed source. */
public final class SlowCalendarRefreshTest extends Instrumentation {
    private volatile boolean slow;
    private volatile int responseCode=200;
    private volatile CountDownLatch entered,release;
    private final AtomicInteger extraRequests=new AtomicInteger();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private String calendar(String id){
        String day=LocalDate.now().plusDays(3).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:"+id+"\r\nDTSTART:"+day+"T150000Z\r\nDURATION:PT8H\r\nSUMMARY:Work fixture\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
    }
    private void fixtures(){URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){
        @Override protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
            @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
            @Override public int getResponseCode(){return SlowCalendarRefreshTest.this.responseCode;}
            @Override public InputStream getInputStream(){
                if("/extra.ics".equals(url.getPath()))extraRequests.incrementAndGet();
                if(slow){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new RuntimeException("Synthetic server timed out");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}}
                return new ByteArrayInputStream(calendar(url.getPath()).getBytes(StandardCharsets.UTF_8));
            }
        };}
    });}
    private void blockNext(){entered=new CountDownLatch(1);release=new CountDownLatch(1);slow=true;}
    private void saveQuickly(Future<?> action)throws Exception{action.get(2,TimeUnit.SECONDS);}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();try{
        // A freshly installed app can start its periodic refresh as this
        // instrumentation begins. Drain it before installing the slow fixture.
        c.getSystemService(JobScheduler.class).cancel(100);
        DaybreakApp.IO.submit(()->{}).get(10,TimeUnit.SECONDS);
        DaybreakApp.NETWORK_IO.submit(()->{}).get(10,TimeUnit.SECONDS);
        DaybreakApp.IO.submit(()->{}).get(10,TimeUnit.SECONDS);
        fixtures();
        DaybreakApp.IO.submit(()->{try{
            JSONObject old=Store.object(c,"scheduled");for(java.util.Iterator<String> ids=old.keys();ids.hasNext();)Scheduler.cancel(c,ids.next());
            Store.p(c).edit().clear().putInt("lead",90).putBoolean("enabled",false).commit();Store.cache(c,"");
            Sync.connect(c,"https://fixture.invalid/primary.ics");CalendarSources.add(c,"Second","https://fixture.invalid/extra.ics");
        }catch(Exception error){throw new RuntimeException(error);}}).get(10,TimeUnit.SECONDS);
        if(Store.array(c,"shifts").length()!=2)throw new Exception("Synthetic calendars did not connect.");
        java.lang.reflect.Field refreshField=CalendarRefresh.class.getDeclaredField("inProgress");
        refreshField.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean refreshBusy=(java.util.concurrent.atomic.AtomicBoolean)refreshField.get(null);
        long idleDeadline=System.currentTimeMillis()+10_000L;
        while(refreshBusy.get()&&System.currentTimeMillis()<idleDeadline)Thread.sleep(50);
        if(refreshBusy.get())throw new Exception("Startup calendar refresh did not become idle.");
        extraRequests.set(0);
        String extraId=CalendarSources.extras(c).getJSONObject(0).getString("id");
        blockNext();CountDownLatch done=new CountDownLatch(1);
        if(!CalendarRefresh.request(c,()->true,done::countDown))throw new Exception("A separate calendar refresh was already in progress.");
        if(!entered.await(5,TimeUnit.SECONDS))throw new Exception("The calendar network worker did not enter the slow fixture.");
        saveQuickly(DaybreakApp.IO.submit(()->{Store.p(c).edit().putInt("lead",47).commit();Sync.run(c,false);}));
        saveQuickly(DaybreakApp.IO.submit(()->{try{CalendarSources.remove(c,extraId);}catch(Exception error){throw new RuntimeException(error);}}));
        release.countDown();if(!done.await(10,TimeUnit.SECONDS))throw new Exception("Slow refresh did not finish.");
        JSONArray shifts=Store.array(c,"shifts");
        if(CalendarSources.extras(c).length()!=0||shifts.length()!=1||shifts.getJSONObject(0).optInt("leadMinutes")!=47||extraRequests.get()!=0)
            throw new Exception("Refresh restored a removed source or overwrote saved settings: extras="+CalendarSources.extras(c).length()+" shifts="+shifts.length()+" lead="+shifts.getJSONObject(0).optInt("leadMinutes")+" requests="+extraRequests.get());
        slow=false;responseCode=403;done=new CountDownLatch(1);CountDownLatch failed=done;
        if(!CalendarRefresh.request(c,()->true,failed::countDown)||!done.await(5,TimeUnit.SECONDS))throw new Exception("Failed refresh did not finish.");
        if(Store.array(c,"shifts").length()!=1||!"failed".equals(CalendarConfidence.snapshot(c).optString("status")))
            throw new Exception("Failed background download removed the saved shift or hid its warning.");
        responseCode=200;
        blockNext();done=new CountDownLatch(1);CountDownLatch completed=done;
        if(!CalendarRefresh.request(c,()->true,completed::countDown)||!entered.await(5,TimeUnit.SECONDS))throw new Exception("Second slow refresh did not start.");
        saveQuickly(DaybreakApp.IO.submit(()->{try{Store.setUrl(c,"");Store.p(c).edit().remove("source").putBoolean("enabled",false).commit();Store.cache(c,"");Sync.run(c,false);}catch(Exception error){throw new RuntimeException(error);}}));
        release.countDown();if(!done.await(10,TimeUnit.SECONDS))throw new Exception("Second slow refresh did not finish.");
        if(!Store.url(c).isEmpty()||!Store.cache(c).isEmpty()||CalendarSources.hasPrimary(c))throw new Exception("Stale response restored a disconnected private calendar.");
        result.putString("summary","Settings saved during blocked fetch; failed fetch kept offline shifts; removed and disconnected sources stayed removed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){if(release!=null)release.countDown();result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
