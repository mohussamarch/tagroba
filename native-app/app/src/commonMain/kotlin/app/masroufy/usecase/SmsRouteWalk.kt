package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.IsoDate
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.isoInstantMillis
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey

/**
 * r051 — توزيع **صفحة واحدة** من الصندوق على (بلد · بنك · محفظة) وحساب «مستنية» (اتنقلوا من `AutoRecordSms.kt`). الصفحة بتتقري مرة
 * (`SmsInboxPort.window`) وبتتفهم مرة لكل بلد، وكل محفظة بتتعاين منها — كانت كل محفظة بتقرا الصندوق وتفهم الصفحة من الأول.
 * كله بيتنادى **جوه** [SMS_RECORD_LOCK] (اللي بينادي ماسكه).
 */

/** اللي مستني دلوقتي + فين ومتى كل رسالة وسؤالها (للأسئلة `SmsAskSource`). [views] = فهم الصفحة لكل بلد (للي بيبني عليها من غير فهم تاني). */
internal class WaitingState(
    val waiting: SmsWaiting,
    val unmapped: List<UnmappedSender>,
    val questions: Map<String, AskKind>,
    val where: Map<String, Pair<String, IsoDate?>>,
    val box: SmsInboxState,
    val views: Map<String, InboxView>,
)

/** نتيجة صفحة: البنوك اللي مالهاش محفظة + عدد الرسايل اللي خلصت واتشالت من الصندوق. */
internal class Routed(val unmapped: List<UnmappedSender>, val acknowledged: Int)

/** ترتيب الصندوق (وقت الوصول · المعرّف) — أقدم رسالة في الصفحة = بداية الصفحة اللي بعدها (`SmsInboxPort.window`). */
private val INBOX_ORDER = compareBy<QueuedSms>({ isoInstantMillis(it.receivedAt) ?: Long.MIN_VALUE }, { it.id })

internal fun oldestOf(box: SmsInboxState): QueuedSms? = box.messages.minWithOrNull(INBOX_ORDER)

internal class SmsRouteWalk(private val deps: AutoRecordSmsDeps) {
    /**
     * كل (بلد · محفظة) في الصفحة [box] ورسايلها المفهومة من المرسلين **اللي المالك مفعّلهم** (دفاع تاني: لو شال بنك من القايمة ورسايله لسه
     * في الصندوق، ما بتتسجلش لوحدها) — [visit] بياخد معاينة كل محفظة. [ids] = الرسايل دي بس اللي بتتفهم وتتعاين (null = الصفحة كلها) ·
     * [previews] false = التوزيع والعدّ بس · [views] = فهم الصفحة لكل بلد لو اتعمل قبل كده.
     */
    suspend fun forEachRoute(
        box: SmsInboxState,
        only: (InboxItem) -> Boolean,
        ids: Set<String>? = null,
        previews: Boolean = true,
        views: MutableMap<String, InboxView>? = null,
        visit: suspend (SmsLane, SmsReview, MutableList<String>) -> Unit,
    ): Routed {
        val unmapped = mutableListOf<UnmappedSender>()
        // مراجعة r04: تحويل بين محفظتين من محافظ المالك = مسارين (رِجل في كل محفظة) — الرسالة بتتشال من الصندوق بعد ما **كل** مساراتها
        // تخلص، مش بعد أول رِجل (التانية كانت بتضيع: الصندوق ما بقاش بيرجّعها للمحفظة التانية)
        val expected = HashMap<String, Int>()
        val done = mutableListOf<String>()
        for (lane in deps.lanes) {
            val view = views?.get(lane.spaceId) ?: lane.review.inboxView(box, ids).also { views?.put(lane.spaceId, it) }
            val enabled = view.senders.map(::smsSenderKey).toSet()
            val items = view.items.filter { it.parsed is SmsParseResult.Ok && only(it) }
            val senders = items.map { smsSenderKey(it.sender) }.distinct().filter { it in enabled }
            if (senders.isEmpty()) continue
            val mapping = deps.inbox.senderWallets(lane.spaceId)
            val wallets = lane.wallets.listAll()
            for (sender in senders) {
                val routes = routeSender(wallets, sender, mapping, items, lane.movesCashWithdrawals)
                if (routes.unrouted > 0) unmapped += UnmappedSender(lane.spaceId, sender, routes.unrouted)
                if (!previews) continue
                for (route in routes.routes) {
                    route.messageIds.forEach { expected[it] = (expected[it] ?: 0) + 1 }
                    visit(lane, lane.review.loadFor(route.target, view) { it.id in route.messageIds }, done)
                }
            }
        }
        val finished = done.groupingBy { it }.eachCount().filter { (id, n) -> n >= (expected[id] ?: 1) }.keys.toList()
        if (finished.isNotEmpty()) deps.inbox.acknowledge(finished)
        return Routed(unmapped, finished.size)
    }

    /**
     * اللي مستني قرار المالك دلوقتي في **أحدث صفحة** — من غير ما يكتب حاجة: كل رسالة ما عدا اللي هتتسجل لوحدها (جديدة وشكلها معروف واتعلّم)
     * أو هتتشال (مكررة). r051: [SmsWaiting.total] = العدد الحقيقي في الصندوق كله (اللي ورا الصفحة مستني برضه — التشغيلة الكاملة سجّلت كل
     * اللي ينفع يتسجل)، مش الـ200 بتوع الصفحة.
     */
    suspend fun waiting(): WaitingState {
        val box = deps.inbox.window(null)
        val all = box.messages.map { it.id }
        val handled = HashSet<String>()
        val unknownShape = HashSet<String>()
        val newShape = HashSet<String>()
        val questions = HashMap<String, AskKind>()
        val where = HashMap<String, Pair<String, IsoDate?>>()
        val views = HashMap<String, InboxView>()
        val unmapped = forEachRoute(box, { true }, views = views) { lane, view, _ ->
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
        }.unmapped
        // الرسايل اللي ما اتبنيلهاش معاينة (مرسل مالوش محفظة · مش مفعّل): بلدها = أول بلد قارئها قبلها
        for (lane in deps.lanes) {
            for (item in (views[lane.spaceId] ?: lane.review.inboxView(box, null)).items) {
                val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
                where.getOrPut(item.id) { lane.spaceId to row.date }
            }
        }
        val waiting = all.filter { it !in handled }
        val total = waiting.size + (box.count - box.messages.size).coerceAtLeast(0)
        return WaitingState(
            SmsWaiting(waiting, waiting.filter { it in unknownShape }, waiting.filter { it in newShape }, total),
            unmapped, questions, where, box, views,
        )
    }
}
