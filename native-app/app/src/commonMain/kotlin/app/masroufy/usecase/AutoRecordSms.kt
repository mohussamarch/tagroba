package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.PendingAsk
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.WalletRepository
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.sync.withLock

/**
 * رسايل البنك بتتسجل لوحدها (OVERRIDES §72 — قرار المالك 2026-10-08): الرسالة المفهومة **الجديدة** بتتسجل وتتصنف لوحدها في الخلفية،
 * و**اللي بيستنى قرار المالك بس**: المرفوضة من القارئ · «شبه عملية موجودة» والتعارض · وبنك (مرسل) مالوش محفظة.
 * - **نفس خط الشاشة بالظبط** (`ReviewSmsInbox` ⇒ `ImportStatement`): منع التكرار · التصنيف · قرارات «زون التحويلات» · مصادر الدخل ·
 *   آثار وقت التسجيل (عقد C0). التسجيل التلقائي **مش تأكيد**: التصنيف من قاعدة بيفضل «مقترح» (§36).
 * - **مرة واحدة بس مهما اتكرر أو اتشغل مع الشاشة في نفس الوقت:** قفل واحد ([SMS_RECORD_LOCK]) + منع التكرار بالمرجع `SMS:<بصمة>` +
 *   شيل اللي خلص من الصندوق.
 * - **مفيش إشعار للمسجّل لوحده** (رد المالك ٢): اللي بيستنى بيتعد في [AutoRecordResult.waiting] ومنه مرشح تنبيه واحد.
 * - **«المفهومة» = شكل معروف** (الجولة الرابعة) **اتأكد قبل كده** (§77-A «وضع التعلّم» — S1): أول رسالة من كل شكل من كل مرسل في كل بلد
 *   بتستنى تأكيد المالك ([AutoRecordResult.newShape])، وبعد ما يأكدها (الشاشة أو [confirm]) رسايل الشكل ده بتتسجل لوحدها — حتى اللي كانت
 *   مستنية في الصندوق (الدورة الجاية).
 * - **بنك بحسابين** (§75-11 — S1): كل رسالة بتروح المحفظة اللي آخر 4 أرقامها في الرسالة (`SmsRouting.kt`)، ودفعة لكل محفظة.
 */

/** بلد واحدة: قارئ رسايلها (جوه `ReviewSmsInbox`) ومحافظها ومستودع سجلات المصدر (عشان معرّفات العمليات اللي اتسجلت). */
class SmsLane private constructor(
    val spaceId: String,
    internal val review: ReviewSmsInbox,
    internal val wallets: WalletRepository,
    internal val sources: SourceRecordRepository,
    /** عقد C0: نفس المستورد اللي جوه [review] (الآثار والسجلات). */
    internal val importer: ImportStatement,
    private val importDeps: ImportStatementDeps,
    private val screenDeps: ReviewSmsInboxDeps,
) {
    /**
     * **شاشة رسايل البنك للبلد دي** (مراجعة S1): نفس التعلّم (§77-A) والآثار (§75-2 · §75-11) والمحافظ بتوع التسجيل التلقائي — الشاشة
     * اللي اتبنت لوحدها من غيرهم ما كانتش بتعلّم ولا بتسأل ولا بتوزّع. نسخة جديدة كل مرة (جلسة الشاشة ما تتخلطش بجلسة الخلفية).
     */
    fun screen(contribute: (suspend (MerchantContribution, Id) -> Unit)? = null): ReviewSmsInbox =
        ReviewSmsInbox(screenDeps.copy(importer = ImportStatement(importDeps), contribute = contribute))

    companion object {
        /**
         * [importDeps] = **نفس** اعتمادات استيراد الكشف في البلد دي — فيها قرارات «زون التحويلات» (`transferParties`) لازم، وآثار وقت
         * التسجيل لرسايل البنك لازم (`effects` — S1: [OwnAccountByLast4Effect] · [SmsSalaryEffect]؛ مراجعة S1: من غيرهم «ده راتبك؟ أيوه»
         * و«حسابي التاني» ما كانوش بيعملوا حاجة في صمت). [inbox] = الصندوق بقارئ رسايل البلد دي (حزمة البلد).
         */
        fun of(spaceId: String, importDeps: ImportStatementDeps, inbox: ManageSmsInbox, wallets: WalletRepository): SmsLane {
            require(importDeps.transferParties != null) { "SMS lane needs the transfer-party decisions (OVERRIDES §60/§72)" }
            require(importDeps.effects.any { it is SmsSalaryEffect } && importDeps.effects.any { it is OwnAccountByLast4Effect }) {
                "SMS lane needs the S1 record effects: SmsSalaryEffect and OwnAccountByLast4Effect (OVERRIDES §75-2/§75-11)"
            }
            val importer = ImportStatement(importDeps)
            val learning = SmsLearning(inbox.port, spaceId)
            val deps = ReviewSmsInboxDeps(inbox, importer, importDeps.merchants, importDeps.categories, importDeps.ids, learning, wallets)
            return SmsLane(spaceId, ReviewSmsInbox(deps), wallets, importDeps.sources, importer, importDeps, deps)
        }
    }
}

data class AutoRecordSmsDeps(
    /** الصندوق نفسه (الجهاز) — المزامنة ومحفظة كل بنك والأشكال اللي اتعلّمت. */
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
    /** جزء من [waiting]: اتفهمت **من كلمات عامة بس** (`TextKey.SMS_WAIT_UNKNOWN_SHAPE`). بترتيب الصندوق. */
    val unknownShape: List<String> = emptyList(),
    /** جزء من [waiting] (§77-A): **شكل واضح لسه ما اتأكدش** من المرسل ده (`TextKey.SMS_WAIT_NEW_SHAPE`). بترتيب الصندوق. */
    val newShape: List<String> = emptyList(),
)

data class SmsWaiting(val messageIds: List<String>, val unknownShape: List<String> = emptyList(), val newShape: List<String> = emptyList())

/** مرسل (بنك) في بلد ورسايله مفهومة بس مالهاش محفظة: البلد فيها أكتر من حساب بنك (أو مفيش) والمالك لسه ما اختارش. */
data class UnmappedSender(val spaceId: String, val sender: String, val messages: Int)

/** اللي مستني دلوقتي + فين ومتى كل رسالة وسؤالها (للأسئلة `SmsAskSource`). */
private class WaitingState(
    val waiting: SmsWaiting,
    val unmapped: List<UnmappedSender>,
    val questions: Map<String, AskKind>,
    val where: Map<String, Pair<String, IsoDate?>>,
)

class AutoRecordSms(private val deps: AutoRecordSmsDeps) {
    val available: Boolean get() = deps.inbox.available

    private fun laneOf(spaceId: String): SmsLane =
        deps.lanes.firstOrNull { it.spaceId == spaceId } ?: throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))

    private suspend fun off(): Boolean = !deps.inbox.available || !deps.inbox.sync().enabled

    /**
     * كل (بلد · محفظة) ورسايلها المفهومة من المرسلين **اللي المالك مفعّلهم** (دفاع تاني: لو شال بنك من القايمة ورسايله لسه في الصندوق،
     * ما بتتسجلش لوحدها) — [visit] بياخد معاينة كل محفظة. بيرجّع المرسلين اللي رسايلهم مالهاش محفظة.
     */
    private suspend fun forEachRoute(only: (InboxItem) -> Boolean, visit: suspend (SmsLane, SmsReview) -> Unit): List<UnmappedSender> {
        val unmapped = mutableListOf<UnmappedSender>()
        for (lane in deps.lanes) {
            val view = lane.review.inboxView()
            val enabled = view.senders.map(::smsSenderKey).toSet()
            val items = view.items.filter { it.parsed is SmsParseResult.Ok && only(it) }
            val senders = items.map { smsSenderKey(it.sender) }.distinct().filter { it in enabled }
            if (senders.isEmpty()) continue
            val mapping = deps.inbox.senderWallets(lane.spaceId)
            val wallets = lane.wallets.listAll()
            for (sender in senders) {
                val routes = routeSender(wallets, sender, mapping, items)
                if (routes.unrouted > 0) unmapped += UnmappedSender(lane.spaceId, sender, routes.unrouted)
                for (route in routes.routes) visit(lane, lane.review.loadFor(route.target) { it.id in route.messageIds })
            }
        }
        return unmapped
    }

    /** المالك اختار محفظة بنك (مرسل) في بلد — مرة واحدة لكل بنك. null = يشيل الربط. المحفظة لازم تبقى من نفس البلد. */
    suspend fun chooseWallet(spaceId: String, sender: String, walletId: Id?) {
        val lane = laneOf(spaceId)
        if (walletId != null && lane.wallets.findById(walletId) == null) throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))
        deps.inbox.setSenderWallet(spaceId, sender, walletId)
    }

    /** المحفظة اللي رسايل المرسل ده هتتسجل فيها دلوقتي (الربط أو الحساب البنكي الوحيد)، أو null لو هتستنى. */
    suspend fun walletOf(spaceId: String, sender: String): Id? =
        senderWallet(laneOf(spaceId).wallets.listAll(), smsSenderKey(sender), deps.inbox.senderWallets(spaceId))?.id

    /** البنوك اللي محتاجة المالك يختار محفظتها دلوقتي (للشاشة) — من غير كتابة. */
    suspend fun unmappedSenders(): List<UnmappedSender> = SMS_RECORD_LOCK.withLock { if (off()) emptyList() else waitingLocked().unmapped }

    /** التشغيلة: كل بلد وكل بنك وكل محفظة بالدور يسجّل الجديد **اللي شكله اتعلّم** ويشيل المكرر، وبعدين حساب «مستنية» على اللي فاضل. */
    suspend fun run(): AutoRecordResult = SMS_RECORD_LOCK.withLock {
        if (off()) return@withLock AutoRecordResult(AutoRecordStatus.OFF)
        var recorded = 0
        var duplicates = 0
        val ids = mutableListOf<Id>()
        forEachRoute({ true }) { lane, _ ->
            // من غير اختيارات: الجديد **اللي شكله معروف واتعلّم** بس، والتصنيف المقترح يفضل مقترح، والشبيه والتعارض ما بيتلمسوش
            val outcome = lane.review.recordLocked(emptyMap(), emptyList(), clearOnly = true)
            recorded += outcome.recorded
            duplicates += outcome.duplicates
            for (batch in outcome.batchIds) ids += lane.sources.listByBatch(batch).mapNotNull { it.transactionId }
        }
        val state = waitingLocked()
        val w = state.waiting
        AutoRecordResult(AutoRecordStatus.RAN, recorded, duplicates, w.messageIds, ids, state.unmapped, w.unknownShape, w.newShape)
    }

    /**
     * §77-A — **تأكيد المالك** لرسايل مستنية (من غير الشاشة): بتتسجل في محفظتها (الجديد · والشبيه لأنه اختاره) **وأشكالها بتتعلّم**، فرسايل
     * الشكل ده الجاية (واللي مستنية) بتتسجل لوحدها. المكرر بيتشال من غير ما يعلّم. بيرجّع عدد اللي اتسجل.
     * §75-2 (مراجعة S1): الرسالة اللي عليها «ده راتبك؟» محتاجة الرد [isSalary] (بيتحفظ لمرسلها ويتطبق عليها وهي بتتسجل)؛ من غيره **ما
     * بتتسجلش** وبتفضل مستنية بسؤالها — كانت بتتسجل «مش متصنف» والسؤال يرجع للرسالة الجاية، والنص بيقول «أكّد مرة إنه راتبك».
     */
    suspend fun confirm(messageIds: List<String>, isSalary: Boolean? = null): Int = SMS_RECORD_LOCK.withLock {
        if (off()) return@withLock 0
        val wanted = messageIds.toSet()
        var recorded = 0
        forEachRoute({ it.id in wanted }) { lane, view ->
            val similar = view.similar.filter { it.state == MatchingState.SIMILAR }.map { it.lineNumber }
            recorded += lane.review.recordLocked(emptyMap(), similar, clearOnly = false, salaryAnswer = isSalary).recorded
        }
        recorded
    }

    /** §75-2 — رد المالك على «ده راتبك؟» لرسايل [sender] في [spaceId] (null = يتسأل تاني). أيوه ⇒ رسايل الراتب منه «راتب» مؤكد. */
    suspend fun answerSalary(spaceId: String, sender: String, yes: Boolean?) {
        laneOf(spaceId)
        deps.inbox.setSalaryAnswer(spaceId, sender, yes)
    }

    /** §77-A — ينسى أشكال رسايل [sender] في [spaceId] ⇒ رسالته الجاية تستنى تأكيد مرة تاني. */
    suspend fun forgetLayouts(spaceId: String, sender: String) {
        laneOf(spaceId)
        deps.inbox.forgetShapes(spaceId, sender)
    }

    /**
     * اللي مستني قرار المالك دلوقتي — من غير ما يكتب حاجة: كل رسالة في الصندوق **ما عدا** اللي هتتسجل لوحدها (جديدة وشكلها معروف
     * واتعلّم) أو هتتشال (مكررة). يعني رسالة هتتسجل لوحدها **مش** مستنية ⇒ مفيش إشعار ليها؛ وأول رسالة من شكل **مستنية**.
     */
    suspend fun waiting(): SmsWaiting = SMS_RECORD_LOCK.withLock { if (off()) SmsWaiting(emptyList()) else waitingLocked().waiting }

    /**
     * سؤال لكل رسالة مستنية (`SmsAskSource`): «ده راتبك؟» لو عليها، وإلا «مستنية تأكيدك» — بلدها ويومها لو اتقرت. الرسالة اللي ولا قارئ
     * فهمها: بلدها = البلد اللي المالك ربط فيها البنك ده بمحفظة (لو بلد واحدة)، أو البلد الوحيدة؛ غير كده **مش معروفة** (`spaceId` فاضي)
     * — مراجعة S1: كانت بتتحسب على أول بلد، فرسالة مصرية مش مقروءة كانت بتتعد في السعودية.
     */
    internal suspend fun waitingAsks(): List<PendingAsk> = SMS_RECORD_LOCK.withLock {
        if (off()) return@withLock emptyList()
        val state = waitingLocked()
        val senderOf = deps.inbox.sync().messages.associate { it.id to smsSenderKey(it.sender) }
        val mapped = deps.lanes.associate { it.spaceId to deps.inbox.senderWallets(it.spaceId).keys }
        fun spaceOfUnread(id: String): String {
            val sender = senderOf[id] ?: return ""
            val spaces = mapped.filterValues { sender in it }.keys
            return spaces.singleOrNull() ?: deps.lanes.singleOrNull()?.spaceId?.takeIf { spaces.isEmpty() } ?: ""
        }
        state.waiting.messageIds.map { id ->
            val (space, date) = state.where[id] ?: (spaceOfUnread(id) to null)
            PendingAsk(state.questions[id] ?: AskKind.SMS_WAITING, space, messageId = id, date = date)
        }
    }

    private suspend fun waitingLocked(): WaitingState {
        val all = deps.inbox.sync().messages.map { it.id }
        val handled = mutableSetOf<String>()
        val unknownShape = mutableSetOf<String>()
        val newShape = mutableSetOf<String>()
        val questions = mutableMapOf<String, AskKind>()
        val where = mutableMapOf<String, Pair<String, IsoDate?>>()
        val unmapped = forEachRoute({ true }) { lane, view ->
            for (line in view.ready + view.similar) {
                where.getOrPut(line.messageId) { lane.spaceId to line.date }
                line.question?.let { questions[line.messageId] = it }
            }
            for (line in view.ready) when {
                line.shape.clear -> handled += line.messageId
                line.waitReason == TextKey.SMS_WAIT_NEW_SHAPE -> newShape += line.messageId
                line.waitReason == TextKey.SMS_WAIT_UNKNOWN_SHAPE -> unknownShape += line.messageId
            }
            view.duplicates.forEach { handled += it.messageId }
        }
        // الرسايل اللي ما اتبنيلهاش معاينة (مرسل مالوش محفظة · مش مفعّل): بلدها = أول بلد قارئها قبلها
        for (lane in deps.lanes) {
            for (item in lane.review.inboxView().items) {
                val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
                where.getOrPut(item.id) { lane.spaceId to row.date }
            }
        }
        val waiting = all.filter { it !in handled }
        return WaitingState(
            SmsWaiting(waiting, waiting.filter { it in unknownShape }, waiting.filter { it in newShape }), unmapped, questions, where,
        )
    }
}
