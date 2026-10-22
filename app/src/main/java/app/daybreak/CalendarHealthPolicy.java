package app.daybreak;

/** Time-based sync health; a download confirms receipt, never the publisher's freshness. */
public final class CalendarHealthPolicy {
 public static final long STALE_MS=8*60*60_000L,FAILURE_GRACE_MS=15*60_000L;
 public static String status(boolean linked,boolean imported,long success,int failures,long now){
  if(!linked)return imported?"imported":"unlinked";
  if(failures>0)return "failed";
  if(success<=0)return "never";
  return now-success>=STALE_MS?"stale":"fresh";
 }
 public static boolean alertDue(boolean linked,long success,int failures,long firstFailure,long now){
  if(!linked)return false;
  return success>0&&now-success>=STALE_MS||failures>=2&&firstFailure>0&&now-firstFailure>=FAILURE_GRACE_MS;
 }
 private CalendarHealthPolicy(){}
}
