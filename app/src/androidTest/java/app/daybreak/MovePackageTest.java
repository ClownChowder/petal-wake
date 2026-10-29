package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** Synthetic phone-move round trip; run only on the dedicated project emulator. */
public class MovePackageTest extends Instrumentation {
    private static final String CALENDAR="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:move-test-only\r\nDTSTART:20261110T130000Z\r\nDTEND:20261110T210000Z\r\nSUMMARY:Synthetic work\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
    private boolean largePack;
    @Override public void onCreate(Bundle args){largePack=args!=null&&"true".equals(args.getString("largePack"));super.onCreate(args);start();}
    @Override public void onStart(){
        waitForIdleSync();
        Bundle result=new Bundle();Context c=getTargetContext();String url="https://example.invalid/private/synthetic-move-token";
        File packageFile=new File(c.getCacheDir(),"synthetic-rouse-transfer.rouse"),damagedFile=new File(c.getCacheDir(),"synthetic-rouse-damaged.rouse");
        try{
            clear(c);
            Store.setUrl(c,url);Store.cache(c,CALENDAR);
            Store.p(c).edit().putString("source","Shared calendar link").putString("primaryName","Synthetic work").putInt("lead",75).putBoolean("enabled",true).commit();
            String alarmId="manual:"+UUID.randomUUID(),soundId="custom:"+UUID.randomUUID(),linkedId="custom:"+UUID.randomUUID(),extraId=UUID.randomUUID().toString();
            String extraUrl="https://example.invalid/private/synthetic-second-calendar";
            Store.put(c,"extraCalendars",new JSONArray().put(new JSONObject().put("id",extraId).put("name","Second synthetic calendar").put("url",extraUrl).put("lead",45).put("filter","").put("allDayEnabled",false).put("allDayMinutes",480)));
            CalendarSources.data(c,extraId,CALENDAR.replace("move-test-only","move-test-extra"));
            long alarmAt=System.currentTimeMillis()+7*86_400_000L;
            Store.put(c,"manual",new JSONObject().put(alarmId,new JSONObject().put("id",alarmId).put("title","Synthetic appointment").put("kind","manual").put("at",alarmAt)));
            Store.put(c,"alarmHistory",new JSONArray().put(new JSONObject().put("event","rung").put("kind","manual").put("when",System.currentTimeMillis()).put("at",alarmAt).put("to",0)));
            File audioDir=new File(c.getFilesDir(),"custom-sounds");if(!audioDir.mkdirs())throw new IOException("Could not prepare test audio.");
            File audio=new File(audioDir,soundId.substring(7)+".audio");
            try(InputStream in=c.getAssets().open("sounds/gentle_01.m4a");FileOutputStream out=new FileOutputStream(audio)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
            Store.put(c,"customSounds",new JSONArray()
                .put(new JSONObject().put("id",soundId).put("title","Synthetic sound").put("genre","Ambient").put("mode","copy").put("duration",60))
                .put(new JSONObject().put("id",linkedId).put("title","Linked synthetic sound").put("genre","Nature").put("mode","link").put("uri",SlowAudioProvider.URI.toString()).put("duration",60)));
            for(int i=5;i<=32;i++){String name=String.format(java.util.Locale.ROOT,"orchestra_%02d.m4a",i);File target=SoundPacks.file(c,name);if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Could not prepare synthetic orchestra pack.");
                try(InputStream in=c.getAssets().open(largePack?"sounds/gentle_01.m4a":"sounds/tone_12.wav");FileOutputStream out=new FileOutputStream(target)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}}
            Store.p(c).edit().putString("sound",soundId).putBoolean("quietPreset",true)
                .putInt("volume",42).putInt("fade",60)
                .putString("previousPlayback",new JSONObject().put("volume",100).put("fade",0).toString()).commit();
            char[] password="synthetic-move-password-24".toCharArray();
            try(FileOutputStream saved=new FileOutputStream(packageFile)){MovePackage.exportTo(c,saved,password);}
            if(contains(packageFile,url)||contains(packageFile,extraUrl))throw new AssertionError("Calendar link leaked into encrypted package.");
            clear(c);
            boolean wrongFailed=false;
            try(FileInputStream input=new FileInputStream(packageFile)){MovePackage.importFrom(c,input,"wrong-password-123".toCharArray());}catch(Exception expected){wrongFailed=true;}
            if(!wrongFailed||!Store.url(c).isEmpty()||Store.object(c,"manual").length()!=0)throw new AssertionError("Wrong password changed app data.");
            java.nio.file.Files.copy(packageFile.toPath(),damagedFile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try(RandomAccessFile changed=new RandomAccessFile(damagedFile,"rw")){changed.seek(changed.length()-1);int last=changed.read();changed.seek(changed.length()-1);changed.write(last^1);}
            boolean damagedFailed=false;
            try(FileInputStream input=new FileInputStream(damagedFile)){MovePackage.importFrom(c,input,password);}catch(Exception expected){damagedFailed=true;}
            if(!damagedFailed||!Store.url(c).isEmpty())throw new AssertionError("Damaged package changed app data.");
            try(FileInputStream input=new FileInputStream(packageFile)){MovePackage.importFrom(c,input,password);}
            if(!url.equals(Store.url(c)))throw new AssertionError("Calendar link was not restored.");
            if(!Store.cache(c).contains("move-test-only"))throw new AssertionError("Calendar snapshot was not restored.");
            if(!extraUrl.equals(CalendarSources.link(c,extraId))||!CalendarSources.data(c,extraId).contains("move-test-extra"))throw new AssertionError("Additional calendar was not restored.");
            if(Store.p(c).getInt("lead",0)!=75)throw new AssertionError("Lead time was not restored.");
            if(!Store.p(c).getBoolean("quietPreset",false)||Store.object(c,"previousPlayback").optInt("volume")!=100)
                throw new AssertionError("Quiet preset return settings were not restored.");
            if(Store.p(c).getBoolean("enabled",true))throw new AssertionError("Work alarms were not paused.");
            if(!Store.object(c,"manual").has(alarmId))throw new AssertionError("Personal alarm was not restored.");
            if(!CustomSounds.available(c,soundId))throw new AssertionError("Personal audio was not restored.");
            if(!CustomSounds.available(c,linkedId)||CustomSounds.linked(c,linkedId))throw new AssertionError("Linked audio was not restored as an offline copy.");
            if(!SoundPacks.installed(c))throw new AssertionError("Optional orchestra pack was not restored.");
            if(!"Synthetic sound".equals(CustomSounds.forUi(c).getJSONObject(0).getString("title")))throw new AssertionError("Sound metadata was not restored.");
            boolean restoredHistory=false;JSONArray history=AlarmHistory.entries(c);
            for(int i=0;i<history.length();i++)if("rung".equals(history.getJSONObject(i).optString("event")))restoredHistory=true;
            if(!restoredHistory)throw new AssertionError("Alarm history was not restored.");
            boolean existingRejected=false;try(FileInputStream input=new FileInputStream(packageFile)){MovePackage.importFrom(c,input,password);}catch(Exception expected){existingRejected=true;}
            if(!existingRejected||!url.equals(Store.url(c)))throw new AssertionError("Restore overwrote existing app data.");
            File[] leftovers=c.getCacheDir().listFiles();if(leftovers!=null)for(File file:leftovers)if(file.getName().startsWith("rouse-move-"))throw new AssertionError("Private restore staging was left behind.");
            Arrays.fill(password,'\0');result.putString("summary","encrypted synthetic multi-calendar, personal alarm, history, copied and linked audio, and "+(largePack?"full-size":"compact")+" orchestra pack round trip; wrong password and damaged package rejected; existing data protected");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{packageFile.delete();damagedFile.delete();}
    }
    private static void clear(Context c)throws Exception{
        Store.p(c).edit().clear().commit();
        File[] files=c.getFilesDir().listFiles();if(files!=null)for(File file:files)if(file.getName().equals("calendar.ics")||file.getName().startsWith("calendar-")||file.getName().equals("custom-sounds")||file.getName().equals("orchestra-pack"))delete(file);
        MovePackage.clearAbandoned(c);
    }
    private static void delete(File file){if(file.isDirectory()){File[] rows=file.listFiles();if(rows!=null)for(File child:rows)delete(child);}file.delete();}
    private static boolean contains(File file,String text)throws IOException{
        byte[] pattern=text.getBytes(StandardCharsets.UTF_8),buffer=new byte[8192+pattern.length];int carry=0;
        try(FileInputStream input=new FileInputStream(file)){int n;while((n=input.read(buffer,carry,8192))!=-1){int size=carry+n;
            for(int i=0;i<=size-pattern.length;i++){int j=0;while(j<pattern.length&&buffer[i+j]==pattern[j])j++;if(j==pattern.length)return true;}
            carry=Math.min(pattern.length-1,size);System.arraycopy(buffer,size-carry,buffer,0,carry);
        }}return false;
    }
}
