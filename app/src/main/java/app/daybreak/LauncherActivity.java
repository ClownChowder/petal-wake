package app.daybreak;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
/** Keep switchable launcher aliases out of the real app task so disabling one cannot close the UI. */
public class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
}
