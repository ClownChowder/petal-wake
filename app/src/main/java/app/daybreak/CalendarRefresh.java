package app.daybreak;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Downloads calendars without occupying the worker that saves settings and alarms. */
final class CalendarRefresh {
    private static final AtomicBoolean inProgress=new AtomicBoolean();
    private static final class Source {
        final String id,url;String data,error;boolean attempted;
        Source(String id,String url){this.id=id;this.url=url;}
    }
    private static final class Batch {
        long started;String primaryUrl;Source primary;final ArrayList<Source> extras=new ArrayList<>();
    }
    static boolean request(Context context,BooleanSupplier valid,Runnable finished){
        if(!inProgress.compareAndSet(false,true))return false;
        Context c=context.getApplicationContext();
        DaybreakApp.IO.execute(()->{
            try{
                Batch batch=snapshot(c);
                DaybreakApp.NETWORK_IO.execute(()->{
                    boolean readFailed=false;
                    try{fetch(c,batch);}catch(RuntimeException error){readFailed=true;}
                    boolean failed=readFailed;
                    DaybreakApp.IO.execute(()->{
                        try{if(valid.getAsBoolean()){apply(c,batch);if(failed)Store.error(c,"Calendar refresh failed. Saved alarms remain in place.");}}
                        catch(Exception error){Store.error(c,"Calendar refresh failed. Saved alarms remain in place.");}
                        finally{inProgress.set(false);finished.run();}
                    });
                });
            }catch(Exception error){Store.error(c,"Calendar refresh failed. Saved alarms remain in place.");inProgress.set(false);finished.run();}
        });
        return true;
    }
    private static Batch snapshot(Context c){
        Batch batch=new Batch();batch.started=System.currentTimeMillis();batch.primaryUrl=Store.url(c);
        if(!batch.primaryUrl.isEmpty())batch.primary=new Source("primary",batch.primaryUrl);
        JSONArray extras=CalendarSources.extras(c);
        for(int i=0;i<extras.length();i++){JSONObject source=extras.optJSONObject(i);if(source!=null)batch.extras.add(new Source(source.optString("id"),source.optString("url")));}
        return batch;
    }
    private static void fetch(Context c,Batch batch){
        if(batch.primary!=null&&batch.primaryUrl.equals(Store.url(c)))fetch(batch.primary);
        for(Source source:batch.extras)if(source.url.equals(CalendarSources.link(c,source.id)))fetch(source);
    }
    private static void fetch(Source source){
        source.attempted=true;
        try{source.data=Sync.download(source.url);Sync.validate(source.data);}
        catch(Exception error){source.data=null;source.error=Store.safeError(error.getMessage()==null?"Download failed.":error.getMessage());}
    }
    private static void apply(Context c,Batch batch)throws Exception{
        synchronized(Scheduler.class){
            JSONArray before=Store.array(c,"shifts");boolean downloaded=false;String primaryError="";
            if(batch.primary!=null&&batch.primary.attempted&&batch.primaryUrl.equals(Store.url(c))&&CalendarSources.hasPrimary(c)
                    &&Store.p(c).getLong("lastSync",0)<=batch.started){
                CalendarConfidence.attempted(c);
                if(batch.primary.data!=null){
                    Store.cache(c,batch.primary.data);Store.p(c).edit().putLong("lastSync",System.currentTimeMillis()).commit();
                    CalendarConfidence.succeeded(c);downloaded=true;
                }else{CalendarConfidence.failed(c);primaryError=batch.primary.error;}
            }
            JSONArray extras=CalendarSources.extras(c);boolean updated=false;
            for(Source result:batch.extras)if(result.attempted)for(int i=0;i<extras.length();i++){
                JSONObject current=extras.optJSONObject(i);
                if(current==null||!result.id.equals(current.optString("id"))||!result.url.equals(current.optString("url"))
                        ||current.optLong("lastSync")>batch.started)continue;
                long attempted=System.currentTimeMillis();current.put("attempt",attempted);updated=true;
                if(result.data!=null){
                    CalendarSources.data(c,result.id,result.data);
                    current.put("lastSync",System.currentTimeMillis()).put("error","").put("failures",0).remove("firstFailure");downloaded=true;
                }else{
                    current.put("error",result.error).put("failures",Math.min(1000,current.optInt("failures",0)+1));
                    if(current.optLong("firstFailure")<=0)current.put("firstFailure",attempted);
                }
                break;
            }
            if(updated)Store.put(c,"extraCalendars",extras);
            Sync.run(c,false);
            if(!primaryError.isEmpty()&&Store.p(c).getString("error","").isEmpty())Store.error(c,primaryError);
            if(downloaded&&Store.p(c).getBoolean("enabled",false))
                ShiftChanges.publish(c,ShiftChanges.diff(before,Store.array(c,"shifts"),System.currentTimeMillis()));
        }
    }
    private CalendarRefresh(){}
}
