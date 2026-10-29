package app.daybreak;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
/** Synthetic destructive recovery tests; disposable emulator only. */
public final class MoveRecoveryInstrumentation extends Instrumentation {
 private String mode;
 public void onCreate(Bundle args){mode=args==null?"normal":args.getString("mode","normal");super.onCreate(args);start();}
 public void onStart(){Bundle out=new Bundle();try{
  waitForIdleSync();
  Context c=getTargetContext();if(!"ranchu".equals(Build.HARDWARE))throw new AssertionError("Disposable emulator only");
  File pack=new File(c.getCacheDir(),"repair-synthetic-package.rouse");char[] password="synthetic-repair-password".toCharArray();
  if("verifyCrash".equals(mode)){
   preserved(c);
   try(InputStream in=new FileInputStream(pack)){MovePackage.importFrom(c,in,password);}
   if(Store.object(c,"manual").length()!=1)throw new AssertionError("Retry after process death failed");
   out.putString("result","PASS: startup rollback after process death, preserved baseline, successful retry");
  }else{
   clear(c);
   String calendar="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:repair-synthetic\r\nDTSTART:20261110T130000Z\r\nDTEND:20261110T210000Z\r\nSUMMARY:Synthetic work\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
   Store.cache(c,calendar);Store.p(c).edit().putString("source","Imported calendar file").putString("primaryName","Synthetic calendar").commit();
   String id="manual:"+UUID.randomUUID();Store.put(c,"manual",new JSONObject().put(id,new JSONObject().put("id",id).put("kind","manual").put("title","Synthetic alarm").put("at",System.currentTimeMillis()+86400000L)));
   try(OutputStream target=new FileOutputStream(pack)){MovePackage.exportTo(c,target,password);}
   String[] keys="crash".equals(mode)?new String[]{"source"}:new String[]{"extraCalendars","manual","source","customThemes","persistent"};
   for(String key:keys){
    clear(c);Store.p(c).edit().putInt("lead",77).putStringSet("repairSet",new HashSet<>(Arrays.asList("one","two"))).commit();Store.cache(c,"original synthetic cache");
    boolean[] injected={false};Context failing=new ContextWrapper(c){@Override public SharedPreferences getSharedPreferences(String n,int m){
     SharedPreferences prefs=super.getSharedPreferences(n,m);
     return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
      if(!method.getName().equals("edit"))return method.invoke(prefs,args);
      SharedPreferences.Editor editor=prefs.edit();boolean[] selected={false};
      return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(ep,em,ea)->{
       if(em.getName().startsWith("put")&&("persistent".equals(key)?"source":key).equals(ea[0]))selected[0]=true;
       if(em.getName().equals("commit")&&((selected[0]&&!injected[0])||("persistent".equals(key)&&injected[0]))){
        injected[0]=true;
        if("crash".equals(mode)){em.invoke(editor,ea);android.os.Process.killProcess(android.os.Process.myPid());Thread.sleep(10000);throw new AssertionError("Process survived");}
        return false;
       }
       Object value=em.invoke(editor,ea);return value instanceof SharedPreferences.Editor?ep:value;
      });
     });
    }};
    boolean failed=false;try(InputStream in=new FileInputStream(pack)){MovePackage.importFrom(failing,in,password);}catch(Exception expected){failed=true;}
    if(!failed||!injected[0])throw new AssertionError("Failure did not reach "+key);
    if ("persistent".equals(key)) {
     if (!MoveRestoreJournal.blocked || !new File(c.getNoBackupFilesDir(),"move-rollback/pending").exists()) throw new AssertionError("Recovery record lost while storage unavailable");
     MoveRestoreJournal.recover(c);
    }
    preserved(c);
    try(InputStream in=new FileInputStream(pack)){MovePackage.importFrom(c,in,password);}
    if(Store.object(c,"manual").length()!=1)throw new AssertionError("Retry failed after "+key);
   }
   out.putString("result","PASS: four commit failures and persistent rollback failure recovered; all five retries succeeded");
  }
  Arrays.fill(password,'\0');finish(Activity.RESULT_OK,out);
 }catch(Throwable e){java.io.StringWriter trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));out.putString("error",trace.toString());finish(Activity.RESULT_CANCELED,out);}}
 private static void preserved(Context c)throws Exception{
  if(MoveRestoreJournal.blocked||Store.object(c,"manual").length()!=0||!Store.p(c).getString("source","").isEmpty()||Store.p(c).getInt("lead",0)!=77||!Store.cache(c).equals("original synthetic cache")||Store.p(c).getStringSet("repairSet",Collections.emptySet()).size()!=2||new File(c.getNoBackupFilesDir(),"move-rollback").exists())throw new AssertionError("Original destination was not restored");
 }
 private static void clear(Context c)throws Exception{
  synchronized(Scheduler.class){
   Store.p(c).edit().clear().commit();
   File[] files=c.getFilesDir().listFiles();if(files!=null)for(File f:files)if(f.getName().startsWith("calendar")||f.getName().equals("custom-sounds")||f.getName().equals("orchestra-pack"))delete(f);
   MoveRestoreJournal.recover(c);MovePackage.clearAbandoned(c);
  }
 }
 private static void delete(File f){if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)delete(c);}f.delete();}
}
