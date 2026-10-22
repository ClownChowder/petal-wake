package app.daybreak;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalKeyPaletteTest {
 @Test public void allSixteenPalettesHaveReadableControlsAndDialogs(){
check("rose","#8A4D63","#F5F0E8","#E6B4C5","#FFF8F0");
check("sage","#596C5A","#EEE4CF","#D9C7AB","#FFF8F0");
check("navy","#142C47","#72C9BB","#D5F1CE","#FFF8F0");
check("ocean","#D5E7E2","#3B7775","#254E65","#211B22");
check("blush","#3A5275","#DFB1BE","#C7DDEC","#FFF8F0");
check("arctic","#244C72","#B7DFF3","#EEF2FA","#FFF8F0");
check("violet","#302940","#B79AD7","#D7C2F2","#FFF8F0");
check("berry","#E8BCD7","#72254F","#452D62","#211B22");
check("vermilion","#172C3B","#F18069","#F2CA82","#FFF8F0");
check("lemon","#F1E7A6","#346C55","#31586E","#211B22");
check("cobalt","#F6BCAD","#264AA0","#34406E","#211B22");
check("pistachio","#CCD98F","#612750","#3B4569","#211B22");
check("marigold","#183455","#F1D95B","#FFE9A8","#FFF8F0");
check("coral","#EEAD94","#19515F","#313F70","#211B22");
check("chartreuse","#354351","#C5D46E","#DCEAB1","#FFF8F0");
check("amethyst","#67428A","#C4E4EF","#E3D1F3","#FFF8F0");

 }
 private void check(String id,String bg,String primary,String secondary,String preferred){
 ThemeColors c=new ThemeColors(bg,primary,secondary,preferred);
 assertEquals(primary,c.primary);assertEquals(secondary,c.secondary);
 for(String surface:new String[]{c.background,c.surface,c.control,c.tokens.get("input")}) {
 for(String ink:new String[]{c.text,c.link,c.secondaryLink,c.tokens.get("muted")})assertTrue(id+" "+ink+" on "+surface,ThemeColors.contrast(ink,surface)>=4.5);
 }
 assertTrue(id,ThemeColors.contrast(c.tokens.get("input-line"),c.tokens.get("input"))>=3);
 assertTrue(id,ThemeColors.contrast(c.actionText,c.primary)>=4.5);
 }
}
