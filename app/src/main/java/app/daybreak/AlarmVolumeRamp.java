package app.daybreak;

/** Maps a gradual rise to the phone's discrete alarm-volume steps. */
public final class AlarmVolumeRamp {
    public static final int DEFAULT_START_PERCENT=30;
    public static int step(int target,int minimum,int maximum,int startPercent,int durationSeconds,long elapsedMillis){
        if(target<=minimum)return target;
        int safeStart=Math.max(10,Math.min(100,startPercent));
        int first=Math.min(target,Math.max(minimum,Math.round(maximum*safeStart/100f)));
        if(durationSeconds<=0)return target;
        float progress=Math.max(0f,Math.min(1f,elapsedMillis/(durationSeconds*1000f)));
        return Math.min(target,Math.max(first,Math.round(first+(target-first)*progress)));
    }
    private AlarmVolumeRamp(){}
}
