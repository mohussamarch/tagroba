package app.masroufy.device

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxState
import java.time.Instant

/**
 * صندوق رسايل البنك على الجهاز — نقل `SmsInboxStore.java`: قاعدة صغيرة خاصة بالتطبيق + إعدادات (مين صاحب الصندوق ·
 * المرسلين · مؤشر آخر رسالة اتقرت). الرسالة بتعدّي على [SmsSafety] **قبل** ما تتحفظ، والرسالة اللي خلصت بيتمسح نصها
 * ويفضل معرّفها بس (عشان ما تتضافش تاني). مش متزامن مع فايربيز — على الجهاز بس.
 * (اتشال من النقل: `upgradeFilter` — ترقيع لنسخ قديمة من التطبيق الحالي، والتطبيق الجديد بيبدأ نضيف.)
 */
internal class SmsInboxStore(context: Context) : SQLiteOpenHelper(context, "sms-inbox.db", null, 1) {
    private val prefs = context.getSharedPreferences("sms-inbox-settings", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(owner TEXT NOT NULL,id TEXT NOT NULL,sender TEXT NOT NULL,body TEXT NOT NULL,received INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(owner,id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun owner(): String = prefs.getString("owner", "") ?: ""

    fun enabled(uid: String) = uid == owner() && prefs.getBoolean("enabled", false)

    fun senders(): Set<String> = prefs.getStringSet("senders", emptySet())?.toSet() ?: emptySet()

    fun accepts(sender: String?): Boolean = sender != null && senders().any { it.equals(sender.trim(), ignoreCase = true) }

    /** أول تفعيل بيبدأ من دلوقتي — الرسايل الأقدم ليها القراية بالطلب. */
    fun configure(uid: String, senders: Set<String>) {
        val changedOwner = uid != owner()
        val edit = prefs.edit().putString("owner", uid).putStringSet("senders", senders).putBoolean("enabled", true)
        if (changedOwner || !enabled(uid) || !prefs.contains("cursorDate")) edit.putLong("cursorDate", System.currentTimeMillis()).putLong("cursorId", -1)
        check(edit.commit()) { "settings write failed" }
    }

    fun disable(uid: String) {
        if (uid == owner()) check(prefs.edit().putBoolean("enabled", false).commit()) { "settings write failed" }
    }

    fun cursorDate(): Long = prefs.getLong("cursorDate", System.currentTimeMillis())

    fun cursorId(): Long = prefs.getLong("cursorId", -1)

    fun checkpoint(date: Long, id: Long) = check(prefs.edit().putLong("cursorDate", date).putLong("cursorId", id).commit()) { "checkpoint failed" }

    fun enqueue(uid: String, sender: String, timestamp: Long, originalBody: String) {
        if (!enabled(uid) || !accepts(sender)) return
        val body = SmsSafety.sanitize(originalBody) ?: return
        val row = ContentValues().apply {
            put("owner", uid)
            put("id", SmsSafety.key(sender, timestamp, body))
            put("sender", sender)
            put("body", body)
            put("received", timestamp)
        }
        writableDatabase.insertWithOnConflict("messages", null, row, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun snapshot(uid: String, permission: Boolean, more: Boolean): SmsInboxState {
        val items = mutableListOf<QueuedSms>()
        readableDatabase.query("messages", arrayOf("id", "sender", "body", "received"), "owner=? AND done=0", arrayOf(uid), null, null, "received ASC,id ASC", "200").use { c ->
            while (c.moveToNext()) items += QueuedSms(c.getString(0), c.getString(1), Instant.ofEpochMilli(c.getLong(3)).toString(), c.getString(2))
        }
        val count = readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE owner=? AND done=0", arrayOf(uid)).use { it.moveToFirst(); it.getInt(0) }
        return SmsInboxState(enabled(uid), permission, more, count, if (uid == owner()) senders().toList() else emptyList(), items)
    }

    fun acknowledge(uid: String, ids: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("done", 1)
                put("body", "")
            }
            for (id in ids) db.update("messages", values, "owner=? AND id=?", arrayOf(uid, id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        /** الاستقبال في الخلفية والشاشة بيكتبوا بالدور. */
        val LOCK = Any()
    }
}
