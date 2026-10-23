package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.PendingAsk
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeMark

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
 * - **r051 (OVERRIDES §102):** التشغيلة بتمشي الصندوق **صفحة ورا صفحة بالمؤشر** (`SmsInboxPort.window` — كل رسالة بتتعاين وتتسجل مرة واحدة
 *   في التشغيلة، كانت نفس الصفحة بتتعاين وتتكتب تاني بعد كل رسالة بتتسجل) و**القفل لكل صفحة** مش للتشغيلة كلها (ضغطة المالك بتاخد دورها
 *   بعد صفحة واحدة بالكتير).
 */

data class AutoRecordSmsDeps(
    /** الصندوق نفسه (الجهاز) — المزامنة ومحفظة كل بنك والأشكال اللي اتعلّمت. */
    val inbox: SmsInboxPort,
    /** البلاد بالترتيب. رسالة بلد بتترفض من قارئ البلد التانية (العملة) فبتتسجل في بلدها بس. */
    val lanes: List<SmsLane>,
    /** r041c: قراية الصندوق وفهمه ومطابقته وتسجيله برّه خيط الواجهة مهما مين ناداه (الشاشة · المساعد · الخلفية). الاختبار بيدّي سياقه. */
    val work: CoroutineContext = Dispatchers.Default,
)

enum class AutoRecordStatus {
    /** اشتغل. */
    RAN,

    /** القراية مقفولة أو الجهاز مالوش رسايل (الآيفون) ⇒ ولا حاجة اتعملت ومفيش حاجة «مستنية». */
    OFF,
}

/** [total] (r051) = كل اللي مستني في الصندوق (مش الصفحة بس) — [messageIds] = اللي منهم في أحدث صفحة. */
data class SmsWaiting(
    val messageIds: List<String>, val unknownShape: List<String> = emptyList(), val newShape: List<String> = emptyList(), val total: Int = messageIds.size,
)

/** مرسل (بنك) في بلد ورسايله مفهومة بس مالهاش محفظة: البلد فيها أكتر من حساب بنك (أو مفيش) والمالك لسه ما اختارش. */
data class UnmappedSender(val spaceId: String, val sender: String, val messages: Int)

/** r051: تقدم تشغيلة طويلة بعد كل صفحة — اللي اتسجل (أو اتدمج) · المكرر اللي اتشال · اللي لسه في الصندوق. */
data class SmsRunProgress(val recorded: Int, val duplicates: Int, val queued: Int)

class AutoRecordSms(private val deps: AutoRecordSmsDeps) {
    private val routes = SmsRouteWalk(deps)

    val available: Boolean get() = deps.inbox.available

    private fun laneOf(spaceId: String): SmsLane =
        deps.lanes.firstOrNull { it.spaceId == spaceId } ?: throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))

    /** r041c: على [AutoRecordSmsDeps.work] وجوه [SMS_RECORD_LOCK]. r051: للحاجات القصيرة بس — التشغيلة بتمسك القفل صفحة صفحة. */
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(deps.work) { SMS_RECORD_LOCK.withLock { block() } }

    private suspend fun off(): Boolean = !deps.inbox.available || !deps.inbox.sync().enabled

    /**
     * S2: الهدف اللي الشاشة بتحمّل بيه رسايل محفظة [walletId] (`ReviewSmsInbox.load`) — نفس اللي التسجيل في الخلفية بيبنيه بالظبط (محفظة
     * الكاش وأرقام الحسابات التانية — `targetOf` في `SmsRouting.kt`)، فسبب الانتظار اللي الشاشة بتعرضه هو نفسه. null = المحفظة مش في البلد دي.
     */
    suspend fun targetFor(spaceId: String, walletId: Id): SmsReviewTarget? {
        val lane = laneOf(spaceId)
        val all = lane.wallets.listAll()
        return all.firstOrNull { it.id == walletId }?.let { targetOf(it, all, lane.movesCashWithdrawals) }
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
    suspend fun unmappedSenders(): List<UnmappedSender> = locked { if (off()) emptyList() else routes.waiting().unmapped }

    /**
     * التشغيلة: كل بلد وكل بنك وكل محفظة بالدور يسجّل الجديد **اللي شكله اتعلّم** ويشيل المكرر، وبعدين حساب «مستنية» على اللي فاضل.
     * [memo] (r051) = التشغيلة الكاملة دي بتتفتكر ⇒ «اسحب لتحت» وفتح الشاشة بعدها بيقروا الجديد بس.
     */
    suspend fun run(progress: ((SmsRunProgress) -> Unit)? = null, memo: SmsRunMemo? = null): AutoRecordResult = withContext(deps.work) {
        val walked = record(progress, memo) ?: return@withContext AutoRecordResult(AutoRecordStatus.OFF)
        val state = locked { routes.waiting() }
        // بيانات الاستخدام (PLAN-0.6 §16 «الرسايل»): نسبة القراية و«شكل جديد مستني» لكل بنك — رقم المرسل العام بس
        usageOfSmsRun(state)
        val w = state.waiting
        walked.copy(waiting = w.messageIds, unmappedSenders = state.unmapped, unknownShape = w.unknownShape, newShape = w.newShape)
    }

    /**
     * r051: التسجيل بس (من غير حساب «مستنية») — لفة كاملة على الصندوق صفحة صفحة. [deadline] = حد الوقت (اللي ما لحقش بيتسجل في التشغيلة
     * الجاية). null = القراية مقفولة.
     */
    internal suspend fun record(progress: ((SmsRunProgress) -> Unit)? = null, memo: SmsRunMemo? = null, deadline: TimeMark? = null): AutoRecordResult? =
        withContext(deps.work) {
            val mark = locked {
                val box = if (deps.inbox.available) deps.inbox.sync() else null
                if (box == null || !box.enabled) null else if (memo != null) smsMarkOf(deps.inbox, deps.lanes, box) else ""
            } ?: return@withContext null
            val (walked, cut) = walk(memo, newOnly = false, progress = progress, deadline = deadline)
            // مراجعة r051: اللفة اللي حد الوقت قطعها مش كاملة ⇒ من غير علامة، فأول فتح للشاشة أو «اسحب لتحت» بيكمّل الصفحات اللي ما اتلفتش
            if (memo != null) locked { memo.mark = if (cut) null else mark }
            walked
        }

    /** r051: لفة تسجيل كاملة من غير حساب «مستنية» (بعد تأكيد المالك على الشاشة) — بيرجّع عدد اللي اتسجل. */
    suspend fun sweep(memo: SmsRunMemo? = null): Int = record(memo = memo)?.recorded ?: 0

    /**
     * r041c — «اسحب لتحت»: نفس تسجيل [run] بالظبط بس على الرسايل **الجديدة** (اللي ما اتعاينتش قبل كده في [memo]) ومن غير حساب «مستنية»
     * (الشاشة ما بتعرضهوش). حاجة اتغيرت من آخر مرة (`smsMarkOf`) ⇒ لفة كاملة زي [run] عشان المستنية اللي بقت تتسجل تتسجل؛ غير كده المستنية
     * بتفضل لتشغيلة الخلفية الكاملة ([run]). مفيش جديد ⇒ قراية الصندوق مرة وخلاص (ولا فهم ولا معاينة).
     */
    suspend fun runNew(memo: SmsRunMemo): AutoRecordResult = withContext(deps.work) {
        val mark = locked {
            val box = if (deps.inbox.available) deps.inbox.sync() else null
            if (box == null || !box.enabled) null else smsMarkOf(deps.inbox, deps.lanes, box)
        } ?: return@withContext AutoRecordResult(AutoRecordStatus.OFF)
        val (walked, _) = walk(memo, newOnly = mark == memo.mark)
        locked { memo.mark = mark }
        walked
    }

    /**
     * r041c — نفس [unmappedSenders] (نفس التوزيع `routeSender` على نفس الرسايل) **من غير معاينة ولا تسجيل**: «إعداد القراءة» كانت بتستنى معاينة
     * كل محفظة — وتستنى التشغيلة كلها لو شغالة — فالصفحة تفضل بيضا. التشغيلة شغالة ⇒ ما بنستناهاش (قراية بس، مفيش كتابة).
     */
    suspend fun unmappedQuick(): List<UnmappedSender> = withContext(deps.work) {
        if (off()) emptyList() else routes.forEachRoute(deps.inbox.window(null), { true }, previews = false) { _, _, _ -> }.unmapped
    }

    /**
     * تسجيل الصفحات. r051: بالمؤشر (`SmsInboxPort.window`) من الأحدث للأقدم — كل رسالة بتتعاين مرة في التشغيلة، والصفحة اللي اتسجل منها
     * حاجة ما بتتعاينش تاني (كانت بتتعاين وتتكتب بعد كل رسالة بتتسجل ⇒ دقايق وآلاف الكتابات). القفل لكل صفحة (معاينة + حفظ + شيل).
     * [memo] ≠ null ⇒ بيفتكر اللي اتعاين · [newOnly] ⇒ الجديد بس، ويقف أول ما صفحة ما يبقاش فيها جديد.
     * التاني في النتيجة = [deadline] قطع اللفة قبل آخر صفحة.
     */
    private suspend fun walk(
        memo: SmsRunMemo?, newOnly: Boolean, progress: ((SmsRunProgress) -> Unit)? = null, deadline: TimeMark? = null,
    ): Pair<AutoRecordResult, Boolean> {
        var cut = false
        var recorded = 0
        var duplicates = 0
        val ids = mutableListOf<Id>()
        var before: QueuedSms? = null
        for (pass in 0 until MAX_PAGE_PASSES) {
            val step = SMS_RECORD_LOCK.withLock {
                val box = deps.inbox.window(before)
                if (box.messages.isEmpty()) return@withLock null
                val page = box.messages.map { it.id }
                val fresh = if (newOnly && memo != null) page.filterNot { it in memo.seen }.toSet() else null
                if (fresh != null && fresh.isEmpty()) return@withLock null
                var pageRecorded = 0
                var pageDuplicates = 0
                val routed = routes.forEachRoute(box, { true }, ids = fresh) { lane, _, done ->
                    // من غير اختيارات: الجديد **اللي شكله معروف واتعلّم** بس، والتصنيف المقترح يفضل مقترح، والشبيه والتعارض ما بيتلمسوش
                    val outcome = lane.review.recordLocked(emptyMap(), emptyList(), clearOnly = true, ackLater = done, verified = true)
                    pageRecorded += outcome.recorded
                    pageDuplicates += outcome.duplicates
                    for (batch in outcome.batchIds) ids += lane.sources.listByBatch(batch).mapNotNull { it.transactionId }
                }
                if (memo != null) memo.seen += page
                recorded += pageRecorded
                duplicates += pageDuplicates
                Triple(box, (box.count - routed.acknowledged).coerceAtLeast(0), oldestOf(box))
            } ?: break
            val (box, queued, oldest) = step
            progress?.invoke(SmsRunProgress(recorded, duplicates, queued))
            if (box.messages.size >= box.count || oldest == null) break
            if (deadline != null && deadline.hasPassedNow()) {
                cut = true
                break
            }
            before = oldest
        }
        return AutoRecordResult(AutoRecordStatus.RAN, recorded, duplicates, recordedTransactionIds = ids) to cut
    }

    /**
     * §77-A — **تأكيد المالك** لرسايل مستنية (من غير الشاشة): بتتسجل في محفظتها (الجديد · والشبيه لأنه اختاره) **وأشكالها بتتعلّم**، فرسايل
     * الشكل ده الجاية (واللي مستنية) بتتسجل لوحدها. المكرر بيتشال من غير ما يعلّم. بيرجّع عدد اللي اتسجل.
     * §75-2 (مراجعة S1): الرسالة اللي عليها «ده راتبك؟» محتاجة الرد [isSalary] (بيتحفظ لمرسلها ويتطبق عليها وهي بتتسجل)؛ من غيره **ما
     * بتتسجلش** وبتفضل مستنية بسؤالها — كانت بتتسجل «مش متصنف» والسؤال يرجع للرسالة الجاية، والنص بيقول «أكّد مرة إنه راتبك».
     * r051: الرسايل بتتدوّر في صفحات الصندوق بالمؤشر (من غير صفحة الشاشة) — والمعاينة على الرسايل دي بس.
     */
    suspend fun confirm(
        messageIds: List<String>, isSalary: Boolean? = null, similarToo: Boolean = true, categories: Map<String, Id> = emptyMap(), recordedIds: MutableList<Id>? = null,
    ): Int = locked {
        if (off()) return@locked 0
        val left = messageIds.toMutableSet()
        var recorded = 0
        var before: QueuedSms? = null
        for (pass in 0 until MAX_PAGE_PASSES) {
            if (left.isEmpty()) break
            val box = deps.inbox.window(before)
            if (box.messages.isEmpty()) break
            val here = box.messages.mapNotNullTo(HashSet()) { m -> m.id.takeIf { it in left } }
            if (here.isNotEmpty()) {
                routes.forEachRoute(box, { it.id in here }, ids = here) { lane, view, done ->
                    val similar = if (!similarToo) emptyList() else view.similar.filter { it.state == MatchingState.SIMILAR }.map { it.lineNumber }
                    // r05 (4a): التصنيف اللي المالك اختاره على الرسالة بيتحفظ مؤكد · [recordedIds] = عملياتها (لوحة جهة الشغل والطرف بعد التسجيل)
                    val chosen = (view.ready + view.similar).mapNotNull { l -> categories[l.messageId]?.let { l.lineNumber to it } }.toMap()
                    val outcome = lane.review.recordLocked(chosen, similar, clearOnly = false, salaryAnswer = isSalary, ackLater = done, verified = true)
                    recorded += outcome.recorded
                    if (recordedIds != null) for (b in outcome.batchIds) recordedIds += lane.sources.listByBatch(b).mapNotNull { it.transactionId }
                }
                left -= here
            }
            before = oldestOf(box) ?: break
            if (box.messages.size >= box.count) break
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
    suspend fun waiting(): SmsWaiting = locked { if (off()) SmsWaiting(emptyList()) else routes.waiting().waiting }

    /** PLAN-0.6 §13 (ج): حالة الصندوق (مفعّل · الإذن · أحدث صفحة) تحت نفس القفل — لإشعارات حالة القراية. null = الجهاز مالوش رسايل. */
    suspend fun inboxState(): app.masroufy.port.SmsInboxState? = if (!deps.inbox.available) null else locked { deps.inbox.sync() }

    /**
     * سؤال لكل رسالة مستنية (`SmsAskSource`): «ده راتبك؟» لو عليها، وإلا «مستنية تأكيدك» — بلدها ويومها لو اتقرت. الرسالة اللي ولا قارئ
     * فهمها: بلدها = البلد اللي المالك ربط فيها البنك ده بمحفظة (لو بلد واحدة)، أو البلد الوحيدة؛ غير كده **مش معروفة** (`spaceId` فاضي)
     * — مراجعة S1: كانت بتتحسب على أول بلد، فرسالة مصرية مش مقروءة كانت بتتعد في السعودية.
     */
    internal suspend fun waitingAsks(): List<PendingAsk> = locked { if (off()) emptyList() else asksOf(routes.waiting()) }

    /** r051 — «مستنية» وأسئلتها في حسبة واحدة (تقرير «اقرأ اللي فات؟» كان بيحسبها مرتين ويقرا الصندوق ويفهمه تاني). null = القراية مقفولة. */
    internal suspend fun waitingWithAsks(): Pair<WaitingState, List<PendingAsk>>? = locked {
        if (off()) null else routes.waiting().let { it to asksOf(it) }
    }

    private suspend fun asksOf(state: WaitingState): List<PendingAsk> {
        val senderOf = state.box.messages.associate { it.id to smsSenderKey(it.sender) }
        val mapped = deps.lanes.associate { it.spaceId to deps.inbox.senderWallets(it.spaceId).keys }
        fun spaceOfUnread(id: String): String {
            val sender = senderOf[id] ?: return ""
            val spaces = mapped.filterValues { sender in it }.keys
            return spaces.singleOrNull() ?: deps.lanes.singleOrNull()?.spaceId?.takeIf { spaces.isEmpty() } ?: ""
        }
        return state.waiting.messageIds.map { id ->
            val (space, date) = state.where[id] ?: (spaceOfUnread(id) to null)
            PendingAsk(state.questions[id] ?: AskKind.SMS_WAITING, space, messageId = id, date = date)
        }
    }
}
