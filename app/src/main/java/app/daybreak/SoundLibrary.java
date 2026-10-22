package app.daybreak;
import android.content.Context;
import android.media.MediaPlayer;

/** One source of truth for the bundled audio and upgrades from the previous MIDI library. */
public final class SoundLibrary {
    public static final String DEFAULT="gentle_01.m4a";
    public static boolean allowed(String file){return file!=null&&file.matches("((orchestra|gentle|nature)_[0-9]{2}\\.m4a|tone_[0-9]{2}\\.wav)");}
    public static void migrate(Context context){
        String old=Store.p(context).getString("sound",DEFAULT);
        if(!allowed(old))Store.p(context).edit().putString("sound",DEFAULT).commit();
    }
    public static String current(Context context){migrate(context);return Store.p(context).getString("sound",DEFAULT);}
    public static String playable(Context context){String wanted=current(context);return available(context,wanted)?wanted:DEFAULT;}
    public static boolean available(Context context,String file){if(!allowed(file))return false;if(SoundPacks.optional(file))return SoundPacks.file(context,file).isFile();try(var ignored=context.getAssets().openFd("sounds/"+file)){return true;}catch(Exception e){return false;}}
    public static void setDataSource(MediaPlayer player,Context context,String file)throws Exception{
        if(!available(context,file))throw new java.io.FileNotFoundException("Sound is not installed.");
        if(SoundPacks.optional(file))player.setDataSource(SoundPacks.file(context,file).getAbsolutePath());
        else try(var descriptor=context.getAssets().openFd("sounds/"+file)){player.setDataSource(descriptor.getFileDescriptor(),descriptor.getStartOffset(),descriptor.getLength());}
    }
    private SoundLibrary(){}
}
