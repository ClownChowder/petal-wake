package app.daybreak;
import android.content.Context;
import org.json.*;

/** Opt-in quiet playback; never changes the phone volume, sound, vibration or shift schedule. */
public final class PlaybackPresets {
 public static final int QUIET_VOLUME=70,QUIET_FADE=60;
 public static void quiet(Context c)throws JSONException{var p=Store.p(c);var e=p.edit();if(!p.getBoolean("quietPreset",false))e.putString("previousPlayback",new JSONObject().put("volume",p.getInt("volume",100)).put("fade",p.getInt("fade",0)).toString());e.putInt("volume",QUIET_VOLUME).putInt("fade",QUIET_FADE).putBoolean("quietPreset",true).commit();}
 public static void restore(Context c){JSONObject previous=Store.object(c,"previousPlayback");if(previous.length()==0)return;Store.p(c).edit().putInt("volume",Math.max(10,Math.min(100,previous.optInt("volume",100)))).putInt("fade",Math.max(0,Math.min(120,previous.optInt("fade",0)))).putBoolean("quietPreset",false).remove("previousPlayback").commit();}
 private PlaybackPresets(){}
}
