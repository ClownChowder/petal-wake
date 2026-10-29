package app.daybreak;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Durable undo for the destination writes of a move into an empty setup. */
final class MoveRestoreJournal {
    static volatile boolean blocked;
    private static File folder(Context c) { return new File(c.getNoBackupFilesDir(), "move-rollback"); }
    private static File pending(Context c) { return new File(folder(c), "pending"); }

    static void begin(Context c, Set<String> paths) throws Exception {
        recover(c);
        File dir = folder(c);
        if (!dir.mkdirs()) throw new IOException("Could not prepare restore recovery storage.");
        JSONArray entries = new JSONArray();
        JSONObject prefs = new JSONObject();
        // Keep values and their types, including preferences unknown to this app version.
        for (Map.Entry<String, ?> entry : c.getSharedPreferences("daybreak", Context.MODE_PRIVATE).getAll().entrySet()) {
            Object value = entry.getValue();
            String type = value instanceof String ? "string" : value instanceof Integer ? "int"
                    : value instanceof Long ? "long" : value instanceof Float ? "float"
                    : value instanceof Boolean ? "boolean" : "set";
            prefs.put(entry.getKey(), new JSONObject().put("type", type)
                    .put("value", value instanceof Set ? new JSONArray((Set<?>) value) : value));
        }
        for (String path : paths) {
            validPath(path);
            File original = new File(c.getFilesDir(), path);
            String backup = "file-" + entries.length();
            if (original.exists()) copy(original, new File(dir, backup));
            entries.put(new JSONObject().put("path", path).put("backup", backup).put("exists", original.exists()));
        }
        byte[] bytes = PrivateStorage.seal(new JSONObject().put("preferences", prefs)
                .put("files", entries).toString()).getBytes(StandardCharsets.UTF_8);
        AtomicFile file = new AtomicFile(pending(c));
        FileOutputStream out = file.startWrite();
        try { out.write(bytes); file.finishWrite(out); }
        catch (Exception error) { file.failWrite(out); throw error; }
    }

    static void committed(Context c) throws IOException {
        // Rename is the commit point: a surviving pending marker always means rollback.
        File marker = pending(c);
        if (!marker.renameTo(new File(folder(c), "committed")))
            throw new IOException("Could not finish restore safely.");
        // Cleanup is optional after commit; startup can remove any leftovers.
        try { remove(folder(c)); } catch (IOException ignored) { }
    }

    static void recover(Context c) throws Exception {
        File dir = folder(c);
        if (!dir.exists()) { blocked = false; return; }
        AtomicFile marker = new AtomicFile(pending(c));
        if (!pending(c).exists() && !new File(dir, "pending.bak").exists()) {
            remove(dir); blocked = false; return;
        }
        blocked = true;
        JSONObject journal = new JSONObject(PrivateStorage.open(new String(marker.readFully(), StandardCharsets.UTF_8)));
        JSONArray entries = journal.getJSONArray("files");
        // Backups stay immutable until *all* rollback writes commit. Retrying is idempotent.
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String path = entry.getString("path"); validPath(path);
            String backup = entry.getString("backup");
            if (!backup.matches("file-[0-9]+")) throw new IOException("Invalid restore recovery record.");
            File target = new File(c.getFilesDir(), path);
            remove(target);
            if (entry.getBoolean("exists")) copy(new File(dir, backup), target);
        }
        JSONObject prefs = journal.getJSONObject("preferences");
        SharedPreferences.Editor editor = c.getSharedPreferences("daybreak", Context.MODE_PRIVATE).edit().clear();
        for (Iterator<String> keys = prefs.keys(); keys.hasNext();) {
            String key = keys.next(); JSONObject item = prefs.getJSONObject(key);
            switch (item.getString("type")) {
                case "string": editor.putString(key, item.getString("value")); break;
                case "int": editor.putInt(key, item.getInt("value")); break;
                case "long": editor.putLong(key, item.getLong("value")); break;
                case "float": editor.putFloat(key, (float) item.getDouble("value")); break;
                case "boolean": editor.putBoolean(key, item.getBoolean("value")); break;
                case "set":
                    JSONArray array = item.getJSONArray("value"); Set<String> values = new HashSet<>();
                    for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
                    editor.putStringSet(key, values); break;
                default: throw new IOException("Invalid restore preference recovery record.");
            }
        }
        if (!editor.commit()) throw new IOException("Restore recovery could not save settings. Free storage and reopen Rouse to retry.");
        // Treat completed rollback like commit before disposing of immutable backup data.
        committed(c);
        blocked = false;
    }

    private static void validPath(String path) throws IOException {
        if (!path.matches("calendar(?:-[a-f0-9-]{36})?\\.ics(?:\\.bak|\\.new)?")
                && !path.equals("custom-sounds") && !path.equals("orchestra-pack"))
            throw new IOException("Invalid restore recovery path.");
    }
    private static void copy(File source, File target) throws IOException {
        if (Files.isSymbolicLink(source.toPath())) throw new IOException("Unexpected link in restore storage.");
        if (source.isDirectory()) {
            if (!target.mkdirs()) throw new IOException("Could not prepare restore backup.");
            File[] children = source.listFiles(); if (children == null) throw new IOException("Could not read restore backup.");
            for (File child : children) copy(child, new File(target, child.getName()));
        } else {
            try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[32768]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.getFD().sync();
            }
        }
    }
    private static void remove(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles(); if (children == null) throw new IOException("Could not read restore storage.");
            for (File child : children) remove(child);
        }
        if (!file.delete()) throw new IOException("Could not clean restore storage. Free storage and reopen Rouse to retry.");
    }
    private MoveRestoreJournal() { }
}
