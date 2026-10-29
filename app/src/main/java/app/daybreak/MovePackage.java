package app.daybreak;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.*;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Password-encrypted, phone-independent transfer of Rouse's user data. */
public final class MovePackage {
    private static final byte[] MAGIC="ROUSEM2\n".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS=310_000,MAX_JSON=32_000_000;
    private static final long MAX_ARCHIVE=675_000_000L,MAX_AUDIO=100_000_000L,MAX_AUDIO_TOTAL=500_000_000L,MAX_PACK_FILE=4_000_000L;
    private static final String DATA="data.json";

    public static void exportTo(Context c,OutputStream out,char[] password)throws Exception{
        checkPassword(password);
        JSONObject data=snapshot(c);
        JSONArray sounds=data.getJSONArray("customSounds");
        byte[] salt=new byte[16],iv=new byte[16];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        out.write(MAGIC);out.write(salt);out.write(iv);
        Keys keys=keys(password,salt);Mac mac;Cipher cipher;
        try{mac=mac(keys.mac);mac.update(MAGIC);mac.update(salt);mac.update(iv);cipher=cipher(Cipher.ENCRYPT_MODE,keys.aes,iv);}
        finally{keys.clear();}
        try(ZipOutputStream zip=new ZipOutputStream(new CipherOutputStream(new MacOutputStream(new NonClosingOutputStream(out),mac),cipher))){
            // Audio is already compressed; avoid long CPU work during a phone move.
            zip.setLevel(Deflater.NO_COMPRESSION);
            byte[] json=data.toString().getBytes(StandardCharsets.UTF_8);
            if(json.length>MAX_JSON)throw new IOException("Calendar data is too large for a move package.");
            zip.putNextEntry(new ZipEntry(DATA));zip.write(json);zip.closeEntry();
            long audioTotal=0;
            for(int i=0;i<sounds.length();i++){
                JSONObject row=sounds.getJSONObject(i);String id=row.getString("id");
                zip.putNextEntry(new ZipEntry(audioName(id)));
                try(InputStream source=CustomSounds.transferAudio(c,row)){long size=copy(source,zip,MAX_AUDIO);if(size==0)throw new IOException("A personal sound is empty.");audioTotal+=size;}
                zip.closeEntry();
                if(audioTotal>MAX_AUDIO_TOTAL)throw new IOException("Personal audio exceeds the 500 MB transfer limit.");
            }
            if(data.getBoolean("soundPack"))for(int i=5;i<=32;i++){
                String name=packName(i);zip.putNextEntry(new ZipEntry("pack/"+name));
                try(InputStream source=new FileInputStream(SoundPacks.file(c,name))){copy(source,zip,MAX_PACK_FILE);}
                zip.closeEntry();
            }
        }
        out.write(mac.doFinal());out.flush();
    }

    static volatile boolean restoring;

    public static synchronized void importFrom(Context c,InputStream in,char[] password)throws Exception{
        checkPassword(password);
        if (MoveRestoreJournal.blocked) throw new IOException("Restore recovery is pending. Reopen Rouse to retry.");
        if(!empty(c))throw new IOException("Restore on a new Rouse setup. This phone already has calendars, personal alarms, or personal sounds.");
        File encrypted=new File(c.getCacheDir(),"rouse-move-"+UUID.randomUUID()+".enc");
        File archive=new File(c.getCacheDir(),"rouse-move-"+UUID.randomUUID()+".zip");
        File stage=new File(c.getCacheDir(),"rouse-move-"+UUID.randomUUID());
        try{
            byte[] magic=readExact(in,MAGIC.length);
            if(!Arrays.equals(magic,MAGIC))throw new IOException("Choose a Rouse move package.");
            byte[] salt=readExact(in,16),iv=readExact(in,16);
            try(FileOutputStream target=new FileOutputStream(encrypted)){copy(in,target,MAX_ARCHIVE+32);target.getFD().sync();}
            long length=encrypted.length();if(length<=32)throw new IOException("The move package is incomplete.");
            Keys keys=keys(password,salt);Mac mac;Cipher cipher;
            try{
            mac=mac(keys.mac);mac.update(MAGIC);mac.update(salt);mac.update(iv);
            byte[] actual;
            try(FileInputStream source=new FileInputStream(encrypted)){
                byte[] buffer=new byte[32768];long remaining=length-32;int n;
                while(remaining>0){n=source.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("The move package is incomplete.");mac.update(buffer,0,n);remaining-=n;}
                actual=readExact(source,32);
            }
            if(!MessageDigest.isEqual(mac.doFinal(),actual))throw new IOException("The package password is wrong, or the package is damaged.");
            cipher=cipher(Cipher.DECRYPT_MODE,keys.aes,iv);
            }finally{keys.clear();}
            try(InputStream source=new FileInputStream(encrypted);
                InputStream decrypted=new CipherInputStream(new BoundedInputStream(source,length-32),cipher);
                FileOutputStream target=new FileOutputStream(archive)){
                copy(decrypted,target,MAX_ARCHIVE);target.getFD().sync();
            }
            if(!stage.mkdirs())throw new IOException("Could not prepare private restore storage.");
            Prepared prepared=prepare(c,archive,stage);
            if(!empty(c))throw new IOException("Rouse data changed while the package was being checked. Restore was cancelled.");
            synchronized (Scheduler.class) {
                if (!empty(c)) throw new IOException("Rouse data changed while the package was being checked. Restore was cancelled.");
                restoring = true;
                try {
                    Set<String> paths = new LinkedHashSet<>();
                    if (!prepared.data.getString("source").isEmpty()) addCalendarPaths(paths, "calendar.ics");
                    JSONArray caches = prepared.data.getJSONArray("extraCaches");
                    for (int i = 0; i < caches.length(); i++)
                        addCalendarPaths(paths, "calendar-" + caches.getJSONObject(i).getString("id") + ".ics");
                    if (prepared.sounds.length() > 0) paths.add("custom-sounds");
                    if (prepared.data.getBoolean("soundPack") && !SoundPacks.installed(c)) paths.add("orchestra-pack");
                    MoveRestoreJournal.begin(c, paths);
                    try {
                        apply(c, prepared, stage);
                        MoveRestoreJournal.committed(c);
                    } catch (Exception failure) {
                        try { MoveRestoreJournal.recover(c); }
                        catch (Exception recovery) {
                            failure.addSuppressed(recovery);
                            throw new IOException("Restore recovery is pending. Free storage and reopen Rouse; saved recovery data is kept.", failure);
                        }
                        throw new IOException("Restore could not finish. Previous settings were restored; retry the original move package.", failure);
                    }
                } finally { restoring = false; }
                // OS side effects happen only after destination data commits.
                try {
                    Appearance.syncLauncher(c);
                    AudioManager audio = c.getSystemService(AudioManager.class);
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    int min = Build.VERSION.SDK_INT >= 28 ? audio.getStreamMinVolume(AudioManager.STREAM_ALARM) : 0;
                    int target = Math.max(min, Math.min(max, Math.round(max * prepared.data.getInt("phoneAlarmPercent") / 100f)));
                    try { audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0); }
                    catch (RuntimeException unavailable) { /* User checks Android alarm volume after restore. */ }
                    Sync.run(c, false);
                    Readiness.update(c);
                } catch (Exception unavailable) {
                    Store.error(c, "Move data restored. Reopen Rouse and check alarm readiness and phone alarm volume.");
                }
            }
        }finally{deleteTree(stage);archive.delete();encrypted.delete();}
    }

    private static void addCalendarPaths(Set<String> paths, String name) {
        paths.add(name); paths.add(name + ".bak"); paths.add(name + ".new");
    }

    /** Clear abandoned plaintext staging after a crash before any user action. */
    static void clearAbandoned(Context c){
        File[] files=c.getCacheDir().listFiles();if(files==null)return;
        for(File file:files)if(file.getName().startsWith("rouse-move-"))deleteTree(file);
    }

    private static JSONObject snapshot(Context c)throws Exception{
        SharedPreferences p=Store.p(c);JSONArray extra=CalendarSources.extras(c),caches=new JSONArray();
        if(extra.length()>5)throw new IOException("Too many calendars for a move package.");
        for(int i=0;i<extra.length();i++){
            JSONObject row=extra.getJSONObject(i);caches.put(new JSONObject().put("id",row.getString("id")).put("data",CalendarSources.data(c,row.getString("id"))));
        }
        AudioManager audio=c.getSystemService(AudioManager.class);
        int max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),level=audio.getStreamVolume(AudioManager.STREAM_ALARM);
        int percent=max>0?Math.round(level*100f/max):100;
        JSONObject settings=new JSONObject(SettingsTransfer.exportJson(c));
        settings.getJSONObject("settings").put("sound",SoundLibrary.playable(c));
        return new JSONObject().put("format","Rouse move").put("schema",1).put("settings",settings)
            .put("source",p.getString("source","")).put("primaryUrl",Store.url(c)).put("primaryName",p.getString("primaryName","Work calendar"))
            .put("primaryCache",Store.cache(c)).put("extraCalendars",new JSONArray(extra.toString())).put("extraCaches",caches)
            .put("manual",futureManual(Store.object(c,"manual"))).put("skipped",Store.object(c,"skipped"))
            .put("history",AlarmHistory.entries(c)).put("customSounds",new JSONArray(CustomSounds.transferRows(c).toString()))
            .put("soundPack",SoundPacks.installed(c)).put("phoneAlarmPercent",percent)
            .put("previousPlayback",Store.object(c,"previousPlayback"))
            .put("workAlarmsWereEnabled",p.getBoolean("enabled",false));
    }

    private static Prepared prepare(Context c,File archive,File stage)throws Exception{
        try(ZipFile zip=new ZipFile(archive)){
            Set<String> names=new HashSet<>();long total=0;Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();String name=entry.getName();
                if(entry.isDirectory()||!names.add(name)||!validName(name))throw new IOException("The package contains an unexpected file.");
                long size=entry.getSize();if(size<0||size>MAX_JSON&&DATA.equals(name)||size>MAX_AUDIO&&name.startsWith("audio/")||size>MAX_PACK_FILE&&name.startsWith("pack/"))throw new IOException("A package entry is too large.");
                total+=size;if(total>MAX_ARCHIVE)throw new IOException("The package is too large.");
            }
            ZipEntry dataEntry=zip.getEntry(DATA);if(dataEntry==null)throw new IOException("The package is missing its data.");
            String json;
            try(InputStream stream=zip.getInputStream(dataEntry);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                copy(stream,bytes,MAX_JSON);json=bytes.toString(StandardCharsets.UTF_8.name());
            }
            JSONObject data=new JSONObject(json);
            if(!"Rouse move".equals(data.optString("format"))||data.optInt("schema")!=1)throw new IOException("This Rouse move package version is not supported.");
            SettingsTransfer.validateJson(c,data.getJSONObject("settings").toString());
            JSONObject prior=data.optJSONObject("previousPlayback");
            if(data.getJSONObject("settings").getJSONObject("settings").optBoolean("quietPreset",false)
                &&(prior==null||prior.length()==0))throw new IOException("Quiet wake-up return settings are missing.");
            if(prior!=null&&prior.length()>0){
                int oldVolume=prior.getInt("volume"),oldFade=prior.getInt("fade");
                if(oldVolume<10||oldVolume>100||!(oldFade==0||oldFade==5||oldFade==15||oldFade==30||oldFade==60||oldFade==120))
                    throw new IOException("Invalid saved wake-up preset.");
            }
            String source=data.getString("source"),url=data.getString("primaryUrl"),cache=data.getString("primaryCache");
            if(!source.isEmpty()&&!"Shared calendar link".equals(source)&&!"Imported calendar file".equals(source))throw new IOException("Invalid calendar source.");
            if("Shared calendar link".equals(source))validateUrl(url);else if(!url.isEmpty())throw new IOException("Unexpected calendar link.");
            if(!cache.isEmpty()){if(cache.getBytes(StandardCharsets.UTF_8).length>4_000_000)throw new IOException("Calendar snapshot is too large.");validateCalendar(cache);}
            if("Imported calendar file".equals(source)&&cache.isEmpty())throw new IOException("Imported calendar snapshot is missing.");
            if(source.isEmpty()&&(!url.isEmpty()||!cache.isEmpty()))throw new IOException("Unexpected calendar snapshot.");
            String primaryName=data.getString("primaryName");if(primaryName.isEmpty()||primaryName.length()>60)throw new IOException("Invalid calendar name.");
            JSONArray extras=validateCalendars(data.getJSONArray("extraCalendars"),data.getJSONArray("extraCaches"));
            if(source.isEmpty()&&extras.length()>0)throw new IOException("Additional calendars need a first calendar.");
            JSONObject manual=validateManual(data.getJSONObject("manual")),skipped=validateSkipped(data.getJSONObject("skipped"));
            JSONArray history=validateHistory(data.getJSONArray("history")),sounds=validateSounds(data.getJSONArray("customSounds"));
            String selected=data.getJSONObject("settings").getJSONObject("settings").optString("sound","");
            if(CustomSounds.id(selected)){boolean found=false;for(int i=0;i<sounds.length();i++)if(selected.equals(sounds.getJSONObject(i).getString("id")))found=true;if(!found)throw new IOException("The selected personal sound is missing.");}
            boolean pack=data.getBoolean("soundPack");int percent=data.getInt("phoneAlarmPercent");
            if(percent<0||percent>100)throw new IOException("Invalid phone alarm volume.");
            Set<String> expected=new HashSet<>();expected.add(DATA);
            File audioDir=new File(stage,"audio"),packDir=new File(stage,"pack");
            if(sounds.length()>0&&!audioDir.mkdir())throw new IOException("Could not prepare audio storage.");
            long audioTotal=0;
            for(int i=0;i<sounds.length();i++){
                String id=sounds.getJSONObject(i).getString("id"),name=audioName(id);expected.add(name);
                ZipEntry entry=zip.getEntry(name);if(entry==null)throw new IOException("A personal sound is missing.");
                File target=new File(audioDir,id.substring(7)+".audio");
                try(InputStream input=zip.getInputStream(entry);FileOutputStream output=new FileOutputStream(target)){long size=copy(input,output,MAX_AUDIO);if(size==0)throw new IOException("A personal sound is empty.");audioTotal+=size;output.getFD().sync();}
                validateAudio(target);
                if(audioTotal>MAX_AUDIO_TOTAL)throw new IOException("Personal audio exceeds the transfer limit.");
            }
            if(pack){
                if(!packDir.mkdir())throw new IOException("Could not prepare orchestra storage.");
                for(int i=5;i<=32;i++){
                    String name=packName(i),entryName="pack/"+name;expected.add(entryName);ZipEntry entry=zip.getEntry(entryName);
                    if(entry==null)throw new IOException("The orchestra pack is incomplete.");
                    try(InputStream input=zip.getInputStream(entry);FileOutputStream output=new FileOutputStream(new File(packDir,name))){copy(input,output,MAX_PACK_FILE);output.getFD().sync();}
                }
            }
            if(!expected.equals(names))throw new IOException("The package has extra or missing files.");
            return new Prepared(data,extras,manual,skipped,history,sounds);
        }
    }

    private static void apply(Context c,Prepared item,File stage)throws Exception{
        JSONObject data=item.data;String source=data.getString("source");
        if(!source.isEmpty())Store.cache(c,data.getString("primaryCache"));
        JSONArray caches=data.getJSONArray("extraCaches");
        for(int i=0;i<caches.length();i++){JSONObject row=caches.getJSONObject(i);CalendarSources.data(c,row.getString("id"),row.getString("data"));}
        if(data.getBoolean("soundPack"))SoundPacks.restoreFrom(c,new File(stage,"pack"));
        CustomSounds.restoreCopies(c,item.sounds,new File(stage,"audio"));
        Store.put(c,"extraCalendars",item.extras);Store.setUrl(c,data.getString("primaryUrl"));
        Store.put(c,"manual",item.manual);Store.put(c,"skipped",item.skipped);Store.put(c,"alarmHistory",item.history);
        SharedPreferences.Editor edit=Store.p(c).edit().putString("source",source).putString("primaryName",data.getString("primaryName"))
            .putLong("lastSync",source.isEmpty()?0:System.currentTimeMillis()).putBoolean("enabled",false).putString("error","");
        if(!edit.commit())throw new IOException("Could not save restored calendar state.");
        SettingsTransfer.importJson(c,data.getJSONObject("settings").toString(),false);
        JSONObject savedSettings=data.getJSONObject("settings").getJSONObject("settings");
        if(savedSettings.optBoolean("quietPreset",false)){
            JSONObject prior=data.optJSONObject("previousPlayback");
            if(prior!=null&&prior.length()>0)
                if (!Store.p(c).edit().putBoolean("quietPreset",true)
                    .putInt("volume",savedSettings.getInt("volume"))
                    .putString("previousPlayback",prior.toString()).commit())
                    throw new IOException("Could not save restored wake-up settings.");
        }

    }

    private static JSONArray validateCalendars(JSONArray rows,JSONArray caches)throws Exception{
        if(rows.length()>5||caches.length()!=rows.length())throw new IOException("Invalid connected calendars.");
        Set<String> ids=new HashSet<>(),cacheIds=new HashSet<>();JSONArray clean=new JSONArray();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String id=row.getString("id"),name=row.getString("name"),url=row.getString("url"),filter=row.optString("filter","");
            if(!id.matches("[a-f0-9-]{36}")||!ids.add(id)||name.isEmpty()||name.length()>60||filter.length()>120)throw new IOException("Invalid connected calendar.");
            validateUrl(url);int lead=row.getInt("lead"),minutes=row.getInt("allDayMinutes");
            if(lead<0||lead>1440||minutes<0||minutes>1439)throw new IOException("Invalid calendar rules.");
            clean.put(new JSONObject().put("id",id).put("name",name).put("url",url).put("lead",lead).put("filter",filter)
                .put("allDayEnabled",row.optBoolean("allDayEnabled",false)).put("allDayMinutes",minutes).put("lastSync",row.optLong("lastSync",0)).put("error",""));
        }
        for(int i=0;i<caches.length();i++){
            JSONObject row=caches.getJSONObject(i);String id=row.getString("id"),body=row.getString("data");
            if(!ids.contains(id)||!cacheIds.add(id)||body.getBytes(StandardCharsets.UTF_8).length>4_000_000)throw new IOException("Invalid calendar snapshot.");
            if(!body.isEmpty())validateCalendar(body);
        }
        if(!ids.equals(cacheIds))throw new IOException("A calendar snapshot is missing.");
        return clean;
    }
    private static void validateCalendar(String body)throws IOException{try{Sync.validate(body);}catch(Exception invalid){throw new IOException("A calendar snapshot cannot be read.");}}
    private static void validateAudio(File file)throws IOException{
        MediaPlayer player=new MediaPlayer();
        try{player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(file.getAbsolutePath());player.prepare();if(player.getDuration()<1000)throw new IOException("A personal sound is too short.");
        }catch(Exception invalid){throw new IOException("A personal sound in the package cannot play.");}
        finally{player.release();}
    }
    private static void validateUrl(String value)throws Exception{
        java.net.URL url=new java.net.URL(value);
        if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||url.getHost().isEmpty()||value.length()>4096)throw new IOException("Invalid private calendar link.");
    }
    private static JSONObject futureManual(JSONObject source)throws Exception{
        JSONObject result=new JSONObject();long now=System.currentTimeMillis();
        for(Iterator<String> keys=source.keys();keys.hasNext();){String id=keys.next();JSONObject alarm=source.getJSONObject(id);if(alarm.optLong("at")>now)result.put(id,alarm);}
        return result;
    }
    private static JSONObject validateManual(JSONObject source)throws Exception{
        if(source.length()>32)throw new IOException("Too many personal alarms.");JSONObject result=new JSONObject();long now=System.currentTimeMillis();
        for(Iterator<String> keys=source.keys();keys.hasNext();){
            String id=keys.next();JSONObject row=source.getJSONObject(id);String title=row.getString("title");long at=row.getLong("at");
            if(!id.matches("manual:[a-f0-9-]{36}")||!id.equals(row.optString("id"))||!"manual".equals(row.optString("kind"))||title.isEmpty()||title.length()>120||at<=0)throw new IOException("Invalid personal alarm.");
            if(at>now)result.put(id,new JSONObject().put("id",id).put("kind","manual").put("title",title).put("at",at));
        }
        return result;
    }
    private static JSONObject validateSkipped(JSONObject source)throws Exception{
        if(source.length()>128)throw new IOException("Too many skipped alarms.");JSONObject result=new JSONObject();
        for(Iterator<String> keys=source.keys();keys.hasNext();){String id=keys.next();if(!id.startsWith("shift:")||id.length()>512||!(source.get(id) instanceof Boolean))throw new IOException("Invalid skipped alarm.");if(source.getBoolean(id))result.put(id,true);}
        return result;
    }
    private static JSONArray validateHistory(JSONArray source)throws Exception{
        if(source.length()>100)throw new IOException("Too much alarm history.");JSONArray result=new JSONArray();
        for(int i=0;i<source.length();i++){JSONObject row=source.getJSONObject(i);String event=row.getString("event"),kind=row.getString("kind");
            if(event.length()>80||kind.length()>40||row.getLong("when")<0||row.getLong("at")<0||row.optLong("to",0)<0)throw new IOException("Invalid alarm history.");
            result.put(new JSONObject().put("event",event).put("kind",kind).put("when",row.getLong("when")).put("at",row.getLong("at")).put("to",row.optLong("to",0)));
        }return result;
    }
    private static JSONArray validateSounds(JSONArray source)throws Exception{
        if(source.length()>40)throw new IOException("Too many personal sounds.");Set<String> ids=new HashSet<>();JSONArray result=new JSONArray();
        for(int i=0;i<source.length();i++){JSONObject row=source.getJSONObject(i);String id=row.getString("id"),title=row.getString("title"),genre=row.getString("genre");
            if(!CustomSounds.id(id)||!ids.add(id)||title.isEmpty()||title.length()>80||!Arrays.asList(CustomSounds.GENRES).contains(genre)||row.getInt("duration")<1)throw new IOException("Invalid personal sound.");
            result.put(new JSONObject().put("id",id).put("title",title).put("genre",genre).put("mode","copy").put("duration",row.getInt("duration")));
        }return result;
    }
    private static boolean validName(String name){return DATA.equals(name)||name.matches("audio/[a-f0-9-]{36}\\.audio")||name.matches("pack/orchestra_[0-9]{2}\\.m4a");}
    private static String audioName(String id){if(!CustomSounds.id(id))throw new IllegalArgumentException("Invalid personal sound.");return "audio/"+id.substring(7)+".audio";}
    private static String packName(int number){return String.format(Locale.ROOT,"orchestra_%02d.m4a",number);}
    private static boolean empty(Context c){return !CalendarSources.hasPrimary(c)&&Store.url(c).isEmpty()&&CalendarSources.extras(c).length()==0&&Store.object(c,"manual").length()==0&&CustomSounds.transferRows(c).length()==0&&Store.object(c,"testAlarms").length()==0&&Store.object(c,"snoozes").length()==0&&Store.object(c,"ringing").length()==0&&Store.object(c,"inFlight").length()==0&&Store.object(c,"scheduled").length()==0;}
    private static void checkPassword(char[] password){if(password==null||password.length<12)throw new IllegalArgumentException("Use a move-package password of at least 12 characters.");}
    private static Keys keys(char[] password,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,512);byte[] derived;
        try{derived=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
        try{return new Keys(Arrays.copyOfRange(derived,0,32),Arrays.copyOfRange(derived,32,64));}
        finally{Arrays.fill(derived,(byte)0);}
    }
    private static Cipher cipher(int mode,byte[] key,byte[] iv)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/CTR/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return cipher;
    }
    private static Mac mac(byte[] key)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac;}
    private static final class Keys{
        final byte[] aes,mac;Keys(byte[] aes,byte[] mac){this.aes=aes;this.mac=mac;}
        void clear(){Arrays.fill(aes,(byte)0);Arrays.fill(mac,(byte)0);}
    }
    private static final class NonClosingOutputStream extends FilterOutputStream{
        NonClosingOutputStream(OutputStream out){super(out);}
        @Override public void close()throws IOException{flush();}
    }
    private static final class MacOutputStream extends FilterOutputStream{
        private final Mac mac;MacOutputStream(OutputStream out,Mac mac){super(out);this.mac=mac;}
        @Override public void write(int value)throws IOException{mac.update((byte)value);out.write(value);}
        @Override public void write(byte[] data,int offset,int length)throws IOException{mac.update(data,offset,length);out.write(data,offset,length);}
    }
    private static byte[] readExact(InputStream in,int size)throws IOException{byte[] result=new byte[size];int offset=0,n;while(offset<size&&(n=in.read(result,offset,size-offset))!=-1)offset+=n;if(offset!=size)throw new IOException("The move package is incomplete.");return result;}
    private static long copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] buffer=new byte[32768];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("The move package exceeds its size limit.");out.write(buffer,0,n);}return total;}
    private static void deleteTree(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);}file.delete();}
    private static final class Prepared {
        final JSONObject data,manual,skipped;final JSONArray extras,history,sounds;
        Prepared(JSONObject data,JSONArray extras,JSONObject manual,JSONObject skipped,JSONArray history,JSONArray sounds){this.data=data;this.extras=extras;this.manual=manual;this.skipped=skipped;this.history=history;this.sounds=sounds;}
    }
    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;
        BoundedInputStream(InputStream in,long length){super(in);remaining=length;}
        @Override public int read()throws IOException{if(remaining==0)return -1;int n=super.read();if(n!=-1)remaining--;return n;}
        @Override public int read(byte[] b,int off,int len)throws IOException{if(remaining==0)return -1;int n=super.read(b,off,(int)Math.min(len,remaining));if(n>0)remaining-=n;return n;}
    }
    private MovePackage(){}
}
