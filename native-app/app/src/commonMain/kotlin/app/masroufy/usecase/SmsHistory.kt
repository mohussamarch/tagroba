package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.SmsParseResult
import app.masroufy.core.dayIn
import app.masroufy.core.dayStartIn
import app.masroufy.port.Clock
import app.masroufy.port.UserSettingsStore
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * **§83-1 (المالك 2026-10-10): «لو اول مرة اضيفه يسألني اقرأ اللي فات ؟ ولو جاوبت ايوة المفروض كل اللي فات يتحمل في البرنامج»**.
 * - **السؤال** ([question]): بنوك مربوطة لسه ما اتسألتش في البلد دي (`SmsReadPoint.asked`) وعلى الجوال رسايل منها **أقدم** من مؤشر القراية.
 * - **من إمتى** (رد المالك ✅ «يسألني أنا من إمتى»): لو أقدم رسالة قبل رصيد بداية محفظة البنك ⇒ [SmsHistoryQuestion.askStart] والشاشة بتعرض
 *   أقدم رسالة ورصيد البداية والمالك بيختار اليوم.
 * - **«أيوه»** ([start]): الصندوق بيرجع يقرا من اليوم ده (`SmsInboxPort.readFrom`) **كل** رسايل البنوك المفعّلة، وبعدين كل اللي شكله اتعلّم بيتسجل
 *   (نفس خط الخلفية بالظبط — `AutoRecordSms`): وضع التعلّم §77-A زي ما هو، والدمج مع الكشف §75-10 (±2 يوم = عملية واحدة بمصدرين)، والمكرر بيتشال.
 * - **الدفعة الأولى مفهومة** ([report]): **قايمة واحدة** — رسالة واحدة من كل شكل جديد ومعاها «+N زيها»، و«أكّد الأشكال» ([confirmShapes]) بيسجّلها
 *   وبيعلّم أشكالها ⇒ الباقي بنفس الأشكال بيتسجل لوحده. مفيش إشعار لكل رسالة: مرشح التنبيه الوحيد للرسايل واحد للكل (`smsConfirmCandidate`).
 * - **«لأ، من دلوقتي بس»** ([decline]): البنوك دي اتسألت ومفيش قراية للي فات.
 */
data class SmsHistoryDeps(
    /** نفس الصندوق والحارات بتوع التسجيل لوحده في الخلفية. */
    val sms: AutoRecordSmsDeps,
    /** إعدادات الحساب — نقطة القراية لكل بلد (`SmsReadPoint`). */
    val settings: UserSettingsStore,
    val clock: Clock,
    /**
     * مراجعة 0.3 بند 7: قراية الصندوق كله واللحاق تقيلين — برّه خيط الواجهة. الاختبارات تقدر تحط منفّذ تاني.
     */
    val work: CoroutineContext = Dispatchers.Default,
)

/** المحفظة اللي رسايل البنك بتتسجل فيها ورصيد بدايتها. */
data class SmsOpening(val walletName: String, val openingAt: IsoDate)

/** «اقرأ اللي فات؟» على [senders] (زي ما هم مكتوبين في الصندوق) — أقدم رسالة منهم على الجوال يوم [oldestDay]. */
data class SmsHistoryQuestion(val spaceId: String, val senders: List<String>, val oldestDay: IsoDate, val opening: SmsOpening?) {
    /** فيه رسايل أقدم من رصيد بداية المحفظة ⇒ المالك يختار من إمتى (رد المالك §83). */
    val askStart: Boolean get() = opening != null && oldestDay < opening.openingAt
}

/** شكل رسالة جديد مستني تأكيد مرة: [messageId] = أحدث رسالة بالشكل ده (للعرض والتأكيد) · [count] = كل الرسايل بنفس الشكل في الصندوق. */
data class SmsShapeGroup(
    val spaceId: String,
    val sender: String,
    val messageId: String,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val merchant: String,
    val count: Int,
)

/**
 * [recorded] · [duplicates] = اللي اتسجل (أو اتدمج في عملية كشف) واللي اتشال مكرر في النداء ده. [shapes] = قايمة التأكيد (شكل واحد في السطر).
 * [waiting] = كل اللي مستني تأكيد المالك **في الصندوق كله** (r051 — كان الجزء المعروض بس فكان دايمًا «200») · [queued] = كل اللي في الصندوق ·
 * [shapes] «+N زيها» على الصندوق كله برضه · [unread] (مراجعة 0.3) = اللي في
 * الصندوق ورا الجزء المعروض (المالك لسه ما شافهاش — بتظهر لما يخلّص اللي قدامه).
 */
data class SmsHistoryReport(
    val recorded: Int,
    val duplicates: Int,
    val shapes: List<SmsShapeGroup>,
    val waiting: Int,
    val queued: Int,
    val unread: Int = 0,
) {
    /** الرسايل اللي هتتسجل لوحدها بعد «أكّد الأشكال» (غير الرسالة اللي في السطر). */
    val sameShape: Int get() = shapes.sumOf { it.count - 1 }
}

class SmsHistory(private val deps: SmsHistoryDeps) {
    private val auto = AutoRecordSms(deps.sms)
    private val points = SmsReadPoints(deps.settings, deps.clock)
    private val inbox get() = deps.sms.inbox

    /** السؤال على البلد [spaceId] دلوقتي، أو null (القراية واقفة · كل البنوك اتسألت · مفيش رسايل أقدم من اللي اتقري). من غير كتابة. */
    suspend fun question(spaceId: String): SmsHistoryQuestion? = withContext(deps.work) { questionNow(spaceId) }

    private suspend fun questionNow(spaceId: String): SmsHistoryQuestion? {
        val lane = deps.sms.lanes.firstOrNull { it.spaceId == spaceId } ?: return null
        if (!inbox.available) return null
        val state = inbox.sync()
        if (!state.enabled) return null
        val marks = inbox.readMarks() ?: return null
        val asked = points.all()[spaceId]?.asked.orEmpty().toSet()
        // fix053 (§112-5): مرسل البلد التانية (رسايله على شكلها، أو مربوط بمحفظة هناك بس) ما يتسألش عنه هنا
        lane.review.inboxView(state, null)
        val mine = inbox.senderWallets(spaceId).keys
        val theirs = deps.sms.lanes.filter { it.spaceId != spaceId }.flatMapTo(HashSet()) { inbox.senderWallets(it.spaceId).keys } - mine
        val fresh = state.senders.filter { smsSenderKey(it) !in asked && smsSenderKey(it) !in theirs && !lane.review.otherCountrySender(it) }
        if (fresh.isEmpty()) return null
        val oldest = inbox.oldestMessageAt(fresh) ?: return null
        // بنك اتضاف بعد الربط: رسايله اللي قبل المؤشر عمرها ما اتقريت ⇒ نفس السؤال
        if (millisOf(oldest) >= millisOf(marks.point)) return null
        // §88: اليوم بتوقيت البلد (القاهرة بالصيفي · الرياض)
        return SmsHistoryQuestion(spaceId, fresh, dayIn(lane.timeZone, oldest) ?: oldest.take(10), openingOf(lane, fresh))
    }

    /** المحفظة اللي رسايل البنوك دي بتتسجل فيها (الربط أو الحساب البنكي الوحيد) — الأقدم رصيد بداية لو أكتر من واحدة. */
    private suspend fun openingOf(lane: SmsLane, senders: List<String>): SmsOpening? {
        val wallets = lane.wallets.listAll()
        val ids = senders.mapNotNull { auto.walletOf(lane.spaceId, it) }.toSet()
        val wallet = wallets.filter { it.id in ids }.minByOrNull { it.openingAt } ?: return null
        return SmsOpening(wallet.name, wallet.openingAt)
    }

    /** «لأ، من دلوقتي بس»: البنوك دي اتسألت — رسايلهم من مؤشر القراية وبعده بس. */
    suspend fun decline(spaceId: String, senders: List<String>) {
        remember(spaceId, senders, null)
    }

    /**
     * «أيوه، اقراها» من أول يوم [from] (null = من أقدم رسالة على الجوال): المؤشر بيرجع لهناك والبلد بتتحفظ «اتسألت» **قبل** القراية (لو الشاشة
     * اتقفلت في النص، الخلفية بتكمّل من المؤشر)، وبعدين الصندوق بيتملى كله وكل اللي شكله اتعلّم بيتسجل.
     */
    suspend fun start(
        spaceId: String, senders: List<String>, from: IsoDate?, progress: ((SmsRunProgress) -> Unit)? = null, memo: SmsRunMemo? = null,
    ): SmsHistoryReport = withContext(deps.work) {
        // §88: أول اليوم بتوقيت البلد — مصر من 00:00 القاهرة (22:00 أو 21:00 جرينتش حسب الصيفي)، مش 21:00 ثابتة
        val zone = deps.sms.lanes.firstOrNull { it.spaceId == spaceId }?.timeZone ?: SAUDI_TIME_ZONE
        val point = if (from != null) dayStartIn(zone, from) else inbox.oldestMessageAt(senders)
        if (point != null) inbox.readFrom(point)
        remember(spaceId, senders, point)
        drain(0, progress, memo)
    }

    /**
     * «أكّد الأشكال»: الرسايل دي بتتسجل وأشكالها بتتعلّم (§77-A) ⇒ كل اللي بنفس الأشكال في الصندوق بيتسجل لوحده.
     * r051: [progress] بعد كل صفحة (العدّ بيتحرك والشغل شغال) · [memo] = فتح الشاشة بعدها بيقرا الجديد بس (مش لفة كاملة تانية).
     */
    suspend fun confirmShapes(
        messageIds: List<String>, progress: ((SmsRunProgress) -> Unit)? = null, memo: SmsRunMemo? = null,
    ): SmsHistoryReport = withContext(deps.work) {
        val confirmed = auto.confirm(messageIds)
        progress?.invoke(SmsRunProgress(confirmed, 0, inbox.window(null).count))
        drain(confirmed, progress, memo)
    }

    /** الصورة دلوقتي — من غير كتابة. */
    suspend fun report(): SmsHistoryReport = withContext(deps.work) { reportOf(0, 0) }

    private suspend fun remember(spaceId: String, senders: List<String>, point: String?) {
        val doc = points.all()[spaceId]
        val candidates = listOfNotNull(point, doc?.point, inbox.readMarks()?.point)
        val earliest = candidates.minByOrNull(::millisOf) ?: return
        points.save(spaceId, earliest, doc?.asked.orEmpty() + senders.map(::smsSenderKey))
    }

    /**
     * الصندوق بيتملى من الجوال كله (500 في المرة على الجهاز) وبعدين **لفة واحدة** بالمؤشر على الصندوق كله بتسجّل كل اللي شكله اتعلّم.
     * r051: كانت لفات لحد ما ما يتسجلش حاجة (لحد 500 لفة × صفحات بتتعاد) ⇒ أكتر من 5 دقايق على 3,600 رسالة. اللي ما لحقش قبل [BUDGET]
     * (أو وصل في النص) بيتسجل في التشغيلة الجاية (الخلفية · فتح الشاشة).
     */
    private suspend fun drain(already: Int, progress: ((SmsRunProgress) -> Unit)?, memo: SmsRunMemo?): SmsHistoryReport {
        for (pull in 0 until MAX_PULLS) if (!inbox.sync().more) break
        val deadline = TimeSource.Monotonic.markNow() + BUDGET
        val run = auto.record({ p -> progress?.invoke(p.copy(recorded = already + p.recorded)) }, memo, deadline)
        return reportOf(already + (run?.recorded ?: 0), run?.duplicates ?: 0)
    }

    private suspend fun reportOf(recorded: Int, duplicates: Int): SmsHistoryReport {
        val found = auto.waitingWithAsks()
        val box = inbox.sync()
        val unread = (box.count - box.messages.size).coerceAtLeast(0)
        val (state, asks) = found ?: return SmsHistoryReport(recorded, duplicates, emptyList(), 0, box.count, unread)
        val newShape = state.waiting.newShape.toSet()
        // «ده راتبك؟» محتاج رده — التأكيد من غيره ما بيسجلش (§75-2) ⇒ مش في قايمة الأشكال
        val salary = asks.filter { it.kind == AskKind.IS_SALARY }.mapNotNullTo(HashSet()) { it.messageId }
        val groups = LinkedHashMap<Triple<String, String, String>, MutableList<Pair<InboxItem, app.masroufy.core.SmsRow>>>()
        val placed = HashSet<String>()
        for (lane in deps.sms.lanes) {
            if (newShape.isEmpty()) break
            for (item in (state.views[lane.spaceId] ?: lane.review.inboxView(state.box, null)).items) {
                if (item.id !in newShape || item.id in salary || item.id in placed) continue
                val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
                val key = row.learnKey ?: continue
                placed += item.id
                groups.getOrPut(Triple(lane.spaceId, smsSenderKey(item.sender), key)) { mutableListOf() } += item to row
            }
        }
        val behind = if (groups.isEmpty()) emptyMap() else sameShapeBehind(state, groups.keys)
        val shapes = groups.map { (key, list) ->
            val (item, row) = list.maxWith(compareBy({ millisOf(it.first.receivedAt) }, { it.first.id }))
            SmsShapeGroup(key.first, item.sender, item.id, row.date, row.amountMinor, row.direction, row.merchantName, list.size + (behind[key] ?: 0))
        }
        return SmsHistoryReport(recorded, duplicates, shapes, state.waiting.total, box.count, unread)
    }

    /**
     * r051: «+N زيها» على الصندوق كله مش الـ200 بتوع الصفحة — الرسايل اللي **ورا** الصفحة بنفس (البلد · المرسل · الشكل) بالفهم بس
     * (من غير معاينة)، صفحة صفحة بالمؤشر. ده نفس معنى «زيها» (نفس الشكل)؛ المكرر منها بيتشال بعد التأكيد بدل ما يتسجل.
     */
    private suspend fun sameShapeBehind(state: WaitingState, keys: Set<Triple<String, String, String>>): Map<Triple<String, String, String>, Int> {
        val out = HashMap<Triple<String, String, String>, Int>()
        var box = state.box
        for (pass in 0 until MAX_PULLS) {
            if (box.messages.size >= box.count) break
            box = inbox.window(oldestOf(box) ?: break)
            if (box.messages.isEmpty()) break
            val placed = HashSet<String>()
            for (lane in deps.sms.lanes) {
                for (item in lane.review.inboxView(box, null).items) {
                    if (item.id in placed) continue
                    val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
                    placed += item.id
                    val key = Triple(lane.spaceId, smsSenderKey(item.sender), row.learnKey ?: continue)
                    if (key in keys) out[key] = (out[key] ?: 0) + 1
                }
            }
        }
        return out
    }

    private companion object {
        /** حد أمان: 1000 × 500 رسالة. */
        const val MAX_PULLS = 1_000

        /** r051: حد وقت لفة التأكيد — الباقي في التشغيلة الجاية. */
        val BUDGET = 3.minutes
    }
}

/** للعرض والاختبار: معرّفات رسايل قايمة التأكيد. */
val SmsHistoryReport.shapeMessageIds: List<Id> get() = shapes.map { it.messageId }
