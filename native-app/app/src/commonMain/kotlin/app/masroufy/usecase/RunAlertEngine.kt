package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertDecision
import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertFactor
import app.masroufy.core.AlertGroup
import app.masroufy.core.KindStats
import app.masroufy.core.LocalMoment
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.SystemNotice
import app.masroufy.core.alertGroupLabel
import app.masroufy.core.alertReasonText
import app.masroufy.core.decideAlert
import app.masroufy.core.digestNotice
import app.masroufy.core.muted
import app.masroufy.core.mutedAlertDecision
import app.masroufy.core.systemNoticeFor
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AlertInteractionStore
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AlertSettingsStore
import app.masroufy.port.Clock
import app.masroufy.port.UsualHoursStore

/**
 * محرك التنبيهات على الجوال (OVERRIDES §61): بياخد مرشحين النهارده (`GatherAlerts`) ويقرر لكل واحد، ويحدّث صفحة الإشعارات.
 * - **المجموعة المقفولة بتفضل في الصفحة بس** (رد المالك §61): سطورها بتظهر في صفحة الإشعارات معلّمة «مقفولة» (`GROUP_OFF`)،
 *   **ولا شريط ولا نافذة**، ومن غير إيصال (ما اتبعتش) ولا عدّ في «بتفتحه/بتتجاهله». لو المستخدم فتحها تاني، الدرجة اللي لسه
 *   ما اتبعتتش بتتبعت عادي. والمحرك **عمره ما بيفتحها** بنفسه.
 * - **ما يتبعتش مرتين**: إيصال لكل `eventKey`. كل درجة تصعيد ليها مفتاح ⇒ قرّب ⇒ النهارده ⇒ عدّى، كل واحدة مرة.
 * - **اتحل ⇒ بيختفي**: سطر في الصفحة موضوعه مابقاش مرشح (القسط اتربط · الطرف اتقرر · السنة اتدفعت) بيتشال.
 * - الشريط بياخد **نص عام بس** ([SystemNotice]) — التفاصيل في الصفحة.
 * ⚠️ إرسال الإشعار فعلًا من أندرويد/الآيفون (والتشغيل في الخلفية) شريحة جاية؛ هنا بيرجع «ابعت إيه وإمتى».
 */
data class AlertEngineDeps(
    val settings: AlertSettingsStore,
    val interactions: AlertInteractionStore,
    val hours: UsualHoursStore,
    val receipts: AlertReceiptStore,
    val inbox: AlertInboxStore,
    val clock: Clock,
)

/** إشعار للشريط: النص العام + إمتى (null = دلوقتي) + مفاتيح التنبيهات اللي جواه. */
data class AlertPost(val notice: SystemNotice, val at: LocalMoment?, val eventKeys: List<String>)

data class AlertRun(
    val posts: List<AlertPost>,
    /** المتأخر: نافذة جوه التطبيق كمان لما يتفتح. */
    val inAppWindows: List<AlertInboxEntry>,
    /** المواضيع اللي اتحلت واتشالت من الصفحة. */
    val resolved: List<String>,
)

/** سطر جاهز للعرض: التفاصيل + «ليه اتبعت دلوقتي» + اسم المجموعة + [muted] = مجموعته مقفولة (في الصفحة بس). */
data class AlertInboxView(val entry: AlertInboxEntry, val reason: String, val group: String, val muted: Boolean = false)

class RunAlertEngine(private val deps: AlertEngineDeps) {
    suspend fun run(candidates: List<AlertCandidate>, now: LocalMoment): AlertRun {
        val off = deps.settings.disabledGroups()
        val live = candidates.filter { !it.kind.needsServer }.distinctBy { it.eventKey }
        val liveThreads = live.map { it.threadKey }.toSet()

        val resolved = deps.inbox.listAll().map { it.threadKey }.filter { it !in liveThreads }
        if (resolved.isNotEmpty()) deps.inbox.remove(resolved)

        val sent = deps.receipts.listAll().map { it.eventKey }.toSet()
        val stats = deps.interactions.load().toMutableMap()
        val hours = deps.hours.load()
        val nowIso = deps.clock.nowIso()
        val posts = mutableListOf<AlertPost>()
        val windows = mutableListOf<AlertInboxEntry>()
        val digest = mutableListOf<Pair<String, LocalMoment?>>()
        val receipts = mutableListOf<NotificationReceipt>()

        val kept = deps.inbox.listAll().map { it.threadKey }.toMutableSet()
        for (c in live) {
            if (c.kind.group in off) {
                // مقفولة ⇒ الصفحة بس: السطر بيتكتب (أو بيتحدث لدرجة أعلى) من غير شريط ولا إيصال ولا عدّ
                val current = deps.inbox.listAll().firstOrNull { it.threadKey == c.threadKey }
                if (current == null || current.eventKey != c.eventKey) {
                    deps.inbox.save(AlertInboxEntry(c.threadKey, c.eventKey, c.kind, c.flow, c.title, c.body, mutedAlertDecision(c.kind), nowIso))
                }
                kept += c.threadKey
                continue
            }
            if (c.eventKey in sent) {
                // اتبعت قبل كده واترجع تاني (دفعة اتفكت · مجموعة اتفتحت تاني) ⇒ يرجع للصفحة بس، من غير شريط
                if (kept.add(c.threadKey)) {
                    val quiet = AlertDecision(c.kind, AlertDelivery.INBOX_ONLY, null, false, listOf(AlertFactor.PAGE_ONLY))
                    deps.inbox.save(AlertInboxEntry(c.threadKey, c.eventKey, c.kind, c.flow, c.title, c.body, quiet, nowIso))
                }
                continue
            }
            val kindStats = stats[c.kind] ?: KindStats()
            val decision = decideAlert(c, kindStats, hours, now)
            val entry = AlertInboxEntry(c.threadKey, c.eventKey, c.kind, c.flow, c.title, c.body, decision, nowIso)
            deps.inbox.save(entry)
            receipts += NotificationReceipt(c.eventKey, null, now.date, nowIso)
            stats[c.kind] = kindStats.copy(shown = kindStats.shown + 1)
            when (decision.delivery) {
                AlertDelivery.SEND_NOW -> posts += AlertPost(systemNoticeFor(c.kind, c.flow), null, listOf(c.eventKey))
                AlertDelivery.AT_USUAL_TIME -> posts += AlertPost(systemNoticeFor(c.kind, c.flow), decision.deliverAt, listOf(c.eventKey))
                AlertDelivery.DIGEST -> digest += c.eventKey to decision.deliverAt
                AlertDelivery.INBOX_ONLY -> Unit
            }
            if (decision.inAppWindow) windows += entry
        }
        if (digest.isNotEmpty()) posts += AlertPost(digestNotice(), digest.first().second, digest.map { it.first })

        if (receipts.isNotEmpty()) deps.receipts.saveMany(receipts)
        for ((kind, s) in stats) deps.interactions.save(kind, s)
        return AlertRun(posts, windows, resolved)
    }

    /** صفحة الإشعارات: الأحدث الأول، وكل سطر معاه «ليه اتبعت دلوقتي»، و«مقفولة» لو مجموعته مقفولة دلوقتي. */
    suspend fun inbox(): List<AlertInboxView> {
        val off = deps.settings.disabledGroups()
        return deps.inbox.listAll().sortedWith(compareByDescending<AlertInboxEntry> { it.createdAt }.thenBy { it.threadKey })
            .map { AlertInboxView(it, alertReasonText(it.decision), alertGroupLabel(it.kind.group), it.decision.muted || it.kind.group in off) }
    }

    /** المستخدم فتح التنبيه (من الشريط أو الصفحة) ⇒ النوع ده «بيتفتح» — مرة واحدة لكل سطر. */
    suspend fun opened(threadKey: String) {
        val entry = deps.inbox.listAll().firstOrNull { it.threadKey == threadKey } ?: return
        if (entry.openedAt != null) return
        deps.inbox.save(entry.copy(openedAt = deps.clock.nowIso()))
        val s = deps.interactions.load()[entry.kind] ?: KindStats()
        deps.interactions.save(entry.kind, KindStats(maxOf(s.shown, s.opened + 1), s.opened + 1))
    }

    /** التطبيق اتفتح في الساعة دي ⇒ المحرك بيتعلم مواعيدك. على الجوال بس. */
    suspend fun appOpened(now: LocalMoment) {
        deps.hours.save(deps.hours.load().recordOpen(now.hour))
    }

    /** المستخدم بس اللي بيقفل أو يفتح مجموعة. */
    suspend fun setGroupEnabled(group: AlertGroup, enabled: Boolean) = deps.settings.setGroupEnabled(group, enabled)
}
