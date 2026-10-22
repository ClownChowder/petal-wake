package app.daybreak;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** A short, local delivery log. Never stores calendar URLs or event titles. */
public final class AlarmHistory {
    private static final String KEY="alarmHistory";
    private static final int LIMIT=100;
    private static final long MAX_AGE=30L*86_400_000L;

    public static synchronized JSONArray entries(Context c){return Store.array(c,KEY);}

    public static synchronized void record(Context c,String event,JSONObject alarm){
        long at=alarm==null?0:alarm.optLong("at");
        String kind=alarm==null?"system":alarm.optString("kind","alarm");
        add(c,event,kind,at,0);
    }

    public static synchronized void change(Context c,String event,long before,long after){
        add(c,event,"shift",before,after);
    }

    private static void add(Context c,String event,String kind,long at,long changedTo){
        long now=System.currentTimeMillis();
        try {
            JSONArray old=Store.array(c,KEY),next=new JSONArray();
            next.put(new JSONObject().put("event",event).put("kind",kind).put("when",now).put("at",at).put("to",changedTo));
            for(int i=0;i<old.length()&&next.length()<LIMIT;i++){
                JSONObject row=old.optJSONObject(i);
                if(row!=null&&now-row.optLong("when")<=MAX_AGE)next.put(row);
            }
            Store.put(c,KEY,next);
        }catch(Exception ignored){}
    }

    public static synchronized void clear(Context c){Store.put(c,KEY,new JSONArray());}
    private AlarmHistory(){}
}
