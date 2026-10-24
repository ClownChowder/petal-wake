package app.daybreak;

/** Start clearly audible, then rise smoothly to the selected playback level. */
public final class AlarmEnvelope {
    public static final int DEFAULT_FADE_SECONDS=0;
    public static float volume(float target,int fadeSeconds,long elapsedMillis){
        float ceiling=Math.max(0f,Math.min(1f,target));
        if(fadeSeconds<=0)return ceiling;
        float progress=Math.max(0f,Math.min(1f,elapsedMillis/(fadeSeconds*1000f)));
        float level=.40f+.60f*progress;
        return ceiling*level*level;
    }
    private AlarmEnvelope(){}
}
