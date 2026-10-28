package app.daybreak;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Portable preferences only. Calendar URLs, events, alarms, and history never leave the app. */
public final class SettingsTransfer {
    private static final String[] INTS={"lead","snooze","volume","fade","riseStartPercent","allDayMinutes","earlyBefore","earlyLead","lateAfter","lateLead","eveningMinutes","fallbackMinutes","maxRingMinutes"};
    private static final String[] BOOLEANS={"vibrate","allDayEnabled","shiftRulesEnabled","eveningCheck","syncAlerts","quietPreset","privateLockScreen"};
    private static final String[] STRINGS={"appearance","iconDesign","sound","filter"};

    public static String exportJson(Context c)throws Exception{
        SharedPreferences prefs=Store.p(c);JSONObject settings=new JSONObject();
        for(String key:INTS)if(prefs.contains(key))settings.put(key,prefs.getInt(key,0));
        for(String key:BOOLEANS)if(prefs.contains(key))settings.put(key,prefs.getBoolean(key,false));
        for(String key:STRINGS)if(prefs.contains(key))settings.put(key,prefs.getString(key,""));
        JSONArray themes=new JSONArray(),saved=Appearance.customThemes(c);
        for(int i=0;i<saved.length();i++){
            JSONObject source=saved.getJSONObject(i);
            themes.put(new JSONObject().put("id",source.getString("id")).put("name",source.getString("name"))
                .put("background",source.getString("background")).put("primary",source.getString("primary"))
                .put("secondary",source.getString("secondary")).put("preferredText",source.getString("preferredText")));
        }
        return new JSONObject().put("app","Rouse").put("schema",1).put("settings",settings).put("customThemes",themes).toString(2);
    }

    public static void validateJson(Context c,String text)throws Exception{validate(c,text);}

    private static final class Validated {
        final JSONObject settings;final JSONArray themes;final boolean hasThemes;
        Validated(JSONObject settings,JSONArray themes,boolean hasThemes){this.settings=settings;this.themes=themes;this.hasThemes=hasThemes;}
    }
    private static Validated validate(Context c,String text)throws Exception{
        if(text==null||text.length()>262_144)throw new IllegalArgumentException("Settings file is too large.");
        JSONObject root=new JSONObject(text);
        String app=root.optString("app");
        if(!("Rouse".equals(app)||"Petal Wake".equals(app))||root.optInt("schema")!=1)throw new IllegalArgumentException("Choose a Rouse settings file.");
        JSONObject settings=root.getJSONObject("settings");
        JSONArray rawThemes=root.optJSONArray("customThemes"),themes=new JSONArray();
        if(rawThemes!=null){
            if(rawThemes.length()>50)throw new IllegalArgumentException("Too many custom themes in this file.");
            Set<String> ids=new HashSet<>();
            for(int i=0;i<rawThemes.length();i++){
                JSONObject raw=rawThemes.getJSONObject(i);String id=raw.getString("id"),name=raw.getString("name").trim();
                if(!id.matches("custom:[a-f0-9-]{36}")||!ids.add(id)||name.isEmpty()||name.length()>40)throw new IllegalArgumentException("Invalid custom theme.");
                ThemeColors colors=new ThemeColors(raw.getString("background"),raw.getString("primary"),raw.getString("secondary"),raw.getString("preferredText"));
                themes.put(new JSONObject().put("id",id).put("name",name).put("background",colors.background)
                    .put("primary",colors.primary).put("secondary",colors.secondary).put("preferredText",colors.preferredText));
            }
        }
        for(String key:INTS)if(settings.has(key)){
            int n=exactInt(settings.get(key),key);
            if(!validInt(key,n))throw new IllegalArgumentException("Invalid "+key+" setting.");
        }
        for(String key:BOOLEANS)if(settings.has(key)&&!(settings.get(key) instanceof Boolean))throw new IllegalArgumentException("Invalid "+key+" setting.");
        for(String key:STRINGS)if(settings.has(key)&&!(settings.get(key) instanceof String))throw new IllegalArgumentException("Invalid "+key+" setting.");
        if(settings.has("filter")&&settings.getString("filter").length()>120)throw new IllegalArgumentException("Invalid title filter.");
        if(settings.has("sound")&&!SoundLibrary.allowed(settings.getString("sound"))&&!CustomSounds.id(settings.getString("sound")))throw new IllegalArgumentException("Invalid alarm sound.");
        if(settings.has("iconDesign")&&!Arrays.asList(Appearance.ICONS).contains(settings.getString("iconDesign")))throw new IllegalArgumentException("Invalid icon design.");
        if(settings.has("appearance")){
            String id=settings.getString("appearance");boolean known=false;
            for(Appearance theme:Appearance.ALL)if(theme.id.equals(id))known=true;
            for(int i=0;i<themes.length();i++)if(themes.getJSONObject(i).getString("id").equals(id))known=true;
            if(!known)throw new IllegalArgumentException("Invalid color theme.");
        }
        int early=settings.has("earlyBefore")?exactInt(settings.get("earlyBefore"),"earlyBefore"):Store.p(c).getInt("earlyBefore",600);
        int late=settings.has("lateAfter")?exactInt(settings.get("lateAfter"),"lateAfter"):Store.p(c).getInt("lateAfter",720);
        if(late<=early)throw new IllegalArgumentException("Later shift time must follow early shift time.");
        return new Validated(settings,themes,rawThemes!=null);
    }
    public static void importJson(Context c,String text)throws Exception{
        Validated checked=validate(c,text);JSONObject settings=checked.settings;JSONArray themes=checked.themes;
        SharedPreferences.Editor editor=Store.p(c).edit();
        for(String key:INTS)if(settings.has(key)){int value=exactInt(settings.get(key),key);if(!"volume".equals(key))editor.putInt(key,value);}
        for(String key:BOOLEANS)if(settings.has(key)&&!"quietPreset".equals(key))editor.putBoolean(key,settings.getBoolean(key));
        editor.putInt("volume",100).putBoolean("quietPreset",false).remove("previousPlayback");
        for(String key:STRINGS)if(settings.has(key))editor.putString(key,settings.getString(key));
        if(checked.hasThemes)editor.putString("customThemes",themes.toString());
        if(!editor.commit())throw new IllegalStateException("Could not save settings.");
        Appearance.syncLauncher(c);Sync.run(c,false);
    }

    private static int exactInt(Object value,String key){
        if(!(value instanceof Number))throw new IllegalArgumentException("Invalid "+key+" setting.");
        try{return new BigDecimal(value.toString()).intValueExact();}
        catch(NumberFormatException|ArithmeticException invalid){throw new IllegalArgumentException("Invalid "+key+" setting.",invalid);}
    }

    private static boolean validInt(String key,int n){
        switch(key){
            case "lead":case "earlyLead":case "lateLead":return n>=0&&n<=1440;
            case "snooze":return n>=1&&n<=60;
            case "volume":return n>=10&&n<=100;
            case "fade":return n==0||n==5||n==15||n==30||n==60||n==120;
            case "riseStartPercent":return n>=10&&n<=100;
            case "fallbackMinutes":return n==0||n==5||n==10||n==15;
            case "maxRingMinutes":return n==15||n==30||n==60;
            default:return n>=0&&n<=1439;
        }
    }
    private SettingsTransfer(){}
}
