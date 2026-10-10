package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.LocalMoment
import app.masroufy.core.hashContent
import app.masroufy.core.smsConfirmCandidate
import app.masroufy.port.DeviceNotifier
import app.masroufy.port.NoticeOutcome
import kotlin.coroutines.cancellation.CancellationException

/**
 * دورة الخلفية (OVERRIDES §72 · §61): (١) رسايل البنك الجديدة تتسجل لوحدها ([AutoRecordSms])، (٢) محرك التنبيهات يقرر على **كل** المرشحين
 * ومنهم «رسايل مستنية تأكيدك»، (٣) اللي المحرك قال يتبعت يروح لنظام الجوال ([DeviceNotifier]).
 * أندرويد بيشغّلها من `MasroufyCycleWorker` (بعد كل رسالة + دوريًا)، والتطبيق ممكن يشغّلها وهو مفتوح.
 *
 * - **المسجّل لوحده عمره ما بيطلّع إشعار** (رد المالك ٢): التسجيل ما بيكلّمش المحرك ولا الجوال، والمرشح الوحيد من الرسايل هو اللي **مستني**.
 * - **المحرك ما بيشتغلش من غير كل المرشحين** ([BackgroundCycleDeps.candidates] = null ⇒ ما بيشتغلش): المحرك بيشيل من الصفحة أي
 *   موضوع مش في المرشحين، فتشغيله بمرشح الرسايل لوحده كان هيمسح باقي الصفحة.
 * - فشل التسجيل ما بيوقفش التنبيهات (والعكس) — النتيجة بتقول إيه اللي فشل عشان العامل يعيد المحاولة.
 */
data class BackgroundCycleDeps(
    val sms: AutoRecordSms? = null,
    /** كل مرشحين النهارده من كل البلاد (`GatherAllSpaceAlerts`). null = المحرك مش متوصل. */
    val candidates: (suspend () -> List<AlertCandidate>)? = null,
    val engine: RunAlertEngine? = null,
    val notifier: DeviceNotifier? = null,
    /**
     * الشريحة S3 (§77-D): تصليح أزواج «اللي رجع» لكل بلد (`ReturnsWiring.repair`) — بعد التسجيل. التراجع في التطبيق القديم بيسيب رجل من
     * الزوج بتشاور على عملية اتمسحت (والأصلية متخبية «تحويل داخلي») لحد ما التصليح يشتغل. فشله ما بيوقفش حاجة (آمن التكرار — الدورة الجاية).
     * بيقرا العمليات المربوطة بس (`listReversalLinked`) — مش فترة عمليات كاملة كل دورة.
     */
    val reversalRepairs: List<RepairReversals> = emptyList(),
    /** §75-7 (S4): لحاق خصومات الاشتراكات — واحد لكل بلد — بعد تسجيل الرسايل وقبل تجميع المرشحين (الاشتراك اللي اتدفع ما يطلعش «متأخر»). */
    val subscriptions: List<MatchSubscriptions> = emptyList(),
)

data class NoticeDelivery(val shown: Int, val scheduled: Int, val blocked: Boolean, val unavailable: Boolean = false)

data class BackgroundCycleResult(
    val sms: AutoRecordResult?,
    val smsFailed: Boolean,
    val alerts: AlertRun?,
    val alertsFailed: Boolean,
    /** null = مفيش حاجة تتبعت أو مفيش منفذ. [NoticeDelivery.blocked] = الإشعارات مقفولة — الشاشة تقدر تقول للمستخدم. */
    val delivery: NoticeDelivery?,
    /** لحاق الاشتراكات فشل في بلد واحدة على الأقل (الباقي كمّل). */
    val subscriptionsFailed: Boolean = false,
)

class RunBackgroundCycle(private val deps: BackgroundCycleDeps) {
    suspend fun run(now: LocalMoment): BackgroundCycleResult {
        var smsFailed = false
        val sms = deps.sms?.let {
            try {
                it.run()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // مفيش تسجيل لنص رسالة أبدًا — الرسايل لسه في الصندوق والتشغيلة الجاية بتكمّل
                smsFailed = true
                null
            }
        }
        for (repair in deps.reversalRepairs) runCatchingNotCancel { repair.run() }
        var subscriptionsFailed = false
        for (match in deps.subscriptions) if (runCatchingNotCancel { match.catchUp(now.date) } == null) subscriptionsFailed = true
        val candidates = deps.candidates
        val engine = deps.engine
        if (candidates == null || engine == null) return BackgroundCycleResult(sms, smsFailed, null, false, null, subscriptionsFailed)

        var alertsFailed = false
        val run = try {
            val all = candidates().toMutableList()
            // مرشح الرسايل من الحالة **بعد** التسجيل — والمحرك بيشيل التكرار بالمفتاح لو التطبيق ضافه بنفسه في `GatherAlerts`
            val waiting = deps.sms?.let { s -> runCatchingNotCancel { s.waiting().messageIds } }.orEmpty()
            smsConfirmCandidate(waiting)?.let { all += it }
            engine.run(all, now)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            alertsFailed = true
            null
        }
        val delivery = run?.let { deliver(it) }
        return BackgroundCycleResult(sms, smsFailed, run, alertsFailed, delivery, subscriptionsFailed)
    }

    private suspend fun deliver(run: AlertRun): NoticeDelivery? {
        val notifier = deps.notifier ?: return null
        if (run.posts.isEmpty()) return null
        if (!notifier.available) return NoticeDelivery(0, 0, blocked = false, unavailable = true)
        if (!notifier.permitted()) return NoticeDelivery(0, 0, blocked = true)
        var shown = 0
        var scheduled = 0
        var blocked = false
        for (post in run.posts) {
            when (notifier.post(noticeTag(post), post.notice, post.at)) {
                NoticeOutcome.SHOWN -> shown++
                NoticeOutcome.SCHEDULED -> scheduled++
                NoticeOutcome.BLOCKED -> blocked = true
                NoticeOutcome.UNAVAILABLE -> Unit
            }
        }
        return NoticeDelivery(shown, scheduled, blocked)
    }

    private suspend fun <T> runCatchingNotCancel(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** معرّف الإشعار من مفاتيح التنبيهات **ببصمة** — المفاتيح فيها أسامي أطراف أحيانًا، والمعرّف بيبان في سجلات النظام. */
fun noticeTag(post: AlertPost): String = "alert-" + hashContent(post.eventKeys.joinToString("\n"))
