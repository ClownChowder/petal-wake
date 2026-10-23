package app.daybreak;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.component.VFreeBusy;
import biweekly.io.TimezoneAssignment;
import biweekly.parameter.FreeBusyType;
import biweekly.property.FreeBusy;
import biweekly.util.Period;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import java.util.*;

/** Expands RFC5545 recurrence in the event's timezone, including exceptions and moved instances. */
public final class CalendarReader {
    private static String uid(VEvent e) {
        if(e.getUid()==null) throw new IllegalArgumentException("Calendar event is missing its UID.");
        return e.getUid().getValue();
    }
    private static boolean cancelled(VEvent e) { return e.getStatus()!=null && "CANCELLED".equalsIgnoreCase(e.getStatus().getValue()); }
    private static int sequence(VEvent e) { return e.getSequence()==null?0:e.getSequence().getValue(); }
    private static long modified(VEvent e) { return e.getLastModified()==null?0:e.getLastModified().getValue().getTime(); }
    private static void keep(Map<String,VEvent> map,String key,VEvent e) {
        VEvent old=map.get(key);
        if(old==null || sequence(e)>sequence(old) || (sequence(e)==sequence(old)&&modified(e)>=modified(old)))map.put(key,e);
    }
    public static List<Shift> read(String text,long from,long to) {
        return read(text,from,to,false,-1);
    }
    /** All-day entries stay visible; a negative start time means they cannot schedule an alarm. */
    public static List<Shift> readWithAllDay(String text,long from,long to,int allDayMinutes) {
        if(allDayMinutes < -1 || allDayMinutes>1439)throw new IllegalArgumentException("Choose a valid all-day shift start time.");
        return read(text,from,to,true,allDayMinutes);
    }
    public static final class Stats {
        public int events,allDay,timed,cancelled,missingStart,availability;
    }
    public static Stats inspect(String text) {
        ICalendar cal=parse(text);Stats stats=new Stats();stats.events=cal.getEvents().size();stats.availability=cal.getFreeBusies().size();
        for(VEvent e:cal.getEvents()){
            if(cancelled(e))stats.cancelled++;
            else if(e.getDateStart()==null)stats.missingStart++;
            else if(e.getDateStart().getValue().hasTime())stats.timed++;
            else stats.allDay++;
        }
        return stats;
    }
    private static ICalendar parse(String text) {
        if(text!=null)text=text.replace("\uFEFF", "").trim();
        if(text==null || !text.toUpperCase(Locale.ROOT).startsWith("BEGIN:VCALENDAR") || !text.toUpperCase(Locale.ROOT).contains("END:VCALENDAR"))
            throw new IllegalArgumentException("This is not an iCalendar file. Use a shared iCalendar link or an exported .ics file.");
        ICalendar cal=Biweekly.parse(text).first();
        if(cal==null)throw new IllegalArgumentException("Calendar could not be read.");
        return cal;
    }
    private static String occurrenceKey(String uid,long stamp,boolean allDay){
        return uid+":"+(allDay?"date:"+java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate():stamp);
    }
    private static long startOfDay(long stamp){return java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private static List<Shift> read(String text,long from,long to,boolean includeAllDay,int allDayMinutes) {
        ICalendar cal=parse(text);
        // Availability-only feeds (including Proton Limited view) contain no event
        // details. Never mix these periods with VEVENTs, which could double alarms.
        if(cal.getEvents().isEmpty()&&!cal.getFreeBusies().isEmpty())
            return readAvailability(cal,from,to,includeAllDay,allDayMinutes);
        Map<String,VEvent> masters=new LinkedHashMap<>(), overrides=new LinkedHashMap<>();
        for(VEvent e:cal.getEvents()) {
            String id=uid(e);
            if(e.getRecurrenceId()!=null) {
                if(e.getRecurrenceId().getParameters().get("RANGE")!=null && !e.getRecurrenceId().getParameters().get("RANGE").isEmpty())
                    throw new IllegalArgumentException("This calendar uses a future-series override. Export separate shift events instead.");
                keep(overrides,occurrenceKey(id,e.getRecurrenceId().getValue().getTime(),!e.getRecurrenceId().getValue().hasTime()),e);
            } else keep(masters,id,e);
        }
        List<Shift> out=new ArrayList<>();
        boolean cancelAll=cal.getMethod()!=null && "CANCEL".equalsIgnoreCase(cal.getMethod().getValue());
        if(cancelAll)return out;
        for(VEvent e:masters.values()) {
            if(cancelled(e)||e.getDateStart()==null)continue;
            boolean allDay=!e.getDateStart().getValue().hasTime();
            if(allDay&&!includeAllDay)continue;
            for(var dates:e.getRecurrenceDates()) if(!dates.getPeriods().isEmpty())
                throw new IllegalArgumentException("Calendar contains unsupported period-based recurrence dates.");
            TimezoneAssignment zone=cal.getTimezoneInfo().getTimezone(e.getDateStart());
            TimeZone tz=zone==null?TimeZone.getDefault():zone.getTimeZone();
            DateIterator it=e.getDateIterator(tz);
            it.advanceTo(new Date(allDay?startOfDay(from):from));
            int count=0;
            while(it.hasNext()) {
                long start=it.next().getTime();if(start>=to)break;
                if(++count>1000)throw new IllegalArgumentException("Too many repeating events. Use a calendar dedicated to work shifts.");
                String id=occurrenceKey(uid(e),start,allDay);
                if(!overrides.containsKey(id))add(out,e,id,start,from,to,allDayMinutes);
            }
        }
        // Moved instances can enter the requested range even when the original date is outside it.
        for(Map.Entry<String,VEvent> entry:overrides.entrySet()) {
            VEvent e=entry.getValue();
            VEvent master=masters.get(uid(e));
            if(master!=null && cancelled(master))continue;
            if(!cancelled(e)&&e.getDateStart()!=null&&(includeAllDay||e.getDateStart().getValue().hasTime()))add(out,e,entry.getKey(),e.getDateStart().getValue().getTime(),from,to,allDayMinutes);
        }
        out.sort(Comparator.comparingLong(s->s.start));
        if(out.size()>1000)throw new IllegalArgumentException("More than 1,000 shifts in 60 days. Use a work-only calendar.");
        return out;
    }
    private static List<Shift> readAvailability(ICalendar cal,long from,long to,boolean includeAllDay,int allDayMinutes) {
        List<long[]> periods=new ArrayList<>();
        for(VFreeBusy component:cal.getFreeBusies())for(FreeBusy property:component.getFreeBusy()){
            FreeBusyType type=property.getType();
            if(type!=null&&type!=FreeBusyType.BUSY&&type!=FreeBusyType.BUSY_UNAVAILABLE)continue;
            for(Period period:property.getValues()){
                if(period.getStartDate()==null)continue;
                long start=period.getStartDate().getTime();
                long end=period.getEndDate()!=null?period.getEndDate().getTime()
                    :period.getDuration()!=null?period.getDuration().add(new Date(start)).getTime():start;
                if(end<=start)continue;
                periods.add(new long[]{start,end});
                if(periods.size()>5000)throw new IllegalArgumentException("Too many busy intervals. Use a work-only calendar.");
            }
        }
        periods.sort(Comparator.comparingLong(a->a[0]));
        List<long[]> merged=new ArrayList<>();
        for(long[] period:periods){
            if(!merged.isEmpty()&&period[0]<merged.get(merged.size()-1)[1])
                merged.get(merged.size()-1)[1]=Math.max(merged.get(merged.size()-1)[1],period[1]);
            else merged.add(period.clone());
        }
        List<Shift> out=new ArrayList<>();
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        for(long[] period:merged){
            long rawStart=period[0],end=period[1];
            java.time.ZonedDateTime localStart=java.time.Instant.ofEpochMilli(rawStart).atZone(zone);
            boolean allDay=localStart.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                &&java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                &&java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate().isAfter(localStart.toLocalDate());
            if(allDay&&!includeAllDay)continue;
            long start=allDay&&allDayMinutes>=0
                ?localStart.toLocalDate().atTime(allDayMinutes/60,allDayMinutes%60).atZone(zone).toInstant().toEpochMilli():rawStart;
            if(start<(allDay&&allDayMinutes<0?startOfDay(from):from)||start>=to)continue;
            String id="availability:"+rawStart+":"+end;
            out.add(new Shift(id,"Busy time",start,end,allDay));
            if(out.size()>1000)throw new IllegalArgumentException("More than 1,000 busy intervals in 60 days. Use a work-only calendar.");
        }
        return out;
    }
    private static void add(List<Shift> out,VEvent e,String id,long rawStart,long from,long to,int allDayMinutes) {
        boolean allDay=!e.getDateStart().getValue().hasTime();long start=rawStart;
        if(allDay&&allDayMinutes>=0){java.time.LocalDate date=java.time.Instant.ofEpochMilli(rawStart).atZone(java.time.ZoneId.systemDefault()).toLocalDate();start=date.atTime(allDayMinutes/60,allDayMinutes%60).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();}
        if(start<(allDay&&allDayMinutes<0?startOfDay(from):from)||start>=to)return;
        long duration=0;
        if(e.getDateEnd()!=null&&e.getDateStart()!=null)duration=e.getDateEnd().getValue().getTime()-e.getDateStart().getValue().getTime();
        else if(e.getDuration()!=null)duration=e.getDuration().getValue().toMillis();
        String title=e.getSummary()==null?"Work shift":e.getSummary().getValue();
        out.add(new Shift(id,title==null||title.trim().isEmpty()?"Work shift":title,start,start+Math.max(0,duration),allDay));
    }
    public static long wakeTime(long start,int leadMinutes) {
        if(leadMinutes<0||leadMinutes>1440)throw new IllegalArgumentException("Lead time must be between 0 and 1,440 minutes.");
        return Math.subtractExact(start,leadMinutes*60_000L);
    }
    public static boolean matches(String title,String filter) {
        return filter.trim().isEmpty()||title.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }
}
