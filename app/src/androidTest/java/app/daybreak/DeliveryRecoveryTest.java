package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.UUID;

/** Exercises watchdog acknowledgement and a single bounded retry on a disposable emulator. */
public final class DeliveryRecoveryTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();String first="manual:qa-"+UUID.randomUUID(),second="manual:qa-"+UUID.randomUUID();String retry=null;
        try{
            if(!Scheduler.allowed(c))throw new Exception("Exact alarm permission is unavailable on the emulator.");
            insert(c,first);JSONObject claimed=Scheduler.claim(c,first);
            if(claimed==null||!Store.object(c,"inFlight").has(first))throw new Exception("Delivery watchdog was not armed.");
            Scheduler.acknowledge(c,first);
            if(Store.object(c,"inFlight").has(first))throw new Exception("Playback acknowledgement did not clear the watchdog.");
            insert(c,second);claimed=Scheduler.claim(c,second);
            if(claimed==null)throw new Exception("Second delivery was not claimed.");
            Scheduler.retryFailedDelivery(c,second);
            if(Store.object(c,"inFlight").has(second))throw new Exception("Failed delivery was left in flight.");
            JSONObject manual=Store.object(c,"manual");
            for(Iterator<String> ids=manual.keys();ids.hasNext();){String id=ids.next();JSONObject row=manual.optJSONObject(id);if(row!=null&&row.optInt("retryCount")==1&&row.optString("title").equals("Delivery recovery test")){retry=id;break;}}
            if(retry==null||!Store.object(c,"scheduled").has(retry))throw new Exception("Recovery alarm was not scheduled.");
            Scheduler.retryFailedDelivery(c,second);
            result.putString("summary","Watchdog acknowledged playback; failed start scheduled exactly one recovery alarm");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{for(String id:new String[]{first,second,retry})if(id!=null){Scheduler.cancel(c,id);for(String key:new String[]{"manual","scheduled","inFlight"}){JSONObject rows=Store.object(c,key);rows.remove(id);Store.put(c,key,rows);}}}
    }
    private void insert(Context c,String id)throws Exception{
        JSONObject row=new JSONObject().put("id",id).put("title","Delivery recovery test").put("kind","manual").put("at",System.currentTimeMillis()-1000);
        for(String key:new String[]{"manual","scheduled"}){JSONObject rows=Store.object(c,key);rows.put(id,row);Store.put(c,key,rows);}
    }
}
