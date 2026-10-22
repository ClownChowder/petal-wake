package app.daybreak;
import java.util.*;

/** Pure color math shared by persisted themes and native UI. Surfaces and readable text are derived. */
public final class ThemeColors {
    public final String background,primary,secondary,preferredText,surface,text,actionText,link,secondaryLink,control;
    public final Map<String,String> tokens=new LinkedHashMap<>();
    public ThemeColors(String background,String primary,String secondary,String preferredText){
        this.background=hex(background);this.primary=hex(primary);this.secondary=hex(secondary);this.preferredText=hex(preferredText);
        text=readable(this.preferredText,this.background);surface=surface(this.background,text,.07);control=surface(surface,text,.07);
        actionText=best(this.primary);link=readableBoth(this.primary,this.background,control,text);secondaryLink=readableBoth(this.secondary,this.background,control,text);
        String muted=readableBoth(mix(text,this.background,.22),this.background,control,text);
        tokens.put("paper",this.background);tokens.put("ink",text);tokens.put("muted",muted);tokens.put("lavender",link);tokens.put("mint",secondaryLink);tokens.put("card",surface);tokens.put("control",control);
        tokens.put("line",mix(this.background,text,.25));tokens.put("placeholder",muted);tokens.put("outline",muted);tokens.put("horizon",mix(surface,this.secondary,.12));tokens.put("track",mix(this.background,text,.25));tokens.put("secondary",mix(surface,this.secondary,.06));tokens.put("input-line",muted);tokens.put("input",mix(this.background,surface,.4));
        tokens.put("action",this.primary);tokens.put("on-action",actionText);tokens.put("nav",this.background+"F5");tokens.put("glass",this.background+"33");tokens.put("glass-card",this.background+"8C");tokens.put("empty",surface+"55");tokens.put("overlay","#14121A99");
        boolean dark=luminance(this.background)<.179;tokens.put("error-bg",dark?"#51343C":"#FBE1DD");tokens.put("error-text",dark?"#FFD0C7":"#7B332C");
    }
    public static String hex(String value){if(value==null||!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Choose a valid six-digit hex color.");return value.toUpperCase(Locale.ROOT);}
    private static int rgb(String value,int channel){return Integer.parseInt(value.substring(1+channel*2,3+channel*2),16);}
    public static double luminance(String color){double total=0;double[] weights={.2126,.7152,.0722};for(int n=0;n<3;n++){double v=rgb(color,n)/255.0;total+=weights[n]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return total;}
    public static double contrast(String a,String b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    public static String best(String background){return contrast("#FFFFFF",background)>=contrast("#000000",background)?"#FFFFFF":"#000000";}
    public static String mix(String a,String b,double fraction){StringBuilder out=new StringBuilder("#");for(int n=0;n<3;n++)out.append(String.format(Locale.ROOT,"%02X",Math.round(rgb(a,n)*(1-fraction)+rgb(b,n)*fraction)));return out.toString();}
    private static String surface(String base,String text,double fraction){for(double f=fraction;f>0;f-=.01){String c=mix(base,text,f);if(contrast(text,c)>=4.5)return c;}return base;}
    public static String readable(String preferred,String background){return readableBoth(preferred,background,background,best(background));}
    private static String readableBoth(String preferred,String a,String b,String fallback){if(contrast(preferred,a)>=4.5&&contrast(preferred,b)>=4.5)return preferred;for(int n=1;n<=100;n++){String c=mix(preferred,fallback,n/100.0);if(contrast(c,a)>=4.5&&contrast(c,b)>=4.5)return c;}return fallback;}
}
