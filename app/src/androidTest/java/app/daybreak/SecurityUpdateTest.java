package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Verifies migration and encrypted-at-rest calendar storage on Android. */
public class SecurityUpdateTest extends Instrumentation {
    private int checks;
    private void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);checks++;Bundle event=new Bundle();event.putString("passed",label);sendStatus(0,event);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();Store.p(c).edit().clear().commit();
        String url="https://example.invalid/private-work-token";
        Store.p(c).edit().putString("url",url).commit();
        check(url.equals(Store.url(c))&&!Store.p(c).contains("url")&&PrivateStorage.encrypted(Store.p(c).getString("secureUrl","")),"Legacy calendar link migrates to encrypted storage");
        check(!Store.p(c).getString("secureUrl","").contains("private-work-token"),"Saved link ciphertext excludes the token");
        String calendar="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:secret-shift\r\nDTSTART:20261025T090000Z\r\nSUMMARY:Private shift\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        Store.cache(c,calendar);
        String saved=new String(Files.readAllBytes(new java.io.File(c.getFilesDir(),"calendar.ics").toPath()),StandardCharsets.UTF_8);
        check(calendar.equals(Store.cache(c))&&PrivateStorage.encrypted(saved)&&!saved.contains("Private shift"),"Primary calendar cache is encrypted and readable");
        Files.write(new java.io.File(c.getFilesDir(),"calendar.ics").toPath(),calendar.getBytes(StandardCharsets.UTF_8));
        check(calendar.equals(Store.cache(c))&&PrivateStorage.encrypted(new String(Files.readAllBytes(new java.io.File(c.getFilesDir(),"calendar.ics").toPath()),StandardCharsets.UTF_8)),"Legacy primary cache migrates without losing shifts");
        String id="11111111-1111-1111-1111-111111111111";CalendarSources.data(c,id,calendar);
        String extraSaved=new String(Files.readAllBytes(new java.io.File(c.getFilesDir(),"calendar-"+id+".ics").toPath()),StandardCharsets.UTF_8);
        check(calendar.equals(CalendarSources.data(c,id))&&PrivateStorage.encrypted(extraSaved)&&!extraSaved.contains("Private shift"),"Additional calendar cache is encrypted and readable");
        Files.write(new java.io.File(c.getFilesDir(),"calendar-"+id+".ics").toPath(),calendar.getBytes(StandardCharsets.UTF_8));
        check(calendar.equals(CalendarSources.data(c,id))&&PrivateStorage.encrypted(new String(Files.readAllBytes(new java.io.File(c.getFilesDir(),"calendar-"+id+".ics").toPath()),StandardCharsets.UTF_8)),"Legacy additional cache migrates without losing shifts");
        JSONArray extras=new JSONArray().put(new JSONObject().put("id",id).put("url",url).put("name","Private work"));Store.put(c,"extraCalendars",extras);
        check(url.equals(CalendarSources.link(c,id))&&PrivateStorage.encrypted(Store.p(c).getString("extraCalendars",""))&&!Store.p(c).getString("extraCalendars","").contains("private-work-token"),"Additional calendar links are encrypted");
        Store.p(c).edit().putString("extraCalendars",extras.toString()).commit();
        check(url.equals(CalendarSources.link(c,id))&&PrivateStorage.encrypted(Store.p(c).getString("extraCalendars","")),"Legacy additional link list migrates without changing calendars");
        String exported=SettingsTransfer.exportJson(c);check(!exported.contains(url)&&!exported.contains("Private shift"),"Export excludes links and event details");
        Store.p(c).edit().clear().commit();Store.cache(c,"");CalendarSources.data(c,id,"");
        check(Store.cache(c).isEmpty()&&CalendarSources.data(c,id).isEmpty(),"Cleared encrypted calendars remain readable");
        result.putString("summary",checks+" security storage checks passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
