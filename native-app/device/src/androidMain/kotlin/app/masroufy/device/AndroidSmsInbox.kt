package app.masroufy.device

import app.masroufy.core.ProblemCode
import app.masroufy.problems.Problems
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.SmsReadMarks
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime

/**
 * صندوق رسايل البنك لحساب واحد ([uid]) — نقل `SmsInboxPlugin.java`. التفعيل محتاج إذن القراية والاستقبال، والشاشة هي اللي
 * بتطلبهم (نافذة أندرويد) — هنا لو مش موجودين بيرمي [SmsPermissionError]. المزامنة بتكمّل من آخر مؤشر (500 رسالة في المرة).
 */
class AndroidSmsInbox(private val context: Context, private val uid: String) : SmsInboxPort {
    override val available: Boolean = true

    private fun granted() = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun <T> withStore(block: (SmsInboxStore) -> T): T = withContext(Dispatchers.IO) {
        try {
            synchronized(SmsInboxStore.LOCK) { SmsInboxStore(context).use(block) }
        } catch (e: SecurityException) {
            Problems.report(ProblemCode.SMS_PERMISSION_LOST, e)
            throw SmsPermissionError(uiText(TextKey.SMS_INBOX_PERMISSION_REVOKED))
        }
    }

    override suspend fun enable(senders: List<String>): SmsInboxState {
        val names = senders.map { it.trim() }.toSet()
        if (names.isEmpty() || names.size > 10 || names.any { it.isEmpty() || it.length > 50 }) throw IllegalArgumentException(uiText(TextKey.SMS_SENDERS_REQUIRED))
        if (!granted()) throw SmsPermissionError(uiText(TextKey.SMS_INBOX_PERMISSION_MISSING))
        return withStore {
            it.configure(uid, names)
            it.snapshot(uid, true, false, skip)
        }
    }

    override suspend fun disable(): SmsInboxState = withStore {
        it.disable(uid)
        it.snapshot(uid, granted(), false, skip)
    }

    override suspend fun acknowledge(ids: List<String>): SmsInboxState = withStore {
        it.acknowledge(uid, ids)
        it.snapshot(uid, granted(), false, skip)
    }

    override suspend fun senderWallets(spaceId: String): Map<String, String> = withStore { it.senderWallets(uid, spaceId) }

    override suspend fun setSenderWallet(spaceId: String, sender: String, walletId: String?) =
        withStore { it.setSenderWallet(uid, spaceId, smsSenderKey(sender), walletId) }

    // §77-A وضع التعلّم · §75-2 «ده راتبك؟» — على الجهاز لصاحب الصندوق ([uid]) ولكل بلد (`SmsInboxStore`)
    override suspend fun learnedShapes(spaceId: String): Map<String, Set<String>> = withStore { it.learnedShapes(uid, spaceId) }

    override suspend fun learnShapes(spaceId: String, sender: String, keys: Set<String>) =
        withStore { it.learnShapes(uid, spaceId, smsSenderKey(sender), keys) }

    override suspend fun forgetShapes(spaceId: String, sender: String) = withStore { it.forgetShapes(uid, spaceId, smsSenderKey(sender)) }

    override suspend fun salaryAnswer(spaceId: String, sender: String): Boolean? = withStore { it.salaryAnswer(uid, spaceId, smsSenderKey(sender)) }

    override suspend fun setSalaryAnswer(spaceId: String, sender: String, answer: Boolean?) =
        withStore { it.setSalaryAnswer(uid, spaceId, smsSenderKey(sender), answer) }

    override suspend fun handoffCard(): String? = withStore { it.handoffCard(uid) }

    override suspend fun setHandoffCard(value: String?) = withStore { it.setHandoffCard(uid, value) }

    // ── §83 «يقرا كل التاريخ ويكمّل الناقص لوحده» ──

    override suspend fun oldestMessageAt(senders: List<String>): String? = withContext(Dispatchers.IO) {
        val names = senders.map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty() || context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return@withContext null
        val selection = names.joinToString(" OR ") { "address = ? COLLATE NOCASE" }
        try {
            context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("date"), selection, names.toTypedArray(), "date ASC")?.use { rows ->
                if (rows.moveToFirst()) Instant.ofEpochMilli(rows.getLong(0)).toString() else null
            }
        } catch (e: SecurityException) {
            Problems.report(ProblemCode.SMS_PERMISSION_LOST, e)
            null
        }
    }

    override suspend fun readMarks(): SmsReadMarks? = withStore { store ->
        if (!store.enabled(uid)) null else SmsReadMarks(Instant.ofEpochMilli(store.cursorDate()).toString(), Instant.ofEpochMilli(store.cursorFloor()).toString())
    }

    override suspend fun readFrom(point: String) {
        val millis = runCatching { OffsetDateTime.parse(point).toInstant().toEpochMilli() }.getOrNull() ?: throw IllegalArgumentException(point)
        withStore { store -> if (store.owner() == uid) store.rewind(millis) }
    }

    /** مراجعة 0.3: صفحة الصندوق (`SmsInboxPort.page`) — في الذاكرة بس، والبرنامج لما يفتح بيبدأ من أحدث صفحة. */
    @Volatile private var skip = 0

    override suspend fun page(skip: Int) {
        this.skip = maxOf(0, skip)
    }

    override suspend fun sync(): SmsInboxState = withStore { store ->
        // التحقق من الإذن ما بيفتحش نافذة أندرويد لوحده أبدًا
        val more = store.enabled(uid) && granted() && catchUp(store)
        store.snapshot(uid, granted(), more, skip)
    }

    /**
     * الرسايل اللي وصلت والتطبيق مقفول (أو الاستقبال فاتها) — من آخر مؤشر، بالترتيب.
     * r041c: الدفعة كلها في معاملة واحدة والمؤشر بيتحفظ مرة بعدها (كان حفظ إعدادات وكتابة على القرص لكل رسالة — آلاف في قراية التاريخ).
     * المؤشر لسه بيتحرك **بعد** الحفظ بس: المعاملة وقعت ⇒ المؤشر مكانه والدفعة بتتقري تاني (المكرر بيتجاهل).
     */
    private fun catchUp(store: SmsInboxStore): Boolean {
        val senders = store.senders()
        if (senders.isEmpty()) return false
        val args = arrayOf(store.cursorDate().toString(), store.cursorDate().toString(), store.cursorId().toString()) + senders
        val selection = "(date > ? OR (date = ? AND _id > ?)) AND (" + senders.joinToString(" OR ") { "address = ? COLLATE NOCASE" } + ")"
        val projection = arrayOf("_id", "address", "body", "date", "date_sent")
        var more = false
        var last: Pair<Long, Long>? = null
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args, "date ASC, _id ASC")?.use { rows ->
                var processed = 0
                while (rows.moveToNext()) {
                    if (processed++ >= 500) {
                        more = true
                        break
                    }
                    val date = rows.getLong(3)
                    val sent = rows.getLong(4)
                    store.enqueue(uid, rows.getString(1), if (sent > 0) sent else date, rows.getString(2) ?: "")
                    last = date to rows.getLong(0)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // المؤشر بيتحرك بعد الحفظ (أو الاستبعاد المقصود) بس
        last?.let { (date, id) -> store.checkpoint(date, id) }
        return more
    }
}
