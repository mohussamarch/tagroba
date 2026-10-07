package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.WalletRepository
import kotlinx.coroutines.sync.withLock

/**
 * رسايل البنك بتتسجل لوحدها (OVERRIDES §72 — قرار المالك 2026-10-08): الرسالة المفهومة **الجديدة** بتتسجل وتتصنف لوحدها في الخلفية،
 * و**اللي بيستنى قرار المالك بس**: المرفوضة من القارئ · «شبه عملية موجودة» والتعارض · وأي بلد مالهاش محفظة تتسجل فيها.
 *
 * - **نفس خط الشاشة بالظبط** (`ReviewSmsInbox` ⇒ `ImportStatement`): منع التكرار · التصنيف · قرارات «زون التحويلات» · مصادر الدخل.
 *   التسجيل التلقائي **مش تأكيد**: التصنيف اللي جه من قاعدة بيفضل «مقترح» (§36)، والمحل اللي مالوش تصنيف بيتسجل «غير مصنف» (رد المالك ١).
 * - **مرة واحدة بس مهما اتكرر أو اتشغل مع الشاشة في نفس الوقت:** قفل واحد للعملية كلها ([SMS_RECORD_LOCK] — نفسه بتاع «سجّل الكل»)
 *   + منع التكرار في الاستيراد (المرجع `SMS:<بصمة>`) + شيل اللي خلص من الصندوق. لو الجهاز وقع بعد الحفظ وقبل الشيل، الرسالة
 *   بتطلع «مكررة» في المرة الجاية وبتتشال من غير ما تتسجل تاني؛ ولو وقع جوه الحفظ، وحدة العمل بترجّع كله والرسالة بتفضل في الصندوق.
 * - **مفيش إشعار للمسجّل لوحده** (رد المالك ٢) — الدالة دي ما بتعرفش حاجة عن الإشعارات أصلًا. اللي بيستنى بيتعد في [AutoRecordResult.waiting]
 *   ومنه مرشح تنبيه واحد (`smsConfirmCandidate`) والمحرك هو اللي بيقرر توقيته.
 */

/** بلد واحدة: قارئ رسايلها (جوه `ReviewSmsInbox`) ومحافظها ومستودع سجلات المصدر (عشان معرّفات العمليات اللي اتسجلت). */
class SmsLane private constructor(
    val spaceId: String,
    internal val review: ReviewSmsInbox,
    internal val wallets: WalletRepository,
    internal val sources: SourceRecordRepository,
) {
    companion object {
        /**
         * [importDeps] = **نفس** اعتمادات استيراد الكشف في البلد دي. لازم فيها قرارات «زون التحويلات» (`transferParties`) — عشان
         * التحويل لطرف المالك ربطه بشخص يتسجل عليه لوحده زي الكشف بالظبط (§60 · §72)؛ من غيرها بيرمي بدل ما يسجّل غلط في صمت.
         * [inbox] = الصندوق بقارئ رسايل البلد دي (حزمة البلد).
         */
        fun of(spaceId: String, importDeps: ImportStatementDeps, inbox: ManageSmsInbox, wallets: WalletRepository): SmsLane {
            require(importDeps.transferParties != null) { "SMS lane needs the transfer-party decisions (OVERRIDES §60/§72)" }
            val review = ReviewSmsInbox(ReviewSmsInboxDeps(inbox, ImportStatement(importDeps), importDeps.merchants, importDeps.categories, importDeps.ids))
            return SmsLane(spaceId, review, wallets, importDeps.sources)
        }
    }
}

data class AutoRecordSmsDeps(
    /** الصندوق نفسه (الجهاز) — المزامنة ومحفظة كل بلد. */
    val inbox: SmsInboxPort,
    /** البلاد بالترتيب. رسالة بلد بتترفض من قارئ البلد التانية (العملة) فبتتسجل في بلدها بس. */
    val lanes: List<SmsLane>,
)

enum class AutoRecordStatus {
    /** اشتغل. */
    RAN,

    /** القراية مقفولة أو الجهاز مالوش رسايل (الآيفون) ⇒ ولا حاجة اتعملت ومفيش حاجة «مستنية». */
    OFF,
}

data class AutoRecordResult(
    val status: AutoRecordStatus,
    val recorded: Int = 0,
    /** اتسجلت قبل كده (من الشاشة أو تشغيلة وقعت قبل الشيل) — اتشالت من الصندوق من غير تسجيل. */
    val duplicates: Int = 0,
    /** معرّفات الرسايل اللي مستنية قرار المالك، بترتيب الصندوق (الأقدم الأول). */
    val waiting: List<String> = emptyList(),
    /** العمليات اللي اتسجلت دلوقتي — الشاشة بتعمل عليها اللمعة النعناعي (§71). */
    val recordedTransactionIds: List<Id> = emptyList(),
    /** البلاد اللي مالهاش محفظة تتسجل فيها ⇒ رسايلها مستنية. */
    val spacesWithoutWallet: List<String> = emptyList(),
)

data class SmsWaiting(val messageIds: List<String>)

class AutoRecordSms(private val deps: AutoRecordSmsDeps) {
    val available: Boolean get() = deps.inbox.available

    /**
     * المحفظة اللي رسايل البلد بتتسجل فيها: اللي المالك اختارها، وإلا **أول محفظة بنك** (نفس اختيار الشاشة §36).
     * محفوظة بس اتمسحت ⇒ مفيش (ما بنخمّنش محفظة تانية).
     */
    private suspend fun targetOf(lane: SmsLane): SmsReviewTarget? {
        val all = lane.wallets.listAll()
        val stored = deps.inbox.autoTarget(lane.spaceId)
        val wallet = if (stored != null) all.firstOrNull { it.id == stored } else all.firstOrNull { it.kind == "bank" }
        return wallet?.let { SmsReviewTarget(it.id, it.name, it.currency) }
    }

    /** المالك اختار محفظة البلد (null = يرجع للافتراضي). المحفظة لازم تبقى من نفس البلد. */
    suspend fun chooseWallet(spaceId: String, walletId: Id?) {
        val lane = deps.lanes.firstOrNull { it.spaceId == spaceId } ?: throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))
        if (walletId != null && lane.wallets.findById(walletId) == null) throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))
        deps.inbox.setAutoTarget(spaceId, walletId)
    }

    suspend fun walletOf(spaceId: String): Id? = deps.lanes.firstOrNull { it.spaceId == spaceId }?.let { targetOf(it)?.walletId }

    /** التشغيلة: كل بلد بالدور تسجّل الجديد بتاعها وتشيل المكرر، وبعدين حساب «مستنية» على اللي فاضل. */
    suspend fun run(): AutoRecordResult = SMS_RECORD_LOCK.withLock {
        if (!deps.inbox.available || !deps.inbox.sync().enabled) return@withLock AutoRecordResult(AutoRecordStatus.OFF)
        var recorded = 0
        var duplicates = 0
        val ids = mutableListOf<Id>()
        val noWallet = mutableListOf<String>()
        for (lane in deps.lanes) {
            val target = targetOf(lane)
            if (target == null) {
                noWallet += lane.spaceId
                continue
            }
            lane.review.load(target)
            // من غير اختيارات: الجديد بس، والتصنيف المقترح يفضل مقترح، والشبيه والتعارض ما بيتلمسوش
            val outcome = lane.review.recordLocked(emptyMap(), emptyList())
            recorded += outcome.recorded
            duplicates += outcome.duplicates
            outcome.batchId?.let { batch -> ids += lane.sources.listByBatch(batch).mapNotNull { it.transactionId } }
        }
        AutoRecordResult(AutoRecordStatus.RAN, recorded, duplicates, waitingLocked().messageIds, ids, noWallet)
    }

    /**
     * اللي مستني قرار المالك دلوقتي — من غير ما يكتب حاجة: كل رسالة في الصندوق **ما عدا** اللي بلد ليها محفظة هتسجّلها (جديدة)
     * أو هتشيلها (مكررة). يعني رسالة لسه واصلة وهتتسجل لوحدها **مش** مستنية ⇒ مفيش إشعار ليها.
     */
    suspend fun waiting(): SmsWaiting = SMS_RECORD_LOCK.withLock {
        if (!deps.inbox.available || !deps.inbox.sync().enabled) SmsWaiting(emptyList()) else waitingLocked()
    }

    private suspend fun waitingLocked(): SmsWaiting {
        val all = deps.inbox.sync().messages.map { it.id }
        val handled = mutableSetOf<String>()
        for (lane in deps.lanes) {
            val target = targetOf(lane) ?: continue
            val view = lane.review.load(target)
            view.ready.forEach { handled += it.messageId }
            view.duplicates.forEach { handled += it.messageId }
        }
        return SmsWaiting(all.filter { it !in handled })
    }
}
