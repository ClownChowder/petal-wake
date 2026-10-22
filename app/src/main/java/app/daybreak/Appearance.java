package app.daybreak;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.Build;
import java.util.*;
import org.json.*;

/** Persisted palette and independent icon shape; all launcher combinations are bundled offline. */
public final class Appearance {
    public static final String CHANGED="app.daybreak.APPEARANCE_CHANGED";
    public static final Appearance[] ALL={keyPalette("rose","Rose Porcelain",R.style.Key_rose,"#8A4D63","#F5F0E8","#E6B4C5","#FFF8F0"),
        keyPalette("sage","Sage & Linen",R.style.Key_sage,"#596C5A","#EEE4CF","#D9C7AB","#FFF8F0"),
        keyPalette("navy","Harbor Jade",R.style.Key_navy,"#142C47","#72C9BB","#D5F1CE","#FFF8F0"),
        keyPalette("ocean","Tidepool Glass",R.style.Key_ocean,"#D5E7E2","#3B7775","#254E65","#211B22"),
        keyPalette("blush","Blush Denim",R.style.Key_blush,"#3A5275","#DFB1BE","#C7DDEC","#FFF8F0"),
        keyPalette("arctic","Arctic Sail",R.style.Key_arctic,"#244C72","#B7DFF3","#EEF2FA","#FFF8F0"),
        keyPalette("violet","Moonlit Orchid",R.style.Key_violet,"#302940","#B79AD7","#D7C2F2","#FFF8F0"),
        keyPalette("berry","Berry Sorbet",R.style.Key_berry,"#E8BCD7","#72254F","#452D62","#211B22"),
        keyPalette("vermilion","Vermilion Ink",R.style.Key_vermilion,"#172C3B","#F18069","#F2CA82","#FFF8F0"),
        keyPalette("lemon","Lemon Grove",R.style.Key_lemon,"#F1E7A6","#346C55","#31586E","#211B22"),
        keyPalette("cobalt","Cobalt Bloom",R.style.Key_cobalt,"#F6BCAD","#264AA0","#34406E","#211B22"),
        keyPalette("pistachio","Pistachio Plum",R.style.Key_pistachio,"#CCD98F","#612750","#3B4569","#211B22"),
        keyPalette("marigold","Marigold Sail",R.style.Key_marigold,"#183455","#F1D95B","#FFE9A8","#FFF8F0"),
        keyPalette("coral","Coral Reef",R.style.Key_coral,"#EEAD94","#19515F","#313F70","#211B22"),
        keyPalette("chartreuse","Chartreuse Smoke",R.style.Key_chartreuse,"#354351","#C5D46E","#DCEAB1","#FFF8F0"),
        keyPalette("amethyst","Amethyst Ice",R.style.Key_amethyst,"#67428A","#C4E4EF","#E3D1F3","#FFF8F0")};
    public static final Appearance NAVY=builtin("navy"),ROSE=builtin("rose");
    private static Appearance builtin(String id){for(Appearance p:ALL)if(p.id.equals(id))return p;throw new IllegalArgumentException(id);}
    public static final String[] ICONS={"petal","dawn","moon","bell","baton","calendar"};
    public final String id,name;
    public final int theme,background,surface,text,action,onAction,accent;
    public final boolean dark;
    public ThemeColors customColors;
    private Appearance(String id,String name,int theme,boolean dark,String background,String surface,String text,String action,String onAction,String accent){
        this.id=id;this.name=name;this.theme=theme;this.dark=dark;this.background=Color.parseColor(background);this.surface=Color.parseColor(surface);
        this.text=Color.parseColor(text);this.action=Color.parseColor(action);this.onAction=Color.parseColor(onAction);this.accent=Color.parseColor(accent);
    }
    private static Appearance keyPalette(String id,String name,int style,String bg,String primary,String secondary,String text){
        ThemeColors colors=new ThemeColors(bg,primary,secondary,text);
        Appearance p=new Appearance(id,name,style,ThemeColors.luminance(bg)<.179,colors.background,colors.surface,colors.text,colors.primary,colors.actionText,colors.link);
        p.customColors=colors;return p;
    }
    private static String resolveId(String id){switch(id){case "lavender":return "violet";case "sand":return "arctic";case "peach":return "vermilion";case "plum":return "pistachio";case "graphite":return "navy";case "moonstone":return "rose";case "forest":return "sage";case "amber":return "marigold";case "cocoa":return "arctic";case "twilight":return "violet";case "mint":return "sage";case "terracotta":return "berry";case "copper":return "coral";case "mulberry":return "berry";case "lilacspruce":return "sage";case "jaderibbon":return "rose";case "raspberry":return "berry";case "skypapaya":return "cobalt";case "saffron":return "marigold";case "olive":return "sage";case "flamingo":return "rose";case "ochre":return "marigold";case "peony":return "blush";case "cherry":return "rose";default:return id;}}
    private static Appearance find(Context c,String id){id=resolveId(id);for(Appearance p:ALL)if(p.id.equals(id))return p;JSONArray saved=Store.array(c,"customThemes");for(int n=0;n<saved.length();n++){JSONObject raw=saved.optJSONObject(n);if(raw!=null&&id.equals(raw.optString("id")))try{return custom(raw);}catch(Exception ignored){}}return null;}
    private static Appearance custom(JSONObject raw)throws JSONException {
        String id=raw.getString("id"),name=raw.getString("name").trim();if(!id.matches("custom:[a-f0-9-]{36}")||name.isEmpty()||name.length()>40)throw new IllegalArgumentException("Choose a theme name of 1–40 characters.");
        ThemeColors colors=new ThemeColors(raw.getString("background"),raw.getString("primary"),raw.getString("secondary"),raw.getString("preferredText"));boolean dark=ThemeColors.luminance(colors.background)<.179;
        Appearance p=new Appearance(id,name,dark?R.style.Theme_graphite:R.style.Theme_moonstone,dark,colors.background,colors.surface,colors.text,colors.primary,colors.actionText,colors.link);p.customColors=colors;return p;
    }
    public JSONObject customJson()throws JSONException {
        ThemeColors c=customColors;return new JSONObject().put("id",id).put("name",name).put("custom",true).put("dark",dark).put("background",c.background).put("primary",c.primary).put("secondary",c.secondary).put("preferredText",c.preferredText).put("text",c.text).put("surface",c.surface).put("action",c.primary).put("onAction",c.actionText).put("accent",c.link).put("tokens",new JSONObject(c.tokens));
    }
    public static JSONArray customThemes(Context c){JSONArray out=new JSONArray(),saved=Store.array(c,"customThemes");for(int n=0;n<saved.length();n++)try{out.put(custom(saved.getJSONObject(n)).customJson());}catch(Exception ignored){}return out;}
    public static void saveCustom(Context c,JSONObject raw)throws JSONException {
        JSONArray saved=Store.array(c,"customThemes");String id=raw.optString("id","");boolean editing=!id.isEmpty();if(!editing)id="custom:"+UUID.randomUUID();raw.put("id",id);Appearance p=custom(raw);JSONArray next=new JSONArray();boolean found=false;
        for(int n=0;n<saved.length();n++){JSONObject item=saved.getJSONObject(n);if(id.equals(item.optString("id"))){next.put(p.customJson());found=true;}else next.put(item);}
        if(editing&&!found)throw new IllegalArgumentException("That custom theme no longer exists.");if(!editing){if(next.length()>=50)throw new IllegalArgumentException("Keep up to 50 custom themes. Edit or remove one first.");next.put(p.customJson());}
        String old=saved.toString();Store.put(c,"customThemes",next);try{save(c,id,icon(c));}catch(RuntimeException e){Store.p(c).edit().putString("customThemes",old).commit();throw e;}
    }
    public static void deleteCustom(Context c,String id)throws JSONException {
        JSONArray saved=Store.array(c,"customThemes"),next=new JSONArray();boolean found=false;for(int n=0;n<saved.length();n++){JSONObject item=saved.getJSONObject(n);if(id.equals(item.optString("id")))found=true;else next.put(item);}if(!found)throw new IllegalArgumentException("That custom theme no longer exists.");
        if(current(c).id.equals(id))choose(c,"rose");Store.put(c,"customThemes",next);
    }
    public static Appearance launcherPalette(Appearance p){for(Appearance candidate:ALL)if(candidate.id.equals(p.id))return candidate;Appearance closest=ROSE;double distance=Double.MAX_VALUE;for(Appearance candidate:ALL){double score=0;int[] x={p.background,p.action},y={candidate.background,candidate.action};for(int k=0;k<x.length;k++)score+=Math.pow(Color.red(x[k])-Color.red(y[k]),2)+Math.pow(Color.green(x[k])-Color.green(y[k]),2)+Math.pow(Color.blue(x[k])-Color.blue(y[k]),2);if(score<distance){distance=score;closest=candidate;}}return closest;}
    public static Appearance current(Context c){String id=Store.p(c).getString("appearance","rose");Appearance p=find(c,id);if(p!=null&&!p.id.equals(id)){Store.p(c).edit().putString("appearance",p.id).commit();syncLauncher(c);}return p==null?ROSE:p;}
    public static String icon(Context c){String id=Store.p(c).getString("iconDesign","petal");return Arrays.asList(ICONS).contains(id)?id:"petal";}
    public String launcher(String icon){
        if(customColors!=null){Appearance closest=launcherPalette(this);return closest.launcherName(icon);}
        return launcherName(icon);
    }
    private String launcherName(String icon){
        if("petal".equals(icon)&&("navy".equals(id)||"rose".equals(id)))return title(id)+"Launcher";
        return title(id)+title(icon)+"Launcher";
    }
    private static String title(String id){return id.substring(0,1).toUpperCase(Locale.ROOT)+id.substring(1);}
    public int iconResource(Context c,String shape){if(customColors!=null)return launcherPalette(this).bundledIcon(c,shape);return bundledIcon(c,shape);}
    private int bundledIcon(Context c,String shape){return c.getResources().getIdentifier("ic_launcher_"+id+"_"+shape,"mipmap",c.getPackageName());}
    public static void choose(Context c,String id){if(find(c,id)==null)throw new IllegalArgumentException("Unknown theme.");save(c,id,icon(c));}
    public static void chooseIcon(Context c,String shape){if(!Arrays.asList(ICONS).contains(shape))throw new IllegalArgumentException("Unknown icon design.");save(c,current(c).id,shape);}
    private static void save(Context c,String id,String shape){
        var prefs=Store.p(c);String oldTheme=prefs.getString("appearance",null),oldIcon=prefs.getString("iconDesign",null);
        if(!prefs.edit().putString("appearance",id).putString("iconDesign",shape).commit())throw new IllegalStateException("Could not save appearance.");
        try{syncLauncher(c);}catch(RuntimeException e){var editor=prefs.edit();if(oldTheme==null)editor.remove("appearance");else editor.putString("appearance",oldTheme);if(oldIcon==null)editor.remove("iconDesign");else editor.putString("iconDesign",oldIcon);editor.commit();syncLauncher(c);throw e;}
        AlarmService.appearanceChanged();c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
    }
    public static void syncLauncher(Context c){
        PackageManager pm=c.getPackageManager();String chosen=c.getPackageName()+"."+current(c).launcher(icon(c));
        var enabled=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName()),0);
        boolean selectedEnabled=false;for(ResolveInfo entry:enabled)if(chosen.equals(entry.activityInfo.name))selectedEnabled=true;
        ArrayList<PackageManager.ComponentEnabledSetting> batch=Build.VERSION.SDK_INT>=33?new ArrayList<>():null;
        if(!selectedEnabled){ComponentName selected=new ComponentName(c.getPackageName(),chosen);
            pm.setComponentEnabledSetting(selected,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        }
        
        // Enable the selected launcher entry before disabling older aliases.
        for(ResolveInfo entry:enabled){ActivityInfo info=entry.activityInfo;if(chosen.equals(info.name)||!"app.daybreak.LauncherActivity".equals(info.targetActivity))continue;
            ComponentName other=new ComponentName(c.getPackageName(),info.name);
            if(Build.VERSION.SDK_INT>=33)batch.add(new PackageManager.ComponentEnabledSetting(other,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP));
            else pm.setComponentEnabledSetting(other,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        }
        if(Build.VERSION.SDK_INT>=33&&!batch.isEmpty())pm.setComponentEnabledSettings(batch);
    }
    public static boolean light(int color){
        double sum=0;double[] weights={.2126,.7152,.0722};int[] values={Color.red(color),Color.green(color),Color.blue(color)};
        for(int n=0;n<3;n++){double v=values[n]/255.0;sum+=weights[n]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return sum>.179;
    }
}
