package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/** A corrupt personal recording must start the bundled alarm tone instead of silence. */
public final class AudioFallbackTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();String id="custom:"+UUID.randomUUID();File directory=new File(c.getFilesDir(),"custom-sounds"),sound=new File(directory,id.substring(7)+".audio");
        String previous=Store.p(c).getString("sound",SoundLibrary.DEFAULT);int oldFallback=Store.p(c).getInt("fallbackMinutes",0);boolean oldVibration=Store.p(c).getBoolean("vibrate",true);
        try{
            if(!directory.isDirectory()&&!directory.mkdirs())throw new Exception("Could not create test sound folder.");
            try(FileOutputStream out=new FileOutputStream(sound)){out.write(new byte[]{1,2,3,4,5,6,7,8});}
            JSONArray list=Store.array(c,"customSounds");list.put(new JSONObject().put("id",id).put("title","Broken test audio").put("genre","Other").put("mode","copy"));Store.put(c,"customSounds",list);
            Store.p(c).edit().putString("sound",id).putInt("fallbackMinutes",0).putBoolean("vibrate",false).commit();AlarmHistory.clear(c);
            JSONObject alarm=new JSONObject().put("id","qa-audio-fallback").put("title","Audio fallback test").put("kind","manual").put("at",System.currentTimeMillis());
            c.startForegroundService(new Intent(c,AlarmService.class).setAction("RING").putExtra("alarm",alarm.toString()));
            long until=System.currentTimeMillis()+10_000L;boolean switched=false;android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);int selectedStep=audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
            while(System.currentTimeMillis()<until){JSONArray history=AlarmHistory.entries(c);for(int i=0;i<history.length();i++)if("audio fallback".equals(history.optJSONObject(i).optString("event")))switched=true;if(switched&&AlarmService.playbackPercent()>=0)break;Thread.sleep(100);}
            if(!switched||audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)!=selectedStep)throw new Exception("Bundled tone did not take over at the selected phone alarm level.");
            result.putString("summary","Corrupt personal audio fell back to bundled tone at the selected phone alarm volume");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{
            c.startService(new Intent(c,AlarmService.class).setAction("STOP"));
            Store.p(c).edit().putString("sound",previous).putInt("fallbackMinutes",oldFallback).putBoolean("vibrate",oldVibration).commit();
            JSONArray old=Store.array(c,"customSounds"),next=new JSONArray();for(int i=0;i<old.length();i++){JSONObject row=old.optJSONObject(i);if(row!=null&&!id.equals(row.optString("id")))next.put(row);}Store.put(c,"customSounds",next);sound.delete();
        }
    }
}
