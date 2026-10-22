package app.daybreak;
import org.junit.*;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

public class CalendarReaderTest {
    private long at(String text){return Instant.parse(text).toEpochMilli();}
    private String calendar(String...events){return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Daybreak tests//EN\r\n"+String.join("\r\n",events)+"\r\nEND:VCALENDAR\r\n";}
    private String event(String body){return "BEGIN:VEVENT\r\n"+body+"\r\nEND:VEVENT";}
    @Before public void zone(){TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));}
    @Test public void weeklyPreservesLocalTimeAcrossFallDST(){
        String ics=calendar(event("UID:work\r\nDTSTART;TZID=America/New_York:20261030T070000\r\nDTEND;TZID=America/New_York:20261030T150000\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\nSUMMARY:Work"));
        var shifts=CalendarReader.read(ics,at("2026-10-29T00:00:00Z"),at("2026-11-20T00:00:00Z"));assertEquals(3,shifts.size());
        assertEquals(at("2026-10-30T11:00:00Z"),shifts.get(0).start);assertEquals(at("2026-11-06T12:00:00Z"),shifts.get(1).start);
    }
    @Test public void weeklyPreservesLocalTimeAcrossSpringDST(){
        String ics=calendar(event("UID:work\r\nDTSTART;TZID=America/New_York:20270305T070000\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\nSUMMARY:Work"));
        var s=CalendarReader.read(ics,at("2027-03-01T00:00:00Z"),at("2027-04-01T00:00:00Z"));assertEquals(3,s.size());assertEquals(at("2027-03-05T12:00:00Z"),s.get(0).start);assertEquals(at("2027-03-12T12:00:00Z"),s.get(1).start);assertEquals(at("2027-03-19T11:00:00Z"),s.get(2).start);
    }
    @Test public void movedAndCancelledOccurrencesReplaceOriginal(){
        String ics=calendar(event("UID:a\r\nDTSTART:20261012T120000Z\r\nRRULE:FREQ=DAILY;COUNT=3\r\nSUMMARY:Work"),event("UID:a\r\nRECURRENCE-ID:20261013T120000Z\r\nDTSTART:20261013T150000Z\r\nSUMMARY:Late shift"),event("UID:a\r\nRECURRENCE-ID:20261014T120000Z\r\nSTATUS:CANCELLED"));
        var s=CalendarReader.read(ics,at("2026-10-11T00:00:00Z"),at("2026-10-16T00:00:00Z"));assertEquals(2,s.size());assertEquals(at("2026-10-13T15:00:00Z"),s.get(1).start);assertEquals("a:"+at("2026-10-13T12:00:00Z"),s.get(1).id);
    }
    @Test public void exdateRdateAndFoldedTitles(){
        String ics=calendar(event("UID:a\r\nDTSTART:20261012T120000Z\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEXDATE:20261013T120000Z\r\nRDATE:20261016T120000Z\r\nSUMMARY:Morning\r\n  shift"));
        var s=CalendarReader.read(ics,at("2026-10-11T00:00:00Z"),at("2026-10-20T00:00:00Z"));assertEquals(3,s.size());assertEquals("Morning shift",s.get(0).title);assertEquals(at("2026-10-16T12:00:00Z"),s.get(2).start);
    }
    @Test public void allDayAndCancelledDoNotCreateAlarms(){
        String ics=calendar(event("UID:a\r\nDTSTART;VALUE=DATE:20261012\r\nSUMMARY:Day off"),event("UID:b\r\nDTSTART:20261012T120000Z\r\nSTATUS:CANCELLED"));assertTrue(CalendarReader.read(ics,at("2026-10-01T00:00:00Z"),at("2026-11-01T00:00:00Z")).isEmpty());
    }
    @Test public void newestSequenceWins(){
        String ics=calendar(event("UID:a\r\nDTSTART:20261012T120000Z\r\nSEQUENCE:2\r\nSUMMARY:Updated"),event("UID:a\r\nDTSTART:20261012T100000Z\r\nSEQUENCE:1\r\nSUMMARY:Old"));var s=CalendarReader.read(ics,at("2026-10-11T00:00:00Z"),at("2026-10-13T00:00:00Z"));assertEquals(1,s.size());assertEquals("Updated",s.get(0).title);
    }
    @Test public void movedOccurrenceCanEnterRange(){
        String ics=calendar(event("UID:a\r\nDTSTART:20261001T120000Z\r\nRRULE:FREQ=DAILY;COUNT=2"),event("UID:a\r\nRECURRENCE-ID:20261002T120000Z\r\nDTSTART:20261015T120000Z\r\nSUMMARY:Moved"));var s=CalendarReader.read(ics,at("2026-10-10T00:00:00Z"),at("2026-10-20T00:00:00Z"));assertEquals(1,s.size());assertEquals("Moved",s.get(0).title);
    }
    @Test public void cancelledSeriesAlsoCancelsDetachedInstances(){
        String ics=calendar(event("UID:a\r\nDTSTART:20261012T120000Z\r\nRRULE:FREQ=DAILY;COUNT=3\r\nSTATUS:CANCELLED"),event("UID:a\r\nRECURRENCE-ID:20261013T120000Z\r\nDTSTART:20261013T150000Z"));assertTrue(CalendarReader.read(ics,at("2026-10-11T00:00:00Z"),at("2026-10-16T00:00:00Z")).isEmpty());
    }
    @Test public void utf8BomIsAccepted(){assertTrue(CalendarReader.read("\uFEFF"+calendar(),0,Long.MAX_VALUE).isEmpty());}
    @Test public void allDayEntriesStayVisibleWithoutInventingATime(){
        String ics=calendar(event("UID:day\r\nDTSTART;VALUE=DATE:20261013\r\nSUMMARY:Work"));
        var rows=CalendarReader.readWithAllDay(ics,at("2026-10-13T15:00:00Z"),at("2026-10-15T00:00:00Z"),-1);
        assertEquals(1,rows.size());assertTrue(rows.get(0).allDay);assertTrue(rows.get(0).id.endsWith("date:2026-10-13"));
    }
    @Test public void chosenAllDayStartUsesLocalTimeAcrossDST(){
        String ics=calendar(event("UID:day\r\nDTSTART;VALUE=DATE:20261030\r\nRRULE:FREQ=WEEKLY;COUNT=3"));
        var rows=CalendarReader.readWithAllDay(ics,at("2026-10-29T00:00:00Z"),at("2026-11-20T00:00:00Z"),480);
        assertEquals(3,rows.size());assertEquals(at("2026-10-30T12:00:00Z"),rows.get(0).start);assertEquals(at("2026-11-06T13:00:00Z"),rows.get(1).start);
    }
    @Test public void allDayExclusionsAndMovedInstancesWork(){
        String ics=calendar(event("UID:day\r\nDTSTART;VALUE=DATE:20261013\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEXDATE;VALUE=DATE:20261014"),event("UID:day\r\nRECURRENCE-ID;VALUE=DATE:20261015\r\nDTSTART;VALUE=DATE:20261016"));
        var rows=CalendarReader.readWithAllDay(ics,at("2026-10-12T00:00:00Z"),at("2026-10-20T00:00:00Z"),540);
        assertEquals(2,rows.size());assertEquals(at("2026-10-16T13:00:00Z"),rows.get(1).start);assertTrue(rows.get(1).id.endsWith("date:2026-10-15"));
    }
    @Test public void changingAllDayStartKeepsOccurrenceIdentity(){
        String ics=calendar(event("UID:day\r\nDTSTART;VALUE=DATE:20261013"));
        var early=CalendarReader.readWithAllDay(ics,0,at("2027-01-01T00:00:00Z"),480);var late=CalendarReader.readWithAllDay(ics,0,at("2027-01-01T00:00:00Z"),540);
        assertEquals(early.get(0).id,late.get(0).id);assertEquals(3600000,late.get(0).start-early.get(0).start);
    }
    @Test public void calendarCountsExplainEmptyAndAllDayFeeds(){
        String ics=calendar(event("UID:day\r\nDTSTART;VALUE=DATE:20261013"),event("UID:timed\r\nDTSTART:20261013T120000Z"),event("UID:cancelled\r\nSTATUS:CANCELLED"));
        var counts=CalendarReader.inspect(ics);assertEquals(3,counts.events);assertEquals(1,counts.allDay);assertEquals(1,counts.timed);assertEquals(1,counts.cancelled);
        assertEquals(0,CalendarReader.inspect(calendar()).events);
    }
    @Test public void availabilityOnlyFeedIsIdentified(){
        String ics=calendar("BEGIN:VFREEBUSY\r\nUID:busy\r\nDTSTART:20261013T080000Z\r\nDTEND:20261013T170000Z\r\nFREEBUSY:20261013T080000Z/20261013T170000Z\r\nEND:VFREEBUSY");
        var counts=CalendarReader.inspect(ics);assertEquals(0,counts.events);assertEquals(1,counts.availability);assertTrue(CalendarReader.readWithAllDay(ics,0,Long.MAX_VALUE,-1).isEmpty());
    }
    @Test public void overnightLeadUsesElapsedTime(){long start=at("2026-11-01T06:30:00Z");assertEquals(at("2026-11-01T05:00:00Z"),CalendarReader.wakeTime(start,90));assertEquals(start,CalendarReader.wakeTime(start,0));}
    @Test public void filteringIsCaseInsensitive(){assertTrue(CalendarReader.matches("WORK – morning","work"));assertFalse(CalendarReader.matches("Dentist","Work"));assertTrue(CalendarReader.matches("Busy",""));}
    @Test(expected=IllegalArgumentException.class) public void htmlIsRejected(){CalendarReader.read("<html>Sign in</html>",0,Long.MAX_VALUE);}
    @Test public void emptyCalendarIsValidAndClearsRemovedShifts(){assertTrue(CalendarReader.read(calendar(),0,Long.MAX_VALUE).isEmpty());}
    @Test(expected=IllegalArgumentException.class) public void invalidLeadRejected(){CalendarReader.wakeTime(0,-10);}
}
