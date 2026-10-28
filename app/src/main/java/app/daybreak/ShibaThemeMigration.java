package app.daybreak;

import android.content.SharedPreferences;

/** One-time app palette migration; icon choice, alarms, and all other preferences stay intact. */
final class ShibaThemeMigration {
    static final String MARKER = "shiba_theme_reset_version";
    private ShibaThemeMigration() {}

    static boolean apply(SharedPreferences prefs) {
        return prefs.getInt(MARKER, 0) >= 1 || prefs.edit()
            .putString("appearance", "shiba_black_tan")
            .putInt(MARKER, 1)
            .commit();
    }
}
