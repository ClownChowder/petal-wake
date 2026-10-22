package app.daybreak;
import android.app.*;import android.content.*;import android.os.*;import java.util.*;
public class FamilyMigrationTest extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();
 Store.p(c).edit().putInt("lead",37).putInt("volume",61).putBoolean("enabled",false).putString("iconDesign","moon").commit();
 String[][] pairs={{"lavender","violet"},{"sand","arctic"},{"peach","vermilion"},{"plum","pistachio"},{"graphite","navy"},{"moonstone","rose"},{"forest","sage"},{"amber","marigold"},{"cocoa","arctic"},{"twilight","violet"},{"mint","sage"},{"terracotta","berry"},{"copper","coral"},{"mulberry","berry"},{"lilacspruce","sage"},{"jaderibbon","rose"},{"raspberry","berry"},{"skypapaya","cobalt"},{"saffron","marigold"},{"olive","sage"},{"flamingo","rose"},{"ochre","marigold"},{"peony","blush"},{"cherry","rose"}};
 for(String[] pair:pairs){Store.p(c).edit().putString("appearance",pair[0]).commit();if(!Appearance.current(c).id.equals(pair[1]))throw new AssertionError("Migration "+pair[0]);
 if(!Store.p(c).getString("appearance","").equals(pair[1]))throw new AssertionError("Persist migration");
 var aliases=c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName()),0);
 if(aliases.size()!=1||!aliases.get(0).activityInfo.name.endsWith(Appearance.current(c).launcher("moon")))throw new AssertionError("Migration launcher");
 }
 if(Store.p(c).getInt("lead",0)!=37||Store.p(c).getInt("volume",0)!=61||Store.p(c).getBoolean("enabled",true)||!Appearance.icon(c).equals("moon"))throw new AssertionError("Unrelated settings changed");
 out.putString("summary","PASS: 24 retired choices migrated with launcher and nonappearance settings preserved");finish(-1,out);
 }catch(Throwable t){out.putString("error",t.toString());finish(0,out);}}
}