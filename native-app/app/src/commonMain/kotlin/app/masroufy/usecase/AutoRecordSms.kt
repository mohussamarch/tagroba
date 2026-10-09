package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.WalletRepository
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.sync.withLock

/**
 * رسايل البنك بتتسجل لوحدها (OVERRIDES §72 — قرار المالك 2026-10-08): الرسالة المفهومة **الجديدة** بتتسجل وتتصنف لوحدها في الخلفية،
 * و**اللي بيستنى قرار المالك بس**: المرفوضة من القارئ · «شبه عملية موجودة» والتعارض · وبنك (مرسل) مالوش محفظة — البلد فيها أكتر
 * من حساب بنك والمالك لسه ما اختارش (رد المالك ١: مرة واحدة لكل بنك؛ حساب بنك واحد بس ⇒ بيتستخدم على طول).
 *
 * - **نفس خط الشاشة بالظبط** (`ReviewSmsInbox` ⇒ `ImportStatement`): منع التكرار · التصنيف · قرارات «زون التحويلات» · مصادر الدخل.
 *   التسجيل التلقائي **مش تأكيد**: التصنيف اللي جه من قاعدة بيفضل «مقترح» (§36)، والمحل اللي مالوش تصنيف بيتسجل «غير مصنف» (رد المالك ١).
 * - **مرة واحدة بس مهما اتكرر أو اتشغل مع الشاشة في نفس الوقت:** قفل واحد للعملية كلها ([SMS_RECORD_LOCK] — نفسه بتاع «سجّل الكل»)
 *   + منع التكرار في الاستيراد (المرجع `SMS:<بصمة>`) + شيل اللي خلص من الصندوق. لو الجهاز وقع بعد الحفظ وقبل الشيل، الرسالة
 *   بتطلع «مكررة» في المرة الجاية وبتتشال من غير ما تتسجل تاني؛ ولو وقع جوه الحفظ، وحدة العمل بترجّع كله والرسالة بتفضل في الصندوق.
 * - **مفيش إشعار للمسجّل لوحده** (رد المالك ٢) — الدالة دي ما بتعرفش حاجة عن الإشعارات أصلًا. اللي بيستنى بيتعد في [AutoRecordResult.waiting]
 *   ومنه مرشح تنبيه واحد (`smsConfirmCandidate`) والمحرك هو اللي بيقرر توقيته.
 * - **«المفهومة» = شكل معروف** (الجولة الرابعة — دفاع من جوه القارئ ومن برّه): بيتسجل لوحده بس الصف اللي [SmsShape.clear] (قالب بنك
 *   معروف أو عنوان موحّد من البنك المركزي واتجاهه نفس القارئ) **ومن مرسل المالك فعّله** في الصندوق. اللي اتفهم من كلمات عامة بس
 *   (`SmsShape.KeywordFallback`) بيفضل في الصندوق **مستني تأكيدك** وبيتحسب في التنبيه ([AutoRecordResult.unknownShape])، والشاشة
 *   بتعرضه جاهز بسببه (`SmsReviewLine.confirmReason`) و«سجّل الكل» بيسجله. الأجنبي (§75-12) مرفوض من القارئ أصلًا ⇒ مستني.
 */

/** بلد واحدة: قارئ رسايلها (جوه `ReviewSmsInbox`) ومحافظها ومستودع سجلات المصدر (عشان معرّفات العمليات اللي اتسجلت). */
class SmsLane private constructor(
    val spaceId: String,
    internal val review: ReviewSmsInbox,
    internal val wallets: WalletRepository,
    internal val sources: SourceRecordRepository,
    /** عقد C0: نفس المستورد اللي جوه [review] (الآثار والسجلات). */
    internal val importer: ImportStatement,
    /**
     * S2 (§75-4): استيراد البلد فيه `CashWithdrawalEffect` — من غيره `SmsReviewTarget.cashWalletId` = null فالسحب من الصرّاف بيستنى (ما
     * يتسجلش صرف من البنك لوحده). حارس توصيل: لو حد بنى البلد من غير الأثر.
     */
    internal val movesCashWithdrawals: Boolean = false,
) {
    companion object {
        /**
         * [importDeps] = **نفس** اعتمادات استيراد الكشف في البلد دي. لازم فيها قرارات «زون التحويلات» (`transferParties`) — عشان
         * التحويل لطرف المالك ربطه بشخص يتسجل عليه لوحده زي الكشف بالظبط (§60 · §72)؛ من غيرها بيرمي بدل ما يسجّل غلط في صمت.
         * [inbox] = الصندوق بقارئ رسايل البلد دي (حزمة البلد).
         */
        fun of(spaceId: String, importDeps: ImportStatementDeps, inbox: ManageSmsInbox, wallets: WalletRepository): SmsLane {
            require(importDeps.transferParties != null) { "SMS lane needs the transfer-party decisions (OVERRIDES §60/§72)" }
            val importer = ImportStatement(importDeps)
            val review = ReviewSmsInbox(ReviewSmsInboxDeps(inbox, importer, importDeps.merchants, importDeps.categories, importDeps.ids))
            return SmsLane(spaceId, review, wallets, importDeps.sources, importer, importDeps.effects.any { it is CashWithdrawalEffect })
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
    /** البنوك (المرسلين) اللي محتاجة المالك يختار محفظتها ⇒ رسايلها مستنية. */
    val unmappedSenders: List<UnmappedSender> = emptyList(),
    /**
     * جزء من [waiting]: رسايل جديدة اتفهمت **من كلمات عامة بس** (مش شكل معروف — الجولة الرابعة) ⇒ مستنية تأكيدك بسبب
     * `TextKey.SMS_WAIT_UNKNOWN_SHAPE`. بترتيب الصندوق.
     */
    val unknownShape: List<String> = emptyList(),
)

data class SmsWaiting(val messageIds: List<String>, val unknownShape: List<String> = emptyList())

/** مرسل (بنك) في بلد ورسايله مفهومة بس مالوش محفظة: البلد فيها أكتر من حساب بنك (أو مفيش) والمالك لسه ما اختارش. */
data class UnmappedSender(val spaceId: String, val sender: String, val messages: Int)

class AutoRecordSms(private val deps: AutoRecordSmsDeps) {
    val available: Boolean get() = deps.inbox.available

    private fun laneOf(spaceId: String): SmsLane =
        deps.lanes.firstOrNull { it.spaceId == spaceId } ?: throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))

    /**
     * محفظة رسايل [senderKey] في البلد (رد المالك ١ — 2026-10-08): اللي المالك ربطها بالبنك ده، وإلا **لو في البلد حساب بنك واحد بس** هو.
     * أكتر من حساب بنك (أو مفيش) ومالوش ربط ⇒ null ⇒ الرسايل تستنى. ربط لمحفظة اتمسحت ⇒ null برضه (ما بنخمّنش).
     */
    private suspend fun walletFor(lane: SmsLane, senderKey: String, mapping: Map<String, String>): SmsReviewTarget? {
        val all = lane.wallets.listAll()
        val mapped = mapping[senderKey]
        val wallet = (if (mapped != null) all.firstOrNull { it.id == mapped } else all.filter { it.kind == "bank" }.singleOrNull()) ?: return null
        return targetOf(lane, wallet, all)
    }

    /** محفظة [wallet] كهدف تسجيل — **نفسه** للخلفية ([walletFor]) وللشاشة ([targetFor]). */
    private fun targetOf(lane: SmsLane, wallet: Wallet, all: List<Wallet>): SmsReviewTarget {
        // الجولة السادسة: آخر 4 أرقام حسابات المالك التانية في البلد — رسالة عن واحد منهم ما بتتسجلش لوحدها في المحفظة دي (رد المالك ١
        // «محفظة لكل بنك» كان مفترض حساب واحد للبنك؛ سؤال مفتوح للمالك في OVERRIDES §72.3)
        val others = all.filter { it.id != wallet.id }.mapNotNull { last4(it.accountLast4) }.toSet()
        // S2 (§75-4): نفس قاعدة `CashWithdrawalEffect` بالظبط — ومن غير الأثر في استيراد البلد مفيش محفظة (السحب يستنى، ما يتسجلش صرف)
        val cash = if (lane.movesCashWithdrawals) cashWalletFor(wallet, all)?.id else null
        return SmsReviewTarget(wallet.id, wallet.name, wallet.currency, last4(wallet.accountLast4), others, cash)
    }

    /**
     * الهدف اللي الشاشة بتحمّل بيه رسايل محفظة [walletId] (`ReviewSmsInbox.load`) — نفس اللي التسجيل في الخلفية بيبنيه بالظبط (محفظة
     * الكاش وأرقام الحسابات التانية)، فسبب الانتظار اللي الشاشة بتعرضه هو نفسه. null = المحفظة مش في البلد دي.
     */
    suspend fun targetFor(spaceId: String, walletId: Id): SmsReviewTarget? {
        val lane = laneOf(spaceId)
        val all = lane.wallets.listAll()
        return all.firstOrNull { it.id == walletId }?.let { targetOf(lane, it, all) }
    }

    /** آخر 4 أرقام من خانة رقم الحساب (ممكن تبقى مكتوبة بمسافات أو كاملة في بيانات قديمة) — أقل من 4 أرقام ⇒ null. */
    private fun last4(value: String?): String? = value?.filter { it in '0'..'9' }?.takeLast(4)?.takeIf { it.length == 4 }

    /**
     * المرسلين اللي ليهم رسايل **مفهومة بقارئ البلد دي** في الصندوق، بترتيب أول ظهور — **والمالك مفعّلهم** (دفاع تاني: الجهاز ما بيحفظش
     * غيرهم أصلًا، بس لو المالك شال بنك من القايمة ورسايله لسه في الصندوق، ما بتتسجلش لوحدها — بتستنى).
     */
    private suspend fun sendersIn(lane: SmsLane): List<String> {
        val view = lane.review.inboxView()
        val enabled = view.senders.map(::smsSenderKey).toSet()
        return view.items.filter { it.parsed is SmsParseResult.Ok }.map { smsSenderKey(it.sender) }.distinct().filter { it in enabled }
    }

    /** المالك اختار محفظة بنك (مرسل) في بلد — مرة واحدة لكل بنك. null = يشيل الربط. المحفظة لازم تبقى من نفس البلد. */
    suspend fun chooseWallet(spaceId: String, sender: String, walletId: Id?) {
        val lane = laneOf(spaceId)
        if (walletId != null && lane.wallets.findById(walletId) == null) throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))
        deps.inbox.setSenderWallet(spaceId, sender, walletId)
    }

    /** المحفظة اللي رسايل المرسل ده هتتسجل فيها دلوقتي (الربط أو الحساب البنكي الوحيد)، أو null لو هتستنى. */
    suspend fun walletOf(spaceId: String, sender: String): Id? {
        val lane = laneOf(spaceId)
        return walletFor(lane, smsSenderKey(sender), deps.inbox.senderWallets(spaceId))?.walletId
    }

    /** البنوك اللي محتاجة المالك يختار محفظتها دلوقتي (للشاشة) — من غير كتابة. */
    suspend fun unmappedSenders(): List<UnmappedSender> = SMS_RECORD_LOCK.withLock {
        if (!deps.inbox.available || !deps.inbox.sync().enabled) emptyList() else unmappedLocked()
    }

    private suspend fun unmappedLocked(): List<UnmappedSender> = deps.lanes.flatMap { lane ->
        val mapping = deps.inbox.senderWallets(lane.spaceId)
        val view = lane.review.inboxView()
        val enabled = view.senders.map(::smsSenderKey).toSet()
        val parsed = view.items.filter { it.parsed is SmsParseResult.Ok }.groupBy { smsSenderKey(it.sender) }.filterKeys { it in enabled }
        parsed.mapNotNull { (sender, items) -> if (walletFor(lane, sender, mapping) == null) UnmappedSender(lane.spaceId, sender, items.size) else null }
    }

    /** التشغيلة: كل بلد وكل بنك بالدور يسجّل الجديد بتاعه ويشيل المكرر، وبعدين حساب «مستنية» على اللي فاضل. */
    suspend fun run(): AutoRecordResult = SMS_RECORD_LOCK.withLock {
        if (!deps.inbox.available || !deps.inbox.sync().enabled) return@withLock AutoRecordResult(AutoRecordStatus.OFF)
        var recorded = 0
        var duplicates = 0
        val ids = mutableListOf<Id>()
        for (lane in deps.lanes) {
            val mapping = deps.inbox.senderWallets(lane.spaceId)
            for (sender in sendersIn(lane)) {
                val target = walletFor(lane, sender, mapping) ?: continue
                lane.review.loadSender(target, sender)
                // من غير اختيارات: الجديد **اللي شكله معروف** بس، والتصنيف المقترح يفضل مقترح، والشبيه والتعارض ما بيتلمسوش
                val outcome = lane.review.recordLocked(emptyMap(), emptyList(), clearOnly = true)
                recorded += outcome.recorded
                duplicates += outcome.duplicates
                outcome.batchId?.let { batch -> ids += lane.sources.listByBatch(batch).mapNotNull { it.transactionId } }
            }
        }
        val waiting = waitingLocked()
        AutoRecordResult(AutoRecordStatus.RAN, recorded, duplicates, waiting.messageIds, ids, unmappedLocked(), waiting.unknownShape)
    }

    /**
     * اللي مستني قرار المالك دلوقتي — من غير ما يكتب حاجة: كل رسالة في الصندوق **ما عدا** اللي ليها محفظة وهتتسجل (جديدة وشكلها
     * معروف) أو هتتشال (مكررة). يعني رسالة لسه واصلة وهتتسجل لوحدها **مش** مستنية ⇒ مفيش إشعار ليها؛ ورسالة بنك مالوش محفظة
     * **مستنية**؛ والجديدة اللي اتفهمت من كلمات عامة بس **مستنية** ([SmsWaiting.unknownShape] — الجولة الرابعة).
     */
    suspend fun waiting(): SmsWaiting = SMS_RECORD_LOCK.withLock {
        if (!deps.inbox.available || !deps.inbox.sync().enabled) SmsWaiting(emptyList()) else waitingLocked()
    }

    private suspend fun waitingLocked(): SmsWaiting {
        val all = deps.inbox.sync().messages.map { it.id }
        val handled = mutableSetOf<String>()
        val unknownShape = mutableSetOf<String>()
        for (lane in deps.lanes) {
            val mapping = deps.inbox.senderWallets(lane.spaceId)
            for (sender in sendersIn(lane)) {
                val target = walletFor(lane, sender, mapping) ?: continue
                val view = lane.review.loadSender(target, sender)
                view.ready.forEach { line -> if (line.shape.clear) handled += line.messageId else unknownShape += line.messageId }
                view.duplicates.forEach { handled += it.messageId }
            }
        }
        val waiting = all.filter { it !in handled }
        return SmsWaiting(waiting, waiting.filter { it in unknownShape })
    }
}
