package app.daybreak;

import org.junit.Test;
import java.util.Calendar;
import static org.junit.Assert.assertEquals;

public class ShiftRulesTest {
    private long local(int hour,int minute){Calendar calendar=Calendar.getInstance();calendar.set(2026,Calendar.OCTOBER,24,hour,minute,0);calendar.set(Calendar.MILLISECOND,0);return calendar.getTimeInMillis();}
    @Test public void choosesEarlyMiddleAndLateLeadTimes(){
        assertEquals(90,ShiftRules.lead(local(9,59),false,60,true,600,90,720,45));
        assertEquals(60,ShiftRules.lead(local(10,0),false,60,true,600,90,720,45));
        assertEquals(45,ShiftRules.lead(local(12,0),false,60,true,600,90,720,45));
    }
    @Test public void allDayAndDisabledRulesKeepCalendarLead(){
        assertEquals(60,ShiftRules.lead(local(8,0),true,60,true,600,90,720,45));
        assertEquals(60,ShiftRules.lead(local(8,0),false,60,false,600,90,720,45));
    }
}
