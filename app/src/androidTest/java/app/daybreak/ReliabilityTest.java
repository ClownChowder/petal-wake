package app.daybreak;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/** Isolated emulator checks for the evening preview and optional-sound fallback. */
public class ReliabilityTest extends Instrumentation {
    private Context context;private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean condition,String message)throws Exception{if(!condition)throw new Exception(message);checks++;Bundle result=new Bundle();result.putString("passed",message);sendStatus(0,result);}
    private void io(Runnable work)throws Exception{DaybreakApp.IO.submit(work).get(15,TimeUnit.SECONDS);}
    private boolean notice(int id){for(android.service.notification.StatusBarNotification item:context.getSystemService(NotificationManager.class).getActiveNotifications())if(item.getId()==id)return true;return false;}
    private boolean awaitNotice(int id,boolean expected)throws InterruptedException{for(int i=0;i<20;i++){if(notice(id)==expected)return true;Thread.sleep(100);}return notice(id)==expected;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();
        io(()->{try{for(var ids=Store.object(context,"scheduled").keys();ids.hasNext();)Scheduler.cancel(context,ids.next());Store.p(context).edit().clear().commit();Store.cache(context,"");}catch(Exception e){throw new RuntimeException(e);}});
        String start=ZonedDateTime.now().plusDays(1).withHour(15).withMinute(0).withSecond(0).withNano(0).withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String ics="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:evening-test\r\nDTSTART:"+start+"\r\nDURATION:PT8H\r\nSUMMARY:Tomorrow's shift\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        io(()->{try{Sync.importFile(context,ics);Store.p(context).edit().putBoolean("enabled",true).commit();Sync.run(context,false);}catch(Exception e){throw new RuntimeException(e);}});
        check(Store.object(context,"scheduled").length()==1,"Tomorrow's shift has an exact work alarm");
        java.util.Calendar now=java.util.Calendar.getInstance();int minute=now.get(java.util.Calendar.HOUR_OF_DAY)*60+now.get(java.util.Calendar.MINUTE);
        Store.p(context).edit().putBoolean("eveningCheck",true).putInt("eveningMinutes",Math.max(0,minute-1)).commit();
        EveningCheck.update(context);check(awaitNotice(202,true),"Evening check posts tomorrow's alarm preview");
        int sent=Store.p(context).getInt("eveningSentDay",-1);EveningCheck.update(context);check(sent==Store.p(context).getInt("eveningSentDay",-1),"Evening check is sent only once per day");
        Store.p(context).edit().putBoolean("eveningCheck",false).commit();EveningCheck.update(context);check(awaitNotice(202,false),"Disabling the evening check removes its notice");
        Store.p(context).edit().putString("sound","orchestra_29.m4a").commit();check(SoundLibrary.DEFAULT.equals(SoundLibrary.playable(context)),"A missing optional sound falls back to bundled gentle audio");
        check(SoundLibrary.current(context).equals("orchestra_29.m4a"),"Optional sound choice is remembered for later pack download");
        result.putString("summary",checks+" reliability checks passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
