package app.masroufy.port

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsParseResult

/**
 * رسايل البنك — نقل `BankSmsPort.ts` و`SmsInboxPort.ts`. **أندرويد بس** (KOTLIN_PLAN §2):
 * الآيفون مفيهوش قراية رسايل، فتنفيذه هناك `available = false`.
 */

data class BankSmsRead(val messages: List<BankSmsMessage>, val truncated: Boolean)

/** قراية رسايل فترة من مرسلين محددين، مرة واحدة بطلب المستخدم. */
interface BankSmsPort {
    val available: Boolean

    suspend fun read(from: String, to: String, senders: List<String>): BankSmsRead
}

/** المحلل بيتمرر كاعتماد — طبقة الاستخدامات ما تعرفش أشكال رسايل البنوك. */
typealias BankSmsParser = (message: BankSmsMessage, lineNumber: Int) -> SmsParseResult

data class QueuedSms(val id: String, val sender: String, val receivedAt: String, val body: String) {
    fun message() = BankSmsMessage(sender, receivedAt, body)
}

data class SmsInboxState(
    val enabled: Boolean,
    val permission: Boolean,
    val more: Boolean,
    val count: Int,
    val senders: List<String>,
    val messages: List<QueuedSms>,
)

/** صندوق الرسايل اللي الجهاز بيلمّها في الخلفية من المرسلين اللي المستخدم فعّلهم. */
interface SmsInboxPort {
    val available: Boolean

    suspend fun sync(): SmsInboxState

    suspend fun enable(senders: List<String>): SmsInboxState

    suspend fun disable(): SmsInboxState

    /** الرسايل دي خلصت (اتسجلت أو اتشالت) — تتشال من الصندوق. */
    suspend fun acknowledge(ids: List<String>): SmsInboxState

    /**
     * المحفظة اللي رسايل **كل مرسل (بنك)** بتتسجل فيها لوحدها في البلد [spaceId] (OVERRIDES §72 — رد المالك ١) — على الجهاز، لصاحب
     * الصندوق. المفتاح = اسم المرسل بعد [smsSenderKey]. مرسل مش هنا ⇒ لو في البلد حساب بنك واحد بس بيتستخدم، وإلا رسايله بتستنى.
     */
    suspend fun senderWallets(spaceId: String): Map<String, String>

    /** null = يشيل الربط (رسايل المرسل ترجع تستنى لو في البلد أكتر من حساب بنك). */
    suspend fun setSenderWallet(spaceId: String, sender: String, walletId: String?)
}

/** نفس المرسل مهما اتكتب بحروف كبيرة أو صغيرة أو مسافات (الصندوق بيقارن المرسلين من غير حالة الحروف). */
fun smsSenderKey(sender: String): String = sender.trim().lowercase()
