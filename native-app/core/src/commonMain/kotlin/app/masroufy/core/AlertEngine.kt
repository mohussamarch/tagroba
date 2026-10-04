package app.masroufy.core

/**
 * محرك التنبيهات (OVERRIDES §61) — **مفيش أرقام ثابتة** (ساعات هدوء ولا حد يومي): المحرك بيقرر لكل تنبيه
 * يتبعت دلوقتي · يستنى وقتك المعتاد · يتجمّع في ملخص · يفضل في الصفحة بس — من إلحاحه، وحجمه بالنسبة لشهرك،
 * ومحتاج قرار ولا لأ، وتفاعلك مع النوع ده قبل كده، والساعات اللي بتفتح فيها التطبيق عادة.
 * التعلم ده **على الجوال بس** ومش بيتزامن. دالة نقية: نفس المدخل ⇒ نفس القرار.
 * الحدود تحت **قرارات تنفيذ (Claude — المالك يقدر يغيّرها)**، مكتوبة في §61.
 */
enum class AlertDelivery(val wire: String) { SEND_NOW("send_now"), AT_USUAL_TIME("at_usual_time"), DIGEST("digest"), INBOX_ONLY("inbox_only") }

/** اللحظة بتوقيت الجوال: اليوم والساعة (0–23). الساعة من الجهاز بتتدّي من برا — مفيش وقت بيتقرا هنا. */
data class LocalMoment(val date: IsoDate, val hour: Int) {
    init {
        require(hour in 0..23) { "hour out of range: $hour" }
    }
}

/** تفاعلك مع نوع تنبيه: اتعرض كام مرة (أي قناة) واتفتح كام مرة. «اتجاهل» = اتعرض وما اتفتحش. */
data class KindStats(val shown: Int = 0, val opened: Int = 0) {
    init {
        require(shown >= 0 && opened in 0..shown) { "bad stats: $shown/$opened" }
    }
}

enum class Engagement { UNKNOWN, IGNORED, NEUTRAL, ENGAGED }

/** أقل عدد مرات عرض قبل ما المحرك يحكم على تفاعلك مع النوع. */
const val ENGAGEMENT_MIN_SHOWN = 5

/** أقل من 20% اتفتح ⇒ بتتجاهله. */
const val IGNORED_BELOW_PERCENT = 20

/** 60% أو أكتر اتفتح ⇒ بتهتم بيه. */
const val ENGAGED_FROM_PERCENT = 60

fun engagementOf(s: KindStats): Engagement = when {
    s.shown < ENGAGEMENT_MIN_SHOWN -> Engagement.UNKNOWN
    s.opened * 100L < s.shown * IGNORED_BELOW_PERCENT.toLong() -> Engagement.IGNORED
    s.opened * 100L >= s.shown * ENGAGED_FROM_PERCENT.toLong() -> Engagement.ENGAGED
    else -> Engagement.NEUTRAL
}

/** أقل عدد فتحات للتطبيق قبل ما المحرك يعرف ساعاتك. */
const val USUAL_HOURS_MIN_OPENS = 5

/**
 * الساعات اللي بتفتح فيها التطبيق: عدد الفتحات في كل ساعة من الـ24. الساعة «معتادة» لو فيها فتحتين على الأقل
 * و**ربع** أكتر ساعة على الأقل — يعني نسبي لعادتك إنت، مش ساعات ثابتة.
 */
data class UsualHours(val opens: List<Int> = List(24) { 0 }) {
    init {
        require(opens.size == 24 && opens.all { it >= 0 }) { "opens must be 24 non-negative counts" }
    }

    val learned: Boolean get() = opens.sum() >= USUAL_HOURS_MIN_OPENS

    fun isUsual(hour: Int): Boolean {
        if (!learned) return false
        val busiest = opens.max()
        return opens[hour] >= 2 && opens[hour] * 4 >= busiest
    }

    /** أول ساعة معتادة **بعد** [now] (النهارده، وإلا بكرة). null لو لسه ما اتعلمتش. */
    fun nextUsual(now: LocalMoment): LocalMoment? {
        if (!learned) return null
        for (h in now.hour + 1..23) if (isUsual(h)) return LocalMoment(now.date, h)
        for (h in 0..now.hour) if (isUsual(h)) return LocalMoment(addDaysIso(now.date, 1), h)
        return null
    }

    fun recordOpen(hour: Int): UsualHours = UsualHours(opens.mapIndexed { h, n -> if (h == hour) n + 1 else n })
}

/** ليه المحرك اختار كده — كل واحد ليه جملة عربي في الصفحة ([alertReasonText]). */
enum class AlertFactor {
    URGENT, MONEY_COMING_IN, BIG_FOR_MONTH, SMALL_FOR_MONTH, NEEDS_DECISION, YOU_OPEN_THESE, YOU_SKIP_THESE,
    USUAL_HOUR_NOW, WAIT_FOR_USUAL_HOUR, HOURS_NOT_LEARNED, BUNDLED, PAGE_ONLY,
}

data class AlertDecision(
    val kind: AlertKind,
    val delivery: AlertDelivery,
    /** إمتى يوصل الشريط (للمستني والملخص) — null = دلوقتي أو مفيش شريط. */
    val deliverAt: LocalMoment?,
    /** المتأخر: نافذة جوه التطبيق كمان (§61). */
    val inAppWindow: Boolean,
    val factors: List<AlertFactor>,
)

/** 10% من سقف الشهر أو أكتر ⇒ «كبير بالنسبة لشهرك». */
const val BIG_FOR_MONTH_PERCENT = 10

/** أقل من 2% ⇒ «صغير» (ما بيصحّيش حد). */
const val SMALL_FOR_MONTH_PERCENT = 2

private const val INBOX = 0
private const val DIGEST = 1
private const val TIMELY = 2
private const val NOW = 3

fun decideAlert(c: AlertCandidate, stats: KindStats, hours: UsualHours, now: LocalMoment): AlertDecision {
    val kind = c.kind
    val factors = mutableListOf<AlertFactor>()
    if (kind.urgency == AlertUrgency.NONE) {
        return AlertDecision(kind, AlertDelivery.INBOX_ONLY, null, false, listOf(AlertFactor.PAGE_ONLY))
    }
    var level = when (kind.urgency) {
        AlertUrgency.HIGH -> NOW
        AlertUrgency.MEDIUM -> TIMELY
        else -> DIGEST
    }
    if (kind.group == AlertGroup.DUES && c.flow == DueFlow.RECEIVE) {
        level--
        factors += AlertFactor.MONEY_COMING_IN
    }

    val amount = c.amountMinor
    val scale = c.monthScaleMinor
    if (amount != null && scale != null && scale > 0) {
        val abs = if (amount < 0) -amount else amount
        if (abs * 100 >= scale * BIG_FOR_MONTH_PERCENT) {
            if (level < NOW) level++
            factors += AlertFactor.BIG_FOR_MONTH
        } else if (abs * 100 < scale * SMALL_FOR_MONTH_PERCENT && kind.urgency != AlertUrgency.HIGH) {
            level--
            factors += AlertFactor.SMALL_FOR_MONTH
        }
    }

    if (kind.needsDecision) {
        if (level < TIMELY) level = TIMELY
        factors += AlertFactor.NEEDS_DECISION
    }

    when (engagementOf(stats)) {
        Engagement.IGNORED -> {
            level--
            // الميعاد النهارده أو المتأخر ما بيختفيش في ملخص — أقصاه يستنى وقتك
            if (kind.urgency == AlertUrgency.HIGH && level < TIMELY) level = TIMELY
            factors += AlertFactor.YOU_SKIP_THESE
        }
        Engagement.ENGAGED -> {
            if (level == DIGEST) level = TIMELY
            factors += AlertFactor.YOU_OPEN_THESE
        }
        else -> Unit
    }

    level = level.coerceIn(INBOX, NOW)
    return when (level) {
        NOW -> AlertDecision(kind, AlertDelivery.SEND_NOW, null, kind.inAppWindow, factors + AlertFactor.URGENT)
        TIMELY -> when {
            hours.isUsual(now.hour) -> AlertDecision(kind, AlertDelivery.SEND_NOW, null, kind.inAppWindow, factors + AlertFactor.USUAL_HOUR_NOW)
            !hours.learned -> AlertDecision(kind, AlertDelivery.SEND_NOW, null, kind.inAppWindow, factors + AlertFactor.HOURS_NOT_LEARNED)
            else -> AlertDecision(kind, AlertDelivery.AT_USUAL_TIME, hours.nextUsual(now), kind.inAppWindow, factors + AlertFactor.WAIT_FOR_USUAL_HOUR)
        }
        DIGEST -> {
            val at = if (hours.isUsual(now.hour)) null else hours.nextUsual(now)
            AlertDecision(kind, AlertDelivery.DIGEST, at, kind.inAppWindow, factors + AlertFactor.BUNDLED)
        }
        else -> AlertDecision(kind, AlertDelivery.INBOX_ONLY, null, kind.inAppWindow, factors + AlertFactor.PAGE_ONLY)
    }
}
