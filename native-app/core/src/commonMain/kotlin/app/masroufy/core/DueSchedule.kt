package app.masroufy.core

/**
 * جدول الأقساط — الحساب المشترك بين كل اللي في «المستحقات» (OVERRIDES §43، §50):
 * قسط الجمعية، قسط التمويل أو التقسيط، ودين ليه ميعاد.
 *
 * **المدفوع بيتوزع على الأقساط بالترتيب** (الأقدم الأول) — مش كل دفعة مربوطة بقسط بعينه.
 * كده الدفعة الناقصة أو الدفعتين في شهر واحد بيتحسبوا صح من غير ما المستخدم يختار «ده قسط شهر كام».
 */
data class DueSchedule(
    /** ميعاد القسط الأول. */
    val firstDueAt: IsoDate,
    /** كل كام [unit] (1 = كل شهر أو كل أسبوع). */
    val every: Int,
    /** القسط العادي. */
    val installmentMinor: Halalas,
    /** الإجمالي. آخر قسط = الباقي، فممكن يكون أصغر من القسط العادي. */
    val totalMinor: Halalas,
    /** للرسايل بس — الحساب نفسه ما بيخلطش عملات. */
    val currency: Currency = Currency.SAR,
    /** الجمعية بس ممكن تبقى بالأسبوع (قرار المالك: سؤال «كل قد إيه» — OVERRIDES §50). */
    val unit: CycleUnit = CycleUnit.MONTH,
)

enum class CycleUnit(val wire: String) {
    WEEK("week"), MONTH("month");

    companion object {
        fun fromWire(wire: String): CycleUnit = entries.first { it.wire == wire }
    }
}

const val DUE_MAX_CYCLE_MONTHS = 12
const val DUE_MAX_CYCLE_WEEKS = 4

/** أقصى عدد أقساط — تمويل 30 سنة شهري. أكتر من كده غالبًا رقم مكتوب غلط. */
const val DUE_MAX_INSTALLMENTS = 360

class DueScheduleError(message: String) : IllegalArgumentException(message)

fun checkDueSchedule(s: DueSchedule) {
    if (!isValidIsoDate(s.firstDueAt)) throw DueScheduleError(uiText(TextKey.DUE_BAD_FIRST_DATE))
    when (s.unit) {
        CycleUnit.MONTH -> if (s.every !in 1..DUE_MAX_CYCLE_MONTHS) throw DueScheduleError(uiText(TextKey.DUE_BAD_CYCLE, DUE_MAX_CYCLE_MONTHS.toString()))
        CycleUnit.WEEK -> if (s.every !in 1..DUE_MAX_CYCLE_WEEKS) throw DueScheduleError(uiText(TextKey.DUE_BAD_CYCLE_WEEKS, DUE_MAX_CYCLE_WEEKS.toString()))
    }
    assertHalalas(s.installmentMinor)
    assertHalalas(s.totalMinor)
    if (s.installmentMinor <= 0) throw DueScheduleError(uiText(TextKey.DUE_INSTALLMENT_POSITIVE))
    if (s.totalMinor <= 0) throw DueScheduleError(uiText(TextKey.DUE_TOTAL_POSITIVE))
    if (installmentCount(s) > DUE_MAX_INSTALLMENTS) throw DueScheduleError(uiText(TextKey.DUE_TOO_MANY, DUE_MAX_INSTALLMENTS.toString()))
}

/** عدد الأقساط = الإجمالي ÷ القسط لفوق. */
fun installmentCount(s: DueSchedule): Int = ((s.totalMinor + s.installmentMinor - 1) / s.installmentMinor).toInt()

/** تاريخ بعد [cycles] دورة. بالشهر: يوم 31 في شهر أقصر بيبقى آخر الشهر. بالأسبوع: 7 أيام بالظبط. */
fun shiftCycles(date: IsoDate, unit: CycleUnit, cycles: Int): IsoDate = when (unit) {
    CycleUnit.MONTH -> shiftMonths(date, cycles)
    CycleUnit.WEEK -> dayNumberToIso(toDayNumber(parseIsoDate(date)) + 7 * cycles)
}

/** ميعاد القسط رقم [n] (من 1) — محسوب من **أول** ميعاد مش من اللي قبله، فيوم 31 ما بيزحفش. */
fun dueDateOf(s: DueSchedule, n: Int): IsoDate = shiftCycles(s.firstDueAt, s.unit, (n - 1) * s.every)

/** اللي لازم يكون اتدفع **لحد آخر** القسط رقم [n] — تراكمي. */
private fun cumulativeThrough(s: DueSchedule, n: Int): Halalas = minOf(multiplyMoneyByInt(s.installmentMinor, n.toLong()), s.totalMinor)

/** قيمة القسط رقم [n] — كله القسط العادي ما عدا الأخير ممكن يبقى أصغر. */
fun installmentAmountOf(s: DueSchedule, n: Int): Halalas = cumulativeThrough(s, n) - cumulativeThrough(s, n - 1)

data class DueProgress(
    val paidMinor: Halalas,
    val remainingMinor: Halalas,
    /** أقساط اتدفعت بالكامل. */
    val paidCount: Int,
    val count: Int,
    /** القسط الجاي (null = خلص). */
    val nextNumber: Int?,
    val nextDueAt: IsoDate?,
    /** الفاضل من القسط الجاي — أقل من القسط لو فيه دفعة ناقصة قبل كده. */
    val nextAmountMinor: Halalas,
    /** أقساط ميعادها فات ولسه ما اتدفعتش كاملة. */
    val overdueCount: Int,
    val overdueMinor: Halalas,
) {
    val done: Boolean get() = remainingMinor == 0L
}

/**
 * التقدم في الجدول. المدفوع أكبر من الإجمالي **خطأ مش صفر** — نفس قاعدة التسويات (spec/06):
 * الزيادة ما بتتبلعش في الصمت.
 */
fun dueProgress(s: DueSchedule, paidMinor: Halalas, today: IsoDate): DueProgress {
    checkDueSchedule(s)
    assertHalalas(paidMinor)
    if (paidMinor < 0) throw DueScheduleError(uiText(TextKey.DUE_PAID_NEGATIVE))
    if (paidMinor > s.totalMinor) {
        throw DueScheduleError(uiText(TextKey.DUE_OVERPAID, formatMoney(paidMinor, s.currency), formatMoney(s.totalMinor, s.currency)))
    }
    val count = installmentCount(s)
    val remaining = subtractMoney(s.totalMinor, paidMinor)
    val paidCount = if (remaining == 0L) count else (paidMinor / s.installmentMinor).toInt()
    if (remaining == 0L) return DueProgress(paidMinor, 0, count, count, null, null, 0, 0, 0)
    val next = paidCount + 1
    var lastOverdue = paidCount
    while (lastOverdue < count && dueDateOf(s, lastOverdue + 1) < today) lastOverdue++
    return DueProgress(
        paidMinor = paidMinor,
        remainingMinor = remaining,
        paidCount = paidCount,
        count = count,
        nextNumber = next,
        nextDueAt = dueDateOf(s, next),
        nextAmountMinor = subtractMoney(cumulativeThrough(s, next), paidMinor),
        overdueCount = lastOverdue - paidCount,
        overdueMinor = if (lastOverdue > paidCount) subtractMoney(cumulativeThrough(s, lastOverdue), paidMinor) else 0,
    )
}

/**
 * الأقساط اللي لسه ما اتدفعتش لحد [until] — بالفاضل من كل واحد. لو مفيش ولا واحد لحد التاريخ ده
 * بيرجع **القسط الجاي بس**، عشان أي ارتباط مفتوح يفضل ظاهر بميعاده.
 */
fun unpaidInstallments(s: DueSchedule, paidMinor: Halalas, until: IsoDate): List<Pair<IsoDate, Halalas>> {
    val progress = dueProgress(s, paidMinor, until)
    val first = progress.nextNumber ?: return emptyList()
    val out = mutableListOf<Pair<IsoDate, Halalas>>()
    var n = first
    while (n <= progress.count) {
        val date = dueDateOf(s, n)
        if (date > until && out.isNotEmpty()) break
        val amount = if (n == first) progress.nextAmountMinor else installmentAmountOf(s, n)
        out += date to amount
        if (date > until) break
        n++
    }
    return out
}
