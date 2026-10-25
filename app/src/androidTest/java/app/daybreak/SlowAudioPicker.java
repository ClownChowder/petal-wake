package app.daybreak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Returns a persistable grant just as a document picker would. */
public final class SlowAudioPicker extends Activity {
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        Intent result=new Intent().setData(SlowAudioProvider.URI)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        setResult(RESULT_OK,result);finish();
    }
}
