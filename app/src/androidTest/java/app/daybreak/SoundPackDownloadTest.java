package app.daybreak;

import android.app.Instrumentation;
import android.content.Context;
import android.media.*;
import android.os.Bundle;

/** Run on a disposable emulator only, after publishing the matching GitHub release. */
public class SoundPackDownloadTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();SoundPacks.remove(c);SoundPacks.install(c);
        if(!SoundPacks.installed(c)||!SoundLibrary.available(c,"orchestra_29.m4a"))throw new Exception("Downloaded orchestra pack was not installed.");
        MediaPlayer player=new MediaPlayer();try{player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());SoundLibrary.setDataSource(player,c,"orchestra_29.m4a");player.prepare();player.start();Thread.sleep(400);if(!player.isPlaying())throw new Exception("Optional orchestra recording did not play.");}finally{player.release();}
        SoundPacks.remove(c);if(SoundPacks.installed(c)||SoundLibrary.available(c,"orchestra_29.m4a"))throw new Exception("Optional orchestra pack could not be removed.");
        result.putString("summary","Live GitHub music-pack download, verified installation, playback, and removal passed");finish(android.app.Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",error.toString());finish(android.app.Activity.RESULT_CANCELED,result);}}
}
