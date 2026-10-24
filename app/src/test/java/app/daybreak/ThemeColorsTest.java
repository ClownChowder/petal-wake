package app.daybreak;
import org.junit.Test;
import static org.junit.Assert.*;
public class ThemeColorsTest {
 @Test public void unreadableTextIsAdjustedAndChosenAccentsRemainExact(){ThemeColors c=new ThemeColors("#eeeeee","#ffffee","#ff99cc","#eeeeee");assertEquals("#FFFFEE",c.primary);assertEquals("#FF99CC",c.secondary);assertTrue(ThemeColors.contrast(c.text,c.background)>=4.5);assertTrue(ThemeColors.contrast(c.text,c.surface)>=4.5);assertTrue(ThemeColors.contrast(c.actionText,c.primary)>=4.5);}
 @Test public void midGrayBoundaryStillHasReadableTextAcrossPanels(){for(int level=0;level<=255;level++){String gray=String.format("#%02X%02X%02X",level,level,level);ThemeColors c=new ThemeColors(gray,"#777777","#888888",gray);assertTrue(gray,ThemeColors.contrast(c.text,c.background)>=4.5);assertTrue(gray,ThemeColors.contrast(c.text,c.control)>=4.5);assertTrue(gray,ThemeColors.contrast(c.link,c.control)>=4.5);}}
 @Test public void validPreferredTextAndMixedCaseHexArePreserved(){ThemeColors c=new ThemeColors("#121e34","#ab75de","#71debb","#ffffff");assertEquals("#121E34",c.background);assertEquals("#FFFFFF",c.text);}
 @Test public void cardsSeparateFromLightAndDarkBackgroundsWithoutLosingTextContrast(){
  ThemeColors dark=new ThemeColors("#8A4D63","#F5F0E8","#E6B4C5","#FFF8F0");
  ThemeColors light=new ThemeColors("#D5E7E2","#3B7775","#254E65","#211B22");
  assertTrue(ThemeColors.luminance(dark.surface)<ThemeColors.luminance(dark.background));
  assertTrue(ThemeColors.luminance(light.surface)>ThemeColors.luminance(light.background));
  assertTrue(ThemeColors.contrast(dark.text,dark.surface)>=4.5);
  assertTrue(ThemeColors.contrast(light.text,light.surface)>=4.5);
 }
 @Test(expected=IllegalArgumentException.class) public void cssAndPartialColorsAreRejected(){new ThemeColors("red;position:fixed","#000000","#000000","#ffffff");}
}
