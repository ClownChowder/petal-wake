package app.daybreak;
import java.util.*;

/** Pure color math shared by persisted themes and native UI. Surfaces and readable text are derived. */
public final class ThemeColors {
    public final String background,primary,secondary,preferredText,surface,text,actionText,link,secondaryLink,control;
    public final Map<String,String> tokens=new LinkedHashMap<>();
    public ThemeColors(String background,String primary,String secondary,String preferredText){
        this.background=hex(background);this.primary=hex(primary);this.secondary=hex(secondary);this.preferredText=hex(preferredText);
        text=readable(this.preferredText,this.background);
        boolean dark=luminance(this.background)<.179;
        surface=mix(this.background,dark?"#000000":"#FFFFFF",dark?.12:.42);
        control=surface(surface,text,.07);
        actionText=best(this.primary);link=readableBoth(this.primary,this.background,control,text);secondaryLink=readableBoth(this.secondary,this.background,control,text);
        String muted=readableBoth(mix(text,this.background,.22),this.background,control,text);
        tokens.put("paper",this.background);tokens.put("ink",text);tokens.put("muted",muted);tokens.put("lavender",link);tokens.put("mint",secondaryLink);tokens.put("card",surface);tokens.put("control",control);
        tokens.put("line",mix(this.background,text,.25));tokens.put("placeholder",muted);tokens.put("outline",muted);tokens.put("horizon",mix(surface,this.secondary,.12));tokens.put("track",mix(this.background,text,.25));tokens.put("secondary",mix(surface,this.secondary,.06));tokens.put("input-line",muted);tokens.put("input",mix(this.background,surface,.4));
        tokens.put("action",this.primary);tokens.put("on-action",actionText);tokens.put("nav",this.background+"F5");tokens.put("glass",this.background+"33");tokens.put("glass-card",this.background+"8C");tokens.put("empty",surface+"55");tokens.put("overlay","#14121A99");
        tokens.put("error-bg",dark?"#51343C":"#FBE1DD");tokens.put("error-text",dark?"#FFD0C7":"#7B332C");
    }
    private ThemeColors(String background,String primary,String secondary,String foreground,String surface,String control,String actionText,String link){
        this.background=background;this.primary=primary;this.secondary=secondary;this.preferredText=foreground;
        this.text=foreground;this.surface=surface;this.control=control;this.actionText=actionText;this.link=link;this.secondaryLink=secondary;
    }
    public static ThemeColors shiba(String id){
        ThemeColors c;
        switch(id){case "shiba_apricot": c=new ThemeColors("#E8DEC8","#97451D","#D98242","#1C1B1A","#F6EFE2","#EFE4D0","#F6EFE2","#97451D");c.tokens.put("paper","#E8DEC8");c.tokens.put("ink","#1C1B1A");c.tokens.put("muted","#66594B");c.tokens.put("lavender","#97451D");c.tokens.put("mint","#97451D");c.tokens.put("card","#F6EFE2");c.tokens.put("control","#EFE4D0");c.tokens.put("line","#9E8B75");c.tokens.put("placeholder","#66594B");c.tokens.put("outline","#9E8B75");c.tokens.put("horizon","#EFE4D0");c.tokens.put("track","#C9B69C");c.tokens.put("secondary","#EFE4D0");c.tokens.put("input","#F6EFE2");c.tokens.put("action","#97451D");c.tokens.put("nav","#E8DEC8F5");c.tokens.put("glass","#E8DEC833");c.tokens.put("empty","#F6EFE255");c.tokens.put("overlay","#14121A99");c.tokens.put("warning","#775519");c.tokens.put("success","#246448");c.tokens.put("input-line","#9E8B75");c.tokens.put("on-action","#F6EFE2");c.tokens.put("glass-card","#E8DEC88C");c.tokens.put("error-bg","#F6EFE2");c.tokens.put("error-text","#AC3535"); return c;case "shiba_black_tan": c=new ThemeColors("#1C1B1A","#E2A56A","#E2A56A","#F6EFE2","#2B2825","#37312B","#1C1B1A","#E2A56A");c.tokens.put("paper","#1C1B1A");c.tokens.put("ink","#F6EFE2");c.tokens.put("muted","#CDBCA8");c.tokens.put("lavender","#E2A56A");c.tokens.put("mint","#E2A56A");c.tokens.put("card","#2B2825");c.tokens.put("control","#37312B");c.tokens.put("line","#8F7D69");c.tokens.put("placeholder","#CDBCA8");c.tokens.put("outline","#8F7D69");c.tokens.put("horizon","#37312B");c.tokens.put("track","#655444");c.tokens.put("secondary","#37312B");c.tokens.put("input","#2B2825");c.tokens.put("action","#E2A56A");c.tokens.put("nav","#1C1B1AF5");c.tokens.put("glass","#1C1B1A33");c.tokens.put("empty","#2B282555");c.tokens.put("overlay","#14121A99");c.tokens.put("warning","#F4D17D");c.tokens.put("success","#AED4AC");c.tokens.put("input-line","#8F7D69");c.tokens.put("on-action","#1C1B1A");c.tokens.put("glass-card","#1C1B1A8C");c.tokens.put("error-bg","#2B2825");c.tokens.put("error-text","#F39D98"); return c;case "shiba_sesame": c=new ThemeColors("#302822","#E8A46F","#D98242","#F6EFE2","#40352D","#514136","#302822","#E8A46F");c.tokens.put("paper","#302822");c.tokens.put("ink","#F6EFE2");c.tokens.put("muted","#DBC3AB");c.tokens.put("lavender","#E8A46F");c.tokens.put("mint","#97451D");c.tokens.put("card","#40352D");c.tokens.put("control","#514136");c.tokens.put("line","#AA8B70");c.tokens.put("placeholder","#DBC3AB");c.tokens.put("outline","#AA8B70");c.tokens.put("horizon","#514136");c.tokens.put("track","#6A5140");c.tokens.put("secondary","#514136");c.tokens.put("input","#40352D");c.tokens.put("action","#E8A46F");c.tokens.put("nav","#302822F5");c.tokens.put("glass","#30282233");c.tokens.put("empty","#40352D55");c.tokens.put("overlay","#14121A99");c.tokens.put("warning","#F4D17D");c.tokens.put("success","#AED4AC");c.tokens.put("input-line","#AA8B70");c.tokens.put("on-action","#302822");c.tokens.put("glass-card","#3028228C");c.tokens.put("error-bg","#40352D");c.tokens.put("error-text","#F39D98"); return c;default:throw new IllegalArgumentException("Unknown Shiba palette");}
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
