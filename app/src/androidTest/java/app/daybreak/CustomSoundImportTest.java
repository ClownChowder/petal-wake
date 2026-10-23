package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies a real audio file through a content provider and verifies private offline playback. */
public final class CustomSoundImportTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Uri uri=null;String id=null;
        try{
            ContentValues values=new ContentValues();values.put(MediaStore.Audio.Media.DISPLAY_NAME,"Petal Wake import test.m4a");values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp4");values.put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/Petal Wake QA");
            uri=c.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new Exception("Could not create provider test audio.");
            try(InputStream in=c.getAssets().open("sounds/gentle_09.m4a");OutputStream out=c.getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("Could not write provider audio.");byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
            CustomSounds.add(c,uri,false,"Piano");JSONArray list=CustomSounds.forUi(c);
            JSONObject row=list.getJSONObject(list.length()-1);id=row.getString("file");if(!row.optString("title").startsWith("Petal Wake import test")||!"Piano".equals(row.optString("genre"))||!row.optBoolean("available"))throw new Exception("Imported sound metadata was incorrect: "+row);
            if(id==null||!SoundLibrary.available(c,id))throw new Exception("Imported file was not available offline.");
            android.media.MediaPlayer player=new android.media.MediaPlayer();try{SoundLibrary.setDataSource(player,c,id);player.prepare();if(player.getDuration()<1000)throw new Exception("Imported audio was not playable.");}finally{player.release();}
            CustomSounds.changeGenre(c,id,"Ambient");if(!"Ambient".equals(CustomSounds.forUi(c).getJSONObject(list.length()-1).optString("genre")))throw new Exception("Genre edit was not saved.");
            CustomSounds.remove(c,id);if(SoundLibrary.available(c,id))throw new Exception("Removed sound still appears available.");id=null;
            result.putString("summary","Provider audio imported, playable offline, reclassified, and removed");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{if(id!=null)try{CustomSounds.remove(c,id);}catch(Exception ignored){}if(uri!=null)c.getContentResolver().delete(uri,null,null);}
    }
}
