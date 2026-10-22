package app.daybreak;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import java.util.function.Consumer;

/** A fresh Android authentication for one pending action; no reusable unlock state. */
final class WorkAlarmAuthorization {
    private static final int CREDENTIAL_REQUEST=41;
    private final Activity activity;
    private final Runnable changed;
    private final Consumer<String> message;
    private CancellationSignal cancellation;
    private Runnable pending;
    private int generation;
    private boolean destroyed;
    private String failureMessage="Work alarms remain on.";

    WorkAlarmAuthorization(Activity activity,Runnable changed,Consumer<String> message){
        this.activity=activity;this.changed=changed;this.message=message;
    }
    boolean isPending(){return pending!=null;}
    void request(String title,String description,Runnable action){
        request(title,description,action,"Work alarms remain on.");
    }
    void request(String title,String description,Runnable action,String onFailure){
        if(destroyed||isPending())return;
        failureMessage=onFailure;
        KeyguardManager keyguard=activity.getSystemService(KeyguardManager.class);
        if(keyguard==null||!keyguard.isDeviceSecure()){
            message.accept("Set up a screen lock in Android Settings first. "+failureMessage);changed.run();return;
        }
        pending=action;int token=++generation;changed.run();
        try{
            if(Build.VERSION.SDK_INT>=29)prompt(title,description,token);
            else{
                Intent intent=keyguard.createConfirmDeviceCredentialIntent(title,description);
                if(intent==null){finish(token,false,"Authentication is unavailable. "+failureMessage);return;}
                activity.startActivityForResult(intent,CREDENTIAL_REQUEST);
            }
        }catch(RuntimeException error){finish(token,false,"Could not open Android authentication. "+failureMessage);}
    }
    @android.annotation.TargetApi(29)
    private void prompt(String title,String description,int token){
        BiometricPrompt.Builder builder=new BiometricPrompt.Builder(activity)
            .setTitle(title).setSubtitle("Confirm with your phone’s security")
            .setDescription(description).setConfirmationRequired(true);
        if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        else builder.setDeviceCredentialAllowed(true);
        cancellation=new CancellationSignal();
        builder.build().authenticate(cancellation,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){finish(token,true,null);}
            @Override public void onAuthenticationError(int code,CharSequence text){finish(token,false,"Authentication canceled or unavailable. "+failureMessage);}
            // A failed recognition leaves the system prompt open for another try or the screen lock.
        });
    }
    boolean onActivityResult(int request,int result){
        if(request!=CREDENTIAL_REQUEST)return false;
        finish(generation,result==Activity.RESULT_OK,result==Activity.RESULT_OK?null:"Authentication canceled. "+failureMessage);return true;
    }
    private void finish(int token,boolean success,String text){
        if(destroyed||token!=generation||pending==null)return;
        Runnable action=pending;pending=null;cancellation=null;++generation;
        if(activity.isFinishing()||activity.isDestroyed())return;
        changed.run();
        if(success)action.run();else if(text!=null)message.accept(text);
    }
    void destroy(){
        destroyed=true;pending=null;++generation;
        if(cancellation!=null){cancellation.cancel();cancellation=null;}
    }
}
