package app.daybreak;
import org.junit.Test;
import static org.junit.Assert.*;
public class CalendarHealthPolicyTest {
 private static final long NOW=100_000_000L;
 @Test public void downloadsAreFreshUntilEightHours(){assertEquals("fresh",CalendarHealthPolicy.status(true,false,NOW,0,NOW));assertEquals("fresh",CalendarHealthPolicy.status(true,false,NOW-CalendarHealthPolicy.STALE_MS+1,0,NOW));assertEquals("stale",CalendarHealthPolicy.status(true,false,NOW-CalendarHealthPolicy.STALE_MS,0,NOW));}
 @Test public void failuresStayVisibleUntilARealSuccess(){assertEquals("failed",CalendarHealthPolicy.status(true,false,NOW,1,NOW));assertEquals("fresh",CalendarHealthPolicy.status(true,false,NOW,0,NOW));assertFalse(CalendarHealthPolicy.alertDue(true,NOW,1,NOW-3_600_000,NOW));}
 @Test public void repeatsNeedGraceAndStaleNeedsOnlyOneNotice(){assertFalse(CalendarHealthPolicy.alertDue(true,NOW,2,NOW-CalendarHealthPolicy.FAILURE_GRACE_MS+1,NOW));assertTrue(CalendarHealthPolicy.alertDue(true,NOW,2,NOW-CalendarHealthPolicy.FAILURE_GRACE_MS,NOW));assertTrue(CalendarHealthPolicy.alertDue(true,NOW-CalendarHealthPolicy.STALE_MS,0,0,NOW));}
 @Test public void importsDisconnectedAndClockRollbackAreNotStale(){assertEquals("imported",CalendarHealthPolicy.status(false,true,1,5,NOW));assertEquals("unlinked",CalendarHealthPolicy.status(false,false,1,5,NOW));assertFalse(CalendarHealthPolicy.alertDue(false,1,5,1,NOW));assertEquals("fresh",CalendarHealthPolicy.status(true,false,NOW+3_600_000,0,NOW));assertFalse(CalendarHealthPolicy.alertDue(true,NOW+3_600_000,2,NOW+60_000,NOW));assertEquals("never",CalendarHealthPolicy.status(true,false,0,0,NOW));}
}
