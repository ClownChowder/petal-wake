package app.daybreak;

import java.util.Calendar;

/** Optional time-of-day overrides; all-day shifts use their calendar's ordinary lead time. */
public final class ShiftRules {
    public static int lead(long start,boolean allDay,int ordinary,boolean enabled,int earlyBefore,int earlyLead,int lateAfter,int lateLead){
        if(!enabled||allDay)return ordinary;
        Calendar local=Calendar.getInstance();local.setTimeInMillis(start);
        int minutes=local.get(Calendar.HOUR_OF_DAY)*60+local.get(Calendar.MINUTE);
        if(minutes<earlyBefore)return earlyLead;
        if(minutes>=lateAfter)return lateLead;
        return ordinary;
    }
    private ShiftRules(){}
}
