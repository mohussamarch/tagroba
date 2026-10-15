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
 * [waiting] = كل اللي مستني تأكيد المالك في الجزء المعروض من الصندوق · [queued] = كل اللي في الصندوق · [unread] (مراجعة 0.3) = اللي في
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
    suspend fun question(spaceId: String): SmsHistoryQuestion? {
        val lane = deps.sms.lanes.firstOrNull { it.spaceId == spaceId } ?: return null
        if (!inbox.available) return null
        val state = inbox.sync()
        if (!state.enabled) return null
        val marks = inbox.readMarks() ?: return null
        val asked = points.all()[spaceId]?.asked.orEmpty().toSet()
        val fresh = state.senders.filter { smsSenderKey(it) !in asked }
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
    suspend fun start(spaceId: String, senders: List<String>, from: IsoDate?): SmsHistoryReport {
        // §88: أول اليوم بتوقيت البلد — مصر من 00:00 القاهرة (22:00 أو 21:00 جرينتش حسب الصيفي)، مش 21:00 ثابتة
        val zone = deps.sms.lanes.firstOrNull { it.spaceId == spaceId }?.timeZone ?: SAUDI_TIME_ZONE
        val point = if (from != null) dayStartIn(zone, from) else inbox.oldestMessageAt(senders)
        if (point != null) inbox.readFrom(point)
        remember(spaceId, senders, point)
        return drain(0)
    }

    /** «أكّد الأشكال»: الرسايل دي بتتسجل وأشكالها بتتعلّم (§77-A) ⇒ كل اللي بنفس الأشكال في الصندوق بيتسجل لوحده. */
    suspend fun confirmShapes(messageIds: List<String>): SmsHistoryReport = drain(auto.confirm(messageIds))

    /** الصورة دلوقتي — من غير كتابة. */
    suspend fun report(): SmsHistoryReport = reportOf(0, 0)

    private suspend fun remember(spaceId: String, senders: List<String>, point: String?) {
        val doc = points.all()[spaceId]
        val candidates = listOfNotNull(point, doc?.point, inbox.readMarks()?.point)
        val earliest = candidates.minByOrNull(::millisOf) ?: return
        points.save(spaceId, earliest, doc?.asked.orEmpty() + senders.map(::smsSenderKey))
    }

    /** الصندوق بيتملى من الجوال كله (500 في المرة على الجهاز) وبعدين التسجيل لوحده لحد ما ما يبقاش فيه حاجة تتسجل. */
    private suspend fun drain(already: Int): SmsHistoryReport {
        var recorded = already
        var duplicates = 0
        for (round in 0 until MAX_ROUNDS) {
            for (pull in 0 until MAX_PULLS) if (!inbox.sync().more) break
            val run = auto.run()
            if (run.status != AutoRecordStatus.RAN) break
            recorded += run.recorded
            duplicates += run.duplicates
            if (run.recorded == 0 && run.duplicates == 0) break
        }
        return reportOf(recorded, duplicates)
    }

    private suspend fun reportOf(recorded: Int, duplicates: Int): SmsHistoryReport {
        val waiting = auto.waiting()
        val newShape = waiting.newShape.toSet()
        // «ده راتبك؟» محتاج رده — التأكيد من غيره ما بيسجلش (§75-2) ⇒ مش في قايمة الأشكال
        val salary = if (newShape.isEmpty()) emptySet() else auto.waitingAsks().filter { it.kind == AskKind.IS_SALARY }.mapNotNull { it.messageId }.toSet()
        val groups = LinkedHashMap<Triple<String, String, String>, MutableList<Pair<InboxItem, app.masroufy.core.SmsRow>>>()
        val placed = mutableSetOf<String>()
        for (lane in deps.sms.lanes) {
            if (newShape.isEmpty()) break
            for (item in lane.review.inboxView().items) {
                if (item.id !in newShape || item.id in salary || item.id in placed) continue
                val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
                val key = row.learnKey ?: continue
                placed += item.id
                groups.getOrPut(Triple(lane.spaceId, smsSenderKey(item.sender), key)) { mutableListOf() } += item to row
            }
        }
        val shapes = groups.map { (key, list) ->
            val (item, row) = list.maxWith(compareBy({ millisOf(it.first.receivedAt) }, { it.first.id }))
            SmsShapeGroup(key.first, item.sender, item.id, row.date, row.amountMinor, row.direction, row.merchantName, list.size)
        }
        val box = inbox.sync()
        return SmsHistoryReport(recorded, duplicates, shapes, waiting.messageIds.size, box.count, (box.count - box.messages.size).coerceAtLeast(0))
    }

    private companion object {
        /** حد أمان: 1000 × 500 رسالة. */
        const val MAX_PULLS = 1_000
        const val MAX_ROUNDS = 500
    }
}

/** للعرض والاختبار: معرّفات رسايل قايمة التأكيد. */
val SmsHistoryReport.shapeMessageIds: List<Id> get() = shapes.map { it.messageId }
