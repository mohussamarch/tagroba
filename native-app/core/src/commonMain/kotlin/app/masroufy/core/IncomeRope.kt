package app.masroufy.core

/**
 * «حبل مصادر دخلك» (مواصفة النقل §1.11 · §2.7 — `InvestIncomeRope.dc.html`): آخر 12 شهر = (النهارده − 12 شهر، النهارده].
 * - كل صفوف «وظيفة» ⇒ **خيط واحد** للمرتب حتى لو جهة الشغل اتغيّرت (البداية = أول بداية، النهاية = النهارده لو فيه شغل شغال وإلا آخر
 *   نهاية) + علامة «غيّرت الشغل» عند كل بداية وظيفة جديدة جوه الفترة.
 * - أي مصدر تاني خيط لوحده مقصوص على الفترة. «شغال» = من غير تاريخ نهاية (المرتب بيتعد مرة واحدة).
 * - البيع الشخصي (مرة واحدة) عقدة مش خيط. مفيش خيوط ⇒ «مفيش بيانات» (null).
 * أماكن الرسم (x) هندسة في الواجهة من التواريخ.
 */
data class RopeJob(val name: String, val startedAt: IsoDate, val endedAt: IsoDate?)

data class RopeStrand(
    val kind: IncomeSourceKind,
    /** مقصوصة على الفترة. */
    val from: IsoDate,
    val to: IsoDate,
    val active: Boolean,
    /** اسم المصدر (للمرتب: آخر جهة). */
    val name: String,
    val startedAt: IsoDate,
    val endedAt: IsoDate?,
    /** المرتب بس: الجهات بالترتيب (2 أو أكتر = اتغيّرت). */
    val jobs: List<RopeJob> = emptyList(),
)

/** بيع شخصي في الفترة. [label] null ⇒ الكلمة الافتراضية «بيع». */
data class RopeKnot(val date: IsoDate, val label: String?)

data class IncomeRope(
    /** أول الفترة (مش داخل فيها). */
    val windowStart: IsoDate,
    val windowEnd: IsoDate,
    val strands: List<RopeStrand>,
    /** بدايات وظايف جديدة جوه الفترة. */
    val ticks: List<IsoDate>,
    val knots: List<RopeKnot>,
) {
    val activeCount: Int get() = strands.count { it.active }
}

const val ROPE_MONTHS = 12

fun ropeWindowStart(today: IsoDate): IsoDate = shiftMonths(today, -ROPE_MONTHS)

private fun inWindow(date: IsoDate, start: IsoDate, end: IsoDate) = date > start && date <= end

private fun overlaps(s: IncomeSource, start: IsoDate, end: IsoDate) = s.startedAt <= end && (s.endedAt == null || s.endedAt > start)

/** [sources] بعملة البلد بس (الفلترة في حالة الاستخدام) · [sales] كل البيع الشخصي (بيتقص هنا على الفترة). */
fun buildIncomeRope(sources: List<IncomeSource>, sales: List<RopeKnot>, today: IsoDate): IncomeRope? {
    val start = ropeWindowStart(today)
    val inRange = sources.filter { overlaps(it, start, today) }
    val jobs = inRange.filter { it.kind == IncomeSourceKind.JOB }.sortedBy { it.startedAt }
    val strands = mutableListOf<RopeStrand>()
    val ticks = mutableListOf<IsoDate>()
    if (jobs.isNotEmpty()) {
        val active = jobs.any { it.endedAt == null }
        val lastEnd = jobs.mapNotNull { it.endedAt }.maxOrNull()
        val to = if (active || lastEnd == null) today else minOf(lastEnd, today)
        val latest = jobs.lastOrNull { it.endedAt == null } ?: jobs.last()
        strands += RopeStrand(
            IncomeSourceKind.JOB, maxOf(jobs.first().startedAt, start), to, active, latest.name,
            jobs.first().startedAt, if (active) null else lastEnd, jobs.map { RopeJob(it.name, it.startedAt, it.endedAt) },
        )
        jobs.drop(1).map { it.startedAt }.filter { inWindow(it, start, today) }.forEach { ticks += it }
    }
    inRange.filter { it.kind != IncomeSourceKind.JOB }.sortedBy { it.startedAt }.forEach { s ->
        val to = s.endedAt?.let { minOf(it, today) } ?: today
        strands += RopeStrand(s.kind, maxOf(s.startedAt, start), to, s.endedAt == null, s.name, s.startedAt, s.endedAt)
    }
    if (strands.isEmpty()) return null
    val knots = sales.filter { inWindow(it.date, start, today) }.sortedBy { it.date }
    return IncomeRope(start, today, strands, ticks.distinct(), knots)
}
