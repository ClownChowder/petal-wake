package app.daybreak;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-bound encryption for calendar links and downloaded calendar contents. */
final class PrivateStorage {
    private static final String ALIAS="petal_wake_calendar_v1";
    private static final String PREFIX="pw1:";
    static boolean encrypted(String value){return value!=null&&value.startsWith(PREFIX);}
    private static synchronized SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    static String seal(String value){
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,combined,0,iv.length);System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
            return PREFIX+Base64.encodeToString(combined,Base64.NO_WRAP);
        }catch(Exception e){throw new IllegalStateException("Secure calendar storage is unavailable. Existing alarms were kept.",e);}
    }
    static String open(String value){
        if(!encrypted(value))return value;
        try{
            byte[] combined=Base64.decode(value.substring(PREFIX.length()),Base64.DEFAULT);
            if(combined.length<28)throw new IllegalArgumentException("Invalid encrypted calendar data.");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,combined,0,12));
            return new String(cipher.doFinal(combined,12,combined.length-12),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Saved calendar data could not be unlocked. Existing alarms were kept.",e);}
    }
    private PrivateStorage(){}
}
