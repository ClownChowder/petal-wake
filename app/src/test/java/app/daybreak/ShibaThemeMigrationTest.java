package app.daybreak;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShibaThemeMigrationTest {
    private static SharedPreferences prefs(Map<String,Object> values, AtomicBoolean failCommit) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                switch(method.getName()) {
                    case "getInt": return values.getOrDefault((String)args[0], args[1]);
                    case "getString": return values.getOrDefault((String)args[0], args[1]);
                    case "contains": return values.containsKey((String)args[0]);
                    case "edit":
                        Map<String,Object> pending = new HashMap<>();
                        return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (editor, editMethod, editArgs) -> {
                                switch(editMethod.getName()) {
                                    case "putInt": case "putString": pending.put((String)editArgs[0],editArgs[1]); return editor;
                                    case "commit": if(failCommit.get())return false; values.putAll(pending); return true;
                                    default: throw new UnsupportedOperationException(editMethod.getName());
                                }
                            });
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
    }

    @Test public void savedThemeResetsOnceAndIconAndAlarmStay() {
        Map<String,Object> values=new HashMap<>(Map.of("appearance","rose","iconDesign","moon","volume",66));
        SharedPreferences p=prefs(values,new AtomicBoolean(false));
        assertTrue(ShibaThemeMigration.apply(p));
        assertEquals("shiba_black_tan",p.getString("appearance",null));
        assertEquals(1,p.getInt(ShibaThemeMigration.MARKER,0));
        assertEquals("moon",p.getString("iconDesign",null));
        assertEquals(66,p.getInt("volume",0));
        values.put("appearance","shiba_sesame");
        assertTrue(ShibaThemeMigration.apply(p));
        assertEquals("shiba_sesame",p.getString("appearance",null));
    }

    @Test public void interruptedCommitRetriesWithoutPartialReset() {
        Map<String,Object> values=new HashMap<>(Map.of("appearance","sage"));
        AtomicBoolean fail=new AtomicBoolean(true);
        SharedPreferences p=prefs(values,fail);
        assertFalse(ShibaThemeMigration.apply(p));
        assertEquals("sage",p.getString("appearance",null));
        assertFalse(p.contains(ShibaThemeMigration.MARKER));
        fail.set(false);
        assertTrue(ShibaThemeMigration.apply(p));
        assertEquals("shiba_black_tan",p.getString("appearance",null));
    }
}
