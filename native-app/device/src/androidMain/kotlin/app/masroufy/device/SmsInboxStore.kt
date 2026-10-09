package app.masroufy.device

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey
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

    /**
     * محفظة كل بنك (مرسل) في كل بلد (OVERRIDES §72 — رد المالك ١) — لكل صاحب صندوق. [sender] بعد `smsSenderKey`.
     * **القديم:** النسخة الأولى من الجلسة 31 كانت بتحفظ محفظة واحدة للبلد (`autoTarget|uid|space`) — لو موجودة بتتحول مرة واحدة لربط
     * كل مرسل مفعّل مالوش ربط (ده اختيار المالك نفسه للبلد)، وبعدين بتتمسح. ما بتضيعش وما بتتقريش غلط.
     */
    fun senderWallets(uid: String, spaceId: String): Map<String, String> {
        migrateLegacyTarget(uid, spaceId)
        val prefix = senderPrefix(uid, spaceId)
        return prefs.all.mapNotNull { (key, value) -> if (key.startsWith(prefix) && value is String) key.removePrefix(prefix) to value else null }.toMap()
    }

    fun setSenderWallet(uid: String, spaceId: String, sender: String, walletId: String?) {
        migrateLegacyTarget(uid, spaceId)
        val key = senderPrefix(uid, spaceId) + sender
        val edit = prefs.edit()
        if (walletId == null) edit.remove(key) else edit.putString(key, walletId)
        check(edit.commit()) { "settings write failed" }
    }

    private fun senderPrefix(uid: String, spaceId: String) = "senderWallet|$uid|$spaceId|"

    /**
     * §77-A «وضع التعلّم»: بصمات أشكال الرسايل اللي المالك أكّدها، لكل صاحب صندوق ولكل بلد ولكل مرسل (بعد `smsSenderKey`) —
     * مفتاح لكل بصمة «smsShape|uid|space|sender|<بصمة>». **بصمة بس** (ولا حرف من نص رسالة)، على الجهاز بس زي محفظة كل بنك.
     */
    fun learnedShapes(uid: String, spaceId: String): Map<String, Set<String>> {
        val prefix = shapePrefix(uid, spaceId)
        return prefs.all.keys.filter { it.startsWith(prefix) && prefs.getBoolean(it, false) }
            .map { it.removePrefix(prefix) }
            .groupBy({ it.substringBeforeLast('|') }, { it.substringAfterLast('|') })
            .mapValues { it.value.toSet() }
    }

    fun learnShapes(uid: String, spaceId: String, sender: String, keys: Set<String>) {
        if (keys.isEmpty()) return
        val edit = prefs.edit()
        for (key in keys) edit.putBoolean(shapePrefix(uid, spaceId) + sender + "|" + key, true)
        check(edit.commit()) { "settings write failed" }
    }

    fun forgetShapes(uid: String, spaceId: String, sender: String) {
        val prefix = shapePrefix(uid, spaceId) + sender + "|"
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) && '|' !in it.removePrefix(prefix) }.forEach { edit.remove(it) }
        check(edit.commit()) { "settings write failed" }
    }

    private fun shapePrefix(uid: String, spaceId: String) = "smsShape|$uid|$spaceId|"

    /** §75-2: رد المالك على «ده راتبك؟» لمرسل في بلد — «smsSalary|uid|space|sender». */
    fun salaryAnswer(uid: String, spaceId: String, sender: String): Boolean? {
        val key = "smsSalary|$uid|$spaceId|$sender"
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun setSalaryAnswer(uid: String, spaceId: String, sender: String, answer: Boolean?) {
        val key = "smsSalary|$uid|$spaceId|$sender"
        val edit = prefs.edit()
        if (answer == null) edit.remove(key) else edit.putBoolean(key, answer)
        check(edit.commit()) { "settings write failed" }
    }

    private fun migrateLegacyTarget(uid: String, spaceId: String) {
        val legacyKey = "autoTarget|$uid|$spaceId"
        val legacy = prefs.getString(legacyKey, null) ?: return
        if (uid != owner()) return // المرسلين بتوع صاحب الصندوق بس معروفين — بيتحول لما صاحبه يرجع
        val edit = prefs.edit()
        for (sender in senders()) {
            val key = senderPrefix(uid, spaceId) + smsSenderKey(sender)
            if (!prefs.contains(key)) edit.putString(key, legacy)
        }
        check(edit.remove(legacyKey).commit()) { "settings write failed" }
    }

    /** `true` = رسالة جديدة دخلت الصندوق (مش مكررة ولا متفلترة) ⇒ الاستقبال يطلب تسجيلها في الخلفية. */
    fun enqueue(uid: String, sender: String, timestamp: Long, originalBody: String): Boolean {
        if (!enabled(uid) || !accepts(sender)) return false
        val body = SmsSafety.sanitize(originalBody) ?: return false
        val row = ContentValues().apply {
            put("owner", uid)
            put("id", SmsSafety.key(sender, timestamp, body))
            put("sender", sender)
            put("body", body)
            put("received", timestamp)
        }
        return writableDatabase.insertWithOnConflict("messages", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1L
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
