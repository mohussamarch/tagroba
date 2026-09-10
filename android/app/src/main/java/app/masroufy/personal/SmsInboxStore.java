package app.masroufy.personal;

import android.content.Context;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Private app database. Acknowledged IDs remain, but their message text is removed. */
public class SmsInboxStore extends SQLiteOpenHelper {
    public static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public SmsInboxStore(Context context) {
        super(context, "sms-inbox.db", null, 1);
        prefs = context.getSharedPreferences("sms-inbox-settings", Context.MODE_PRIVATE);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages(owner TEXT NOT NULL,id TEXT NOT NULL,sender TEXT NOT NULL,body TEXT NOT NULL,received INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(owner,id))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    public String owner() { return prefs.getString("owner", ""); }
    public boolean enabled(String uid) { return uid.equals(owner()) && prefs.getBoolean("enabled", false); }
    public Set<String> senders() { return new HashSet<>(prefs.getStringSet("senders", new HashSet<>())); }
    public boolean accepts(String sender) {
        if (sender == null) return false;
        for (String item : senders()) if (item.equalsIgnoreCase(sender.trim())) return true;
        return false;
    }
    public void configure(String uid, Set<String> senders) {
        boolean changedOwner = !uid.equals(owner());
        var edit = prefs.edit().putString("owner", uid).putStringSet("senders", senders).putBoolean("enabled", true);
        // First enable starts now; manual import remains available for older messages.
        if (changedOwner || !enabled(uid) || !prefs.contains("cursorDate")) edit.putLong("cursorDate", System.currentTimeMillis()).putLong("cursorId", -1);
        if (!edit.commit()) throw new IllegalStateException("settings write failed");
    }
    public void disable(String uid) {
        if (uid.equals(owner()) && !prefs.edit().putBoolean("enabled", false).commit()) throw new IllegalStateException("settings write failed");
    }
    public long cursorDate() { return prefs.getLong("cursorDate", System.currentTimeMillis()); }
    public long cursorId() { return prefs.getLong("cursorId", -1); }
    public void checkpoint(long date, long id) {
        if (!prefs.edit().putLong("cursorDate", date).putLong("cursorId", id).commit()) throw new IllegalStateException("checkpoint failed");
    }
    public void enqueue(String uid, String sender, long timestamp, String originalBody) {
        if (!enabled(uid) || !accepts(sender)) return;
        String body = SmsSafety.sanitize(originalBody);
        if (body == null) return;
        ContentValues row = new ContentValues();
        row.put("owner", uid); row.put("id", SmsSafety.key(sender, timestamp, body));
        row.put("sender", sender); row.put("body", body); row.put("received", timestamp);
        getWritableDatabase().insertWithOnConflict("messages", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public JSObject snapshot(String uid, boolean permission, boolean more) {
        JSObject result = new JSObject();
        result.put("enabled", enabled(uid)); result.put("permission", permission); result.put("more", more);
        JSArray names = new JSArray();
        if (uid.equals(owner())) for (String sender : senders()) names.put(sender);
        result.put("senders", names);
        JSArray items = new JSArray();
        try (Cursor rows = getReadableDatabase().query("messages", new String[]{"id","sender","body","received"},
                "owner=? AND done=0", new String[]{uid}, null, null, "received ASC,id ASC", "200")) {
            while (rows.moveToNext()) {
                JSObject item = new JSObject(); item.put("id", rows.getString(0)); item.put("sender", rows.getString(1));
                item.put("body", rows.getString(2)); item.put("receivedAt", Instant.ofEpochMilli(rows.getLong(3)).toString()); items.put(item);
            }
        }
        try (Cursor count = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages WHERE owner=? AND done=0", new String[]{uid})) {
            count.moveToFirst(); result.put("count", count.getInt(0));
        }
        result.put("messages", items); return result;
    }
    public void acknowledge(String uid, JSArray ids) throws Exception {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues values = new ContentValues(); values.put("done", 1); values.put("body", "");
            for (int i=0;i<ids.length();i++) db.update("messages", values, "owner=? AND id=?", new String[]{uid, ids.getString(i)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
