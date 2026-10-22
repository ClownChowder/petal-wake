package app.daybreak;
import android.app.job.*;
import android.content.*;
public class SyncJob extends JobService {
    public static void install(Context c){
        JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(js.getPendingJob(100)==null)js.schedule(new JobInfo.Builder(100,new ComponentName(c,SyncJob.class)).setPeriodic(15*60_000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters params){DaybreakApp.IO.execute(()->{Sync.run(this,true);((DaybreakApp)getApplication()).changed();jobFinished(params,false);});return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}
