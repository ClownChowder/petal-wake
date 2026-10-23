package app.daybreak;
import android.content.Context;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Sync {
    public static final long DAY=86_400_000L;
    public static String download(String url)throws Exception {
        URL u=new URL(url.trim().replaceFirst("^webcal:","https:"));
        for(int n=0;n<4;n++) {
            if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null)throw new IOException("Use a secure https:// calendar sharing link.");
            HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","text/calendar");
            try {
                int code=c.getResponseCode();
                if(code>=300&&code<400){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("Calendar redirected without a destination.");u=new URL(u,loc);continue;}
                if(code!=200)throw new IOException("Calendar server returned "+code+". Check that your sharing link is still active.");
                try(InputStream s=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] b=new byte[8192];int read;
                    while((read=s.read(b))!=-1){out.write(b,0,read);if(out.size()>4_000_000)throw new IOException("Calendar is too large. Use a calendar dedicated to work.");}
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            } finally{c.disconnect();}
        }
        throw new IOException("Calendar link redirected too many times.");
    }
    public static void connect(Context c,String url)throws Exception {
        String normalized=url.trim().replaceFirst("^webcal:","https:");
        String data=download(normalized);validate(data);Store.cache(c,data);Store.setUrl(c,normalized);
        Store.p(c).edit().putString("source","Shared calendar link").putLong("lastSync",System.currentTimeMillis()).putString("error","").commit();
        CalendarConfidence.succeeded(c);CalendarConfidence.attempted(c);run(c,false);
    }
    public static void importFile(Context c,String data)throws Exception {
        validate(data);Store.cache(c,data);
        Store.setUrl(c,"");Store.p(c).edit().putString("source","Imported calendar file").putLong("lastSync",System.currentTimeMillis()).putString("error","").commit();CalendarConfidence.reset(c);run(c,false);
    }
    private static void validate(String data){CalendarReader.read(data,System.currentTimeMillis()-DAY,System.currentTimeMillis()+60*DAY);}
    public static void run(Context c,boolean fetch) {
        var p=Store.p(c);long now=System.currentTimeMillis();String error="";boolean downloaded=false;JSONArray previousShifts=Store.array(c,"shifts");
        String primaryUrl=Store.url(c);
        if(fetch&&!primaryUrl.trim().isEmpty()) {
            CalendarConfidence.attempted(c);
            try{String data=download(primaryUrl);validate(data);Store.cache(c,data);p.edit().putLong("lastSync",System.currentTimeMillis()).commit();CalendarConfidence.succeeded(c);downloaded=true;}
            catch(Exception e){CalendarConfidence.failed(c);error=e.getMessage()==null?"Refresh failed. Existing offline alarms are kept.":Store.safeError(e.getMessage());}
        }
        JSONArray extras=CalendarSources.extras(c);
        if(fetch){
            for(int i=0;i<extras.length();i++)try{
                JSONObject source=extras.getJSONObject(i);
                long attempted=System.currentTimeMillis();source.put("attempt",attempted);
                try{
                    String body=download(source.getString("url"));validate(body);CalendarSources.data(c,source.getString("id"),body);
                    source.put("lastSync",System.currentTimeMillis()).put("error","").put("failures",0).remove("firstFailure");downloaded=true;
                }catch(Exception e){source.put("error",e.getMessage()==null?"Download failed.":Store.safeError(e.getMessage())).put("failures",Math.min(1000,source.optInt("failures",0)+1));if(source.optLong("firstFailure")<=0)source.put("firstFailure",attempted);}
            }catch(Exception ignored){}
            Store.put(c,"extraCalendars",extras);
        }
        synchronized(Scheduler.class) {
        now=System.currentTimeMillis();
        try {
            JSONArray visible=new JSONArray();JSONObject desired=new JSONObject(),skipped=Store.object(c,"skipped");
            ArrayList<JSONObject> all=new ArrayList<>();CalendarReader.Stats totals=new CalendarReader.Stats();
            int upcomingTimed=0,upcomingAllDay=0,filtered=0,needsTimeCount=0,late=0;
            JSONArray sources=new JSONArray();
            if(CalendarSources.hasPrimary(c))sources.put(new JSONObject().put("id","primary").put("name","Work calendar").put("data",Store.cache(c)).put("filter",p.getString("filter","")).put("lead",p.getInt("lead",90)).put("allDayEnabled",p.getBoolean("allDayEnabled",false)).put("allDayMinutes",p.getInt("allDayMinutes",480)));
            for(int i=0;i<extras.length();i++){JSONObject extra=extras.getJSONObject(i);sources.put(new JSONObject(extra.toString()).put("data",CalendarSources.data(c,extra.getString("id"))));if(!extra.optString("error").isEmpty())error=extra.optString("name","Calendar")+": "+extra.optString("error");}
            for(int i=0;i<sources.length();i++){
                JSONObject source=sources.getJSONObject(i);String data=source.optString("data","");if(data.trim().isEmpty())continue;
                boolean allDayEnabled=source.optBoolean("allDayEnabled",false);
                List<Shift> shifts=CalendarReader.readWithAllDay(data,now,now+60*DAY,allDayEnabled?source.optInt("allDayMinutes",480):-1);
                CalendarReader.Stats stats=CalendarReader.inspect(data);
                totals.events+=stats.events;totals.timed+=stats.timed;totals.allDay+=stats.allDay;totals.cancelled+=stats.cancelled;totals.missingStart+=stats.missingStart;totals.availability+=stats.availability;
                for(Shift shift:shifts){
                    if(shift.allDay)upcomingAllDay++;else upcomingTimed++;
                    if(!CalendarReader.matches(shift.title,source.optString("filter",""))){filtered++;continue;}
                    boolean needsTime=shift.allDay&&!allDayEnabled;if(needsTime)needsTimeCount++;
                    int ordinary=source.optInt("lead",90);
                    int lead=ShiftRules.lead(shift.start,shift.allDay,ordinary,p.getBoolean("shiftRulesEnabled",false),p.getInt("earlyBefore",600),p.getInt("earlyLead",90),p.getInt("lateAfter",720),p.getInt("lateLead",45));
                    long at=needsTime?0:CalendarReader.wakeTime(shift.start,lead);
                    String id="primary".equals(source.optString("id"))?"shift:"+shift.id:"shift:"+source.getString("id")+":"+shift.id;
                    all.add(new JSONObject().put("id",id).put("title",shift.title).put("at",at).put("start",shift.start).put("end",shift.end).put("kind","shift").put("skipped",skipped.optBoolean(id)).put("allDay",shift.allDay).put("needsTime",needsTime).put("leadMinutes",lead).put("calendarId",source.getString("id")).put("calendarName",source.optString("name","Work calendar")));
                }
            }
            all.sort(Comparator.comparingLong(a->a.optLong("start")));
            for(JSONObject alarm:all){
                if(visible.length()<128)visible.put(alarm);
                String id=alarm.getString("id");long at=alarm.optLong("at");
                if(!alarm.optBoolean("needsTime")&&p.getBoolean("enabled",false)&&!skipped.optBoolean(id)){
                    if(at>now&&desired.length()<128)desired.put(id,alarm);else if(at<=now)late++;
                }
            }
            JSONObject manual=Store.object(c,"manual");
            for(Iterator<String> it=manual.keys();it.hasNext();) {String id=it.next();JSONObject a=manual.getJSONObject(id);if(a.optLong("at")>now)desired.put(id,a);}
            // Snoozes are independent alarms: a calendar refresh must never remove one.
            JSONObject snoozes=Store.object(c,"snoozes");
            for(Iterator<String> it=snoozes.keys();it.hasNext();) {String id=it.next();JSONObject a=snoozes.getJSONObject(id);if(a.optLong("at")>now)desired.put(id,a);}
            Store.put(c,"shifts",visible);p.edit().putInt("late",late).commit();
            Store.put(c,"calendarInfo",new JSONObject().put("events",totals.events).put("timed",totals.timed).put("allDay",totals.allDay).put("cancelled",totals.cancelled).put("missingStart",totals.missingStart).put("availability",totals.availability).put("upcomingTimed",upcomingTimed).put("upcomingAllDay",upcomingAllDay).put("filtered",filtered).put("needsTime",needsTimeCount).put("windowDays",60));
            Scheduler.reconcile(c,desired);
            if(downloaded&&p.getBoolean("enabled",false))ShiftChanges.publish(c,ShiftChanges.diff(previousShifts,visible,now));
        } catch(Exception e){error="Calendar could not be read. Existing alarms are kept. "+Store.safeError(e.getMessage());}
        Store.error(c,error);
        CalendarConfidence.update(c);
        EveningCheck.update(c);
        Readiness.update(c);
        }
    }
}
