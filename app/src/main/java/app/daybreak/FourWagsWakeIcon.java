package app.daybreak;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import dev.fourwags.rouse.R;

/** First-launch choice only; AlarmService schedules and stored settings stay unchanged. */
@SuppressLint("ApplySharedPref")
public final class FourWagsWakeIcon {
    private FourWagsWakeIcon() {}
    public static void initialize(Context c){
        var p=Store.p(c);
        if(p.contains("fw_icon_initialized"))return;
        boolean fresh=false;
        try{var info=c.getPackageManager().getPackageInfo(c.getPackageName(),0);fresh=info.firstInstallTime==info.lastUpdateTime;}
        catch(PackageManager.NameNotFoundException ignored){}
        var editor=p.edit().putBoolean("fw_icon_initialized",true).putBoolean("fw_icon_pending",fresh);
        if(fresh)editor.putString("iconDesign","rouse_c");
        editor.commit();
    }
    public static boolean pending(Context c){return Store.p(c).getBoolean("fw_icon_pending",false);}
    public static void skip(Context c){boolean first=pending(c);if(Store.p(c).edit().putBoolean("fw_icon_pending",false).commit()&&first)try{Appearance.syncLauncher(c);}catch(RuntimeException ignored){} }
    public static void showPicker(Activity activity){
        var rows=new LinearLayout[3];var labels=new String[]{"Sunrise","Sunrise clock","Shiba alarm"};
        var icons=new int[]{R.drawable.fw_rouse_a_foreground,R.drawable.fw_rouse_b_foreground,R.drawable.fw_rouse_c_foreground};
        float density=activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp=n->Math.round(n*density);
        var body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp.applyAsInt(16),dp.applyAsInt(8),dp.applyAsInt(16),dp.applyAsInt(8));
        for(int i=0;i<3;i++){
            var row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp.applyAsInt(68));
            var image=new ImageView(activity);image.setImageResource(icons[i]);image.setBackgroundColor(0xff1c1b1a);
            image.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(image,new LinearLayout.LayoutParams(dp.applyAsInt(56),dp.applyAsInt(56)));
            var title=new TextView(activity);title.setText(labels[i]);title.setTextSize(16);title.setPadding(dp.applyAsInt(16),0,0,0);
            row.addView(title);row.setContentDescription(labels[i]+" launcher icon");body.addView(row);rows[i]=row;
        }
        var dialog=new AlertDialog.Builder(activity).setTitle("Choose Rouse's icon")
            .setMessage("You can change this later in Appearance settings.")
            .setView(body).setNegativeButton("Later",(d,which)->skip(activity)).create();
        dialog.setOnCancelListener(d->skip(activity));
        for(int i=0;i<3;i++){
            final String choice="rouse_"+(char)('a'+i);
            rows[i].setOnClickListener(v->{
                try{Appearance.chooseIcon(activity,choice);skip(activity);dialog.dismiss();}
                catch(RuntimeException e){Toast.makeText(activity,"Could not save the icon. Please try again.",Toast.LENGTH_LONG).show();}
            });
        }
        dialog.show();
    }
}
