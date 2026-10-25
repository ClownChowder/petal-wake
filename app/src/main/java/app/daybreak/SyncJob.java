package app.daybreak;
import android.app.job.*;
import android.content.*;
public class SyncJob extends JobService {
    private volatile int generation;
    public static void install(Context c){
        JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(js.getPendingJob(100)==null)js.schedule(new JobInfo.Builder(100,new ComponentName(c,SyncJob.class)).setPeriodic(15*60_000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters params){
        int run=++generation;
        return CalendarRefresh.request(this,()->run==generation,()->{
            if(run==generation){((DaybreakApp)getApplication()).changed();jobFinished(params,false);}
        });
    }
    @Override public boolean onStopJob(JobParameters params){++generation;return true;}
}
