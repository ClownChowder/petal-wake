package app.daybreak;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.*;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
public class RingActivity extends Activity {
    private LinearLayout root;private Button snooze,awake;private boolean receiverRegistered;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private static final long DISMISS_HOLD_MS=2_000L;
    private long dismissHoldBegan;
    private boolean dismissHoldFinished;
    private CharSequence awakeLabel;
    private final Runnable dismissHoldProgress=new Runnable(){public void run(){
        if(awake==null||dismissHoldFinished||dismissHoldBegan==0)return;
        long elapsed=SystemClock.elapsedRealtime()-dismissHoldBegan;
        if(elapsed>=DISMISS_HOLD_MS){completeDismissHold();return;}
        int tenths=(int)Math.ceil((DISMISS_HOLD_MS-elapsed)/100.0);
        int filled=(int)(elapsed*10/DISMISS_HOLD_MS);
        StringBuilder bar=new StringBuilder(10);for(int n=0;n<10;n++)bar.append(n<filled?'■':'□');
        awake.setText("Hold to stop\n\n"+bar+"\n"+String.format(Locale.getDefault(),"%.1f seconds",tenths/10.0));
        ui.postDelayed(this,40);
    }};
    private final BroadcastReceiver done=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Appearance.CHANGED.equals(i.getAction()))applyPalette();else finish();}};
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 receivers require the signature permission; 33+ also use NOT_EXPORTED.
    @Override public void onCreate(Bundle b){
        setTheme(Appearance.current(this).theme);super.onCreate(b);if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Appearance.current(this).background);
        int minutes=Store.p(this).getInt("snooze",9);
        String time=new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date());
        String title=Store.object(this,"current").optString("title","Time to wake up");
        SpannableString snoozeLabel=snoozeLabel(time,title,minutes);
        snooze=button(snoozeLabel,"SNOOZE",Appearance.current(this).surface,Appearance.current(this).text);snooze.setContentDescription("Snooze for "+minutes+" minutes. "+time+". "+title);
        SpannableString initialAwakeLabel=new SpannableString("I’m up\n\nStart my day\n\nHold 2 seconds to dismiss");size(initialAwakeLabel,"I’m up",44);size(initialAwakeLabel,"Start my day",18);size(initialAwakeLabel,"Hold 2 seconds to dismiss",15);awakeLabel=initialAwakeLabel;
        awake=button(awakeLabel,"",Appearance.current(this).action,Appearance.current(this).onAction);awake.setContentDescription("I’m up. Press and hold for two seconds to dismiss the alarm. Accessibility users can use the Dismiss alarm long-click action.");
        awake.setOnClickListener(v->Toast.makeText(this,"Hold for two seconds to dismiss.",Toast.LENGTH_SHORT).show());
        awake.setOnLongClickListener(v->{dismissHoldBegan=SystemClock.elapsedRealtime()-DISMISS_HOLD_MS;completeDismissHold();return true;});
        awake.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,"Dismiss alarm"));}
        });
        awake.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    dismissHoldBegan=SystemClock.elapsedRealtime();dismissHoldFinished=false;awake.setPressed(true);ui.removeCallbacks(dismissHoldProgress);ui.post(dismissHoldProgress);return true;
                case MotionEvent.ACTION_MOVE:
                    if(event.getX()<0||event.getY()<0||event.getX()>awake.getWidth()||event.getY()>awake.getHeight())cancelDismissHold();return true;
                case MotionEvent.ACTION_UP:
                    if(dismissHoldBegan>0&&SystemClock.elapsedRealtime()-dismissHoldBegan>=DISMISS_HOLD_MS)completeDismissHold();else{cancelDismissHold();awake.performClick();}return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelDismissHold();return true;
                default:return true;
            }
        });
        root.addView(snooze,new LinearLayout.LayoutParams(-1,0,1));root.addView(awake,new LinearLayout.LayoutParams(-1,0,1));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int topInset,bottomInset,left,right;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());topInset=bars.top;bottomInset=bars.bottom;left=bars.left;right=bars.right;}
            else{topInset=insets.getSystemWindowInsetTop();bottomInset=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}
            snooze.setPadding(dp(24)+left,dp(16)+topInset,dp(24)+right,dp(16));awake.setPadding(dp(24)+left,dp(16),dp(24)+right,dp(16)+bottomInset);return insets;
        });
        ui.post(new Runnable(){public void run(){snooze.setText(snoozeLabel(time,title,minutes));ui.postDelayed(this,1000);}});
        setContentView(root);if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        applyPalette();IntentFilter events=new IntentFilter("app.daybreak.RING_DONE");events.addAction(Appearance.CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(done,events,DaybreakApp.INTERNAL_PERMISSION,null,RECEIVER_NOT_EXPORTED);else registerReceiver(done,events,DaybreakApp.INTERNAL_PERMISSION,null);receiverRegistered=true;
    }
    private void applyPalette(){
        Appearance p=Appearance.current(this);root.setBackgroundColor(p.background);tint(snooze,p.surface,p.text);tint(awake,p.action,p.onAction);
        int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int bars=(Appearance.light(p.surface)?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0)|(Appearance.light(p.action)?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().setSystemBarsAppearance((Appearance.light(p.surface)?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS:0)|(Appearance.light(p.action)?WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0),WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        else getWindow().getDecorView().setSystemUiVisibility((getWindow().getDecorView().getSystemUiVisibility()&~mask)|bars);
    }
    private void completeDismissHold(){if(dismissHoldBegan==0||dismissHoldFinished)return;dismissHoldFinished=true;ui.removeCallbacks(dismissHoldProgress);awake.setPressed(false);awake.setText(awakeLabel);act("STOP");}
    private void cancelDismissHold(){ui.removeCallbacks(dismissHoldProgress);dismissHoldBegan=0;if(awake!=null){awake.setPressed(false);if(!dismissHoldFinished)awake.setText(awakeLabel);}}
    private void tint(Button button,int background,int text){
        button.setTextColor(text);GradientDrawable color=new GradientDrawable();color.setColor(background);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((Appearance.current(this).accent&0x00ffffff)|0x33000000),color,null));
    }
    private SpannableString snoozeLabel(String time,String title,int minutes){
        int percent=AlarmService.playbackPercent();String volume=percent<0?"Gradual app volume rise":"App playback · "+percent+"%";
        SpannableString label=new SpannableString("Petal Wake\n\n"+time+"\n"+title+"\n\nSnooze\n"+minutes+" minutes\n"+volume);size(label,"Petal Wake",14);size(label,time,56);size(label,title,18);size(label,"Snooze",38);size(label,minutes+" minutes",16);size(label,volume,13);return label;
    }
    private void size(SpannableString label,String part,int sp){int start=label.toString().indexOf(part);if(start>=0)label.setSpan(new AbsoluteSizeSpan(sp,true),start,start+part.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
    private Button button(CharSequence label,String action,int background,int text){
        Button button=new Button(this);button.setText(label);button.setTextSize(18);button.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));button.setAllCaps(false);button.setTextColor(text);button.setGravity(Gravity.CENTER);button.setPadding(dp(24),dp(16),dp(24),dp(16));button.setMinHeight(0);button.setMinimumHeight(0);button.setStateListAnimator(null);button.setElevation(0);
        GradientDrawable color=new GradientDrawable();color.setColor(background);button.setBackground(new RippleDrawable(ColorStateList.valueOf((Appearance.current(this).accent&0x00ffffff)|0x33000000),color,null));button.setOnClickListener(v->act(action));return button;
    }
    private void act(String action){
        try{startService(new Intent(this,AlarmService.class).setAction(action));}
        catch(RuntimeException failure){Toast.makeText(this,"Alarm action failed. The alarm is still active.",Toast.LENGTH_LONG).show();}
        // Only AlarmService's RING_DONE broadcast closes this screen. Leaving the app,
        // locking the display, or a failed snooze must never be treated as an alarm action.
    }
    @Override public void onBackPressed(){}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);if(receiverRegistered){unregisterReceiver(done);receiverRegistered=false;}super.onDestroy();}
}
