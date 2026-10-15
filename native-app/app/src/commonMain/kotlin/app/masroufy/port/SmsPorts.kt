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

/**
 * [messages] = **أحدث** رسايل الصندوق لحد حد معيّن (الجهاز 200) بالترتيب من الأقدم — §83: بعد قراية التاريخ كله الصندوق ممكن يبقى فيه آلاف،
 * واللي مستني تأكيد المالك من التاريخ القديم ما يوقفش تسجيل رسايل النهارده. [count] = كل اللي في الصندوق. [oldestQueuedAt] = وقت وصول أقدم
 * رسالة لسه في الصندوق (حتى لو مش في [messages]) — null = فاضي.
 */
data class SmsInboxState(
    val enabled: Boolean,
    val permission: Boolean,
    val more: Boolean,
    val count: Int,
    val senders: List<String>,
    val messages: List<QueuedSms>,
    val oldestQueuedAt: String? = null,
)

/**
 * §83: مؤشر قراية الصندوق على الجهاز ده (أوقات ISO): [point] = صندوق رسايل الجوال اتقري لحد هنا · [floor] = أول نقطة الجهاز ده قرا منها
 * (اللي قبلها عمره ما اتقري هنا — برنامج اتثبت جديد أو جوال جديد).
 */
data class SmsReadMarks(val point: String, val floor: String)

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

    /**
     * §77-A «وضع التعلّم»: بصمات **أشكال** الرسايل اللي المالك أكّد أول رسالة منها، لكل مرسل (بعد [smsSenderKey]) في البلد [spaceId].
     * على الجهاز بس ولصاحب الصندوق (زي تعلّم التنبيهات §61) — **مش** في فايربيز ولا النسخة الشاملة: الجوال الجديد بيتعلّم كل شكل تاني مرة.
     * بصمات بس (`SmsRow.learnKey`) — ولا حرف من نص رسالة.
     */
    suspend fun learnedShapes(spaceId: String): Map<String, Set<String>>

    /** المالك أكّد رسايل بالأشكال دي من [sender] في [spaceId] ⇒ الرسايل الجاية بنفس الشكل بتتسجل لوحدها. */
    suspend fun learnShapes(spaceId: String, sender: String, keys: Set<String>)

    /** ينسى أشكال [sender] في [spaceId] ⇒ رسالته الجاية تستنى تأكيد مرة تاني. */
    suspend fun forgetShapes(spaceId: String, sender: String)

    /** §75-2: رد المالك على «ده راتبك؟» لرسايل راتب [sender] اللي من غير اسم جهة — null = لسه ما اتسألش. على الجهاز بس. */
    suspend fun salaryAnswer(spaceId: String, sender: String): Boolean?

    /** null = يشيل الرد (يتسأل تاني). */
    suspend fun setSalaryAnswer(spaceId: String, sender: String, answer: Boolean?)

    // ── §83 «يقرا كل التاريخ ويكمّل الناقص لوحده» (المالك 2026-10-10) ──

    /** وقت وصول أقدم رسالة **على الجوال** من [senders] (ISO) — null = مفيش، أو مفيش إذن، أو الجهاز ما بيقراش صندوق الرسايل. */
    suspend fun oldestMessageAt(senders: List<String>): String?

    /** مؤشر القراية على الجهاز ده — null = الصندوق مش مفعّل لصاحبه هنا. */
    suspend fun readMarks(): SmsReadMarks?

    /**
     * يرجّع القراية لـ[point] (ISO) لو أقدم من المؤشر — المزامنة الجاية بتجيب كل اللي من هناك (من المرسلين المفعّلين). الرسالة اللي اتقرت
     * قبل كده على الجهاز ده (اتسجلت · اتشالت · لسه مستنية) **ما بتدخلش تاني**، وأي رسالة اتسجلت قبل كده في أي جهاز بتطلع «مكررة» بمرجعها.
     */
    suspend fun readFrom(point: String)

    /**
     * مراجعة 0.3: الصندوق بيعرض أحدث صفحة (200 على الجهاز) — [skip] = يتخطى أحدث [skip] رسالة مستنية عشان التسجيل لوحده يوصل للأقدم
     * اللي ورا صفحة كلها مستنية تأكيد. 0 = الصفحة العادية (أحدث الرسايل). بيأثر على كل `sync` بعده لحد ما يرجع 0.
     */
    suspend fun page(skip: Int)
}

/** نفس المرسل مهما اتكتب بحروف كبيرة أو صغيرة أو مسافات (الصندوق بيقارن المرسلين من غير حالة الحروف). */
fun smsSenderKey(sender: String): String = sender.trim().lowercase()
