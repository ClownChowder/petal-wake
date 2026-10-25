package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;

/** Disposable-emulator check for isolated test scheduling and forged/early IDs. */
public final class TestAlarmSchedulingTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();JSONObject previousManual=Store.object(c,"manual"),previousTests=Store.object(c,"testAlarms");
        String manualId="manual:qa-isolation",testId="test:qa-isolation";
        try{
            long now=System.currentTimeMillis();
            JSONObject manual=new JSONObject(previousManual.toString()).put(manualId,new JSONObject().put("id",manualId).put("kind","manual").put("title","Synthetic personal alarm").put("at",now+3_600_000));
            JSONObject tests=new JSONObject(previousTests.toString()).put(testId,new JSONObject().put("id",testId).put("kind","test").put("title","Synthetic sound check").put("at",now+60_000));
            Store.put(c,"manual",manual);Store.put(c,"testAlarms",tests);Sync.run(c,false);
            JSONObject scheduled=Store.object(c,"scheduled");
            if(!scheduled.has(manualId)||!scheduled.has(testId))throw new Exception("Personal and isolated test alarms were not both scheduled");
            if(Scheduler.claim(c,testId)!=null||Scheduler.claim(c,"forged")!=null)throw new Exception("Early or forged alarm ID was accepted");
            tests.remove(testId);Store.put(c,"testAlarms",tests);Scheduler.cancel(c,testId);Sync.run(c,false);
            scheduled=Store.object(c,"scheduled");
            if(scheduled.has(testId)||!scheduled.has(manualId)||Scheduler.claim(c,testId)!=null)throw new Exception("Cancelling test affected a personal alarm or allowed replay");
            result.putString("summary","Test schedules separately, preserves personal alarm, and rejects early/forged/replayed IDs");finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{Store.put(c,"manual",previousManual);Store.put(c,"testAlarms",previousTests);Scheduler.cancel(c,manualId);Scheduler.cancel(c,testId);Sync.run(c,false);}
    }
}
