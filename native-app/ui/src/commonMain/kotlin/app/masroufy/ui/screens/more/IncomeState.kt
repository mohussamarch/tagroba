package app.masroufy.ui.screens.more

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PayFrequency
import app.masroufy.core.SALARIED_KINDS
import app.masroufy.core.SourceStartComparison
import app.masroufy.core.TextKey
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t

/**
 * مصادر الدخل للعرض — **دوال نقية** (JVM). المبالغ والنِسب جاهزة من حالات الاستخدام (`compareAroundStart` بيدّي النسبة بالعُشر من المية)؛
 * هنا بس الصياغة. المتوقع الفاضي «لم تكتبه» مش صفر (§48)، والمقارنة `null` ⇒ «غير متاح» (القاعدة 10).
 */
fun incomeKindLabel(kind: IncomeSourceKind): TextRef = when (kind) {
    IncomeSourceKind.JOB -> UiKey.INCSRC_KIND_JOB
    IncomeSourceKind.PART_TIME -> UiKey.INCSRC_KIND_PART_TIME
    IncomeSourceKind.CLIENT -> UiKey.INCSRC_KIND_CLIENT
    IncomeSourceKind.RENT -> UiKey.INCSRC_KIND_RENT
    IncomeSourceKind.INVESTMENT -> UiKey.INCSRC_KIND_INVESTMENT
    IncomeSourceKind.PENSION -> UiKey.INCSRC_KIND_PENSION
    IncomeSourceKind.OTHER -> UiKey.INCSRC_KIND_OTHER
}

/** الاتنين = 1 … الأحد = 7 (ISO — زي `payWeekday`). */
fun weekdayLabel(day: Int): TextRef = listOf(
    UiKey.WEEKDAY_MON, UiKey.WEEKDAY_TUE, UiKey.WEEKDAY_WED, UiKey.WEEKDAY_THU, UiKey.WEEKDAY_FRI, UiKey.WEEKDAY_SAT, UiKey.WEEKDAY_SUN,
)[(day - 1).coerceIn(0, 6)]

/** «يوم 28 شهريًا» · «كل الخميس» · «الموعد غير محدد» (للي ليه مرتب) · «بلا موعد ثابت». */
fun payText(s: IncomeSource): String = when {
    s.payFrequency == PayFrequency.WEEKLY -> s.payWeekday?.let { t(UiKey.INCSRC_PAY_WEEKLY, t(weekdayLabel(it))) } ?: t(UiKey.INCSRC_PAY_WEEKLY_UNSET)
    s.expectedDayOfMonth != null -> t(UiKey.INCSRC_PAY_MONTHLY, sentenceNumber(s.expectedDayOfMonth!!))
    s.kind in SALARIED_KINDS || s.kind == IncomeSourceKind.PENSION -> t(UiKey.INCSRC_PAY_UNSET)
    else -> t(UiKey.INCSRC_PAY_NONE)
}

/** «منذ 28 أبريل 2026» أو «1 مارس 2025 – 27 أبريل 2026». */
fun periodText(s: IncomeSource): String {
    val from = fullDate(s.startedAt) ?: s.startedAt
    return s.endedAt?.let { t(UiKey.INCSRC_PERIOD_RANGE, from, fullDate(it) ?: it) } ?: t(UiKey.INCSRC_PERIOD_SINCE, from)
}

data class IncomeRowView(val id: String, val name: String, val kind: IncomeSourceKind, val meta: String, val period: String, val expected: String?, val ended: Boolean)

fun incomeRow(s: IncomeSource): IncomeRowView = IncomeRowView(
    s.id, s.name, s.kind, t(UiKey.INCSRC_META, t(incomeKindLabel(s.kind)), payText(s)), periodText(s),
    s.expectedMinor?.let { amountLabel(it, s.currency) }, s.endedAt != null,
)

data class IncomeSections(val current: List<IncomeRowView>, val past: List<IncomeRowView>)

/** الشغالة ثم اللي فاتت (ترتيب `ManageIncomeSources.list`). */
fun incomeSections(sources: List<IncomeSource>): IncomeSections =
    IncomeSections(sources.filter { it.endedAt == null }.map(::incomeRow), sources.filter { it.endedAt != null }.map(::incomeRow))

/** نسبة بالعُشر من المية ⇒ «+26.3%» (صياغة بس — القسمة على 10 عدد صحيح للعرض). null ⇒ «—». */
fun tenthPercentText(tenth: Long?): String {
    if (tenth == null) return "—"
    val abs = if (tenth < 0) -tenth else tenth
    val sign = when {
        tenth > 0 -> "+"
        tenth < 0 -> "−"
        else -> ""
    }
    return sign + sentenceDigits("${abs / 10}") + t(UiKey.INCSRC_DECIMAL_SEP) + sentenceDigits("${abs % 10}") + "%"
}

enum class Trend { GOOD, BAD, FLAT }

/** الدخل لو زاد كويس، والمصروف لو قلّ كويس. */
fun trend(tenth: Long?, isIncome: Boolean): Trend = when {
    tenth == null || tenth == 0L -> Trend.FLAT
    (tenth > 0) == isIncome -> Trend.GOOD
    else -> Trend.BAD
}

data class CompareRow(val label: TextRef, val before: Halalas, val after: Halalas, val change: String, val trend: Trend, val currency: Currency)

fun compareRows(c: SourceStartComparison): List<CompareRow> = listOf(
    CompareRow(UiKey.INCSRC_CMP_INCOME, c.incomeBeforeAvgMinor, c.incomeAfterAvgMinor, tenthPercentText(c.incomeChangeTenthPercent), trend(c.incomeChangeTenthPercent, true), c.currency),
    CompareRow(UiKey.INCSRC_CMP_EXPENSE, c.expenseBeforeAvgMinor, c.expenseAfterAvgMinor, tenthPercentText(c.expenseChangeTenthPercent), trend(c.expenseChangeTenthPercent, false), c.currency),
)

/** «غيّرت شغلي»: الأسئلة اللي فاضلة بالترتيب. الرد بيطلّع أسئلة جديدة (يوم الراتب ⇒ «تغيّر بداية شهرك؟») بتتحط قبل الباقي. */
fun nextQueue(queue: List<IncomeFollowUp>, produced: List<IncomeFollowUp>): List<IncomeFollowUp> = produced + queue.drop(1)

// ── تواريخ الشرائح (عرض واختيار بس — من غير أي حساب فلوس) ──

fun plusDays(iso: IsoDate, days: Int): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(iso)) + days)

fun firstOfMonth(iso: IsoDate, monthsAhead: Int = 0): IsoDate {
    val p = parseIsoDate(iso)
    val index = p.year * 12 + (p.month - 1) + monthsAhead
    val y = index / 12
    val m = index % 12 + 1
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-01"
}

/** «خلص إمتى؟»: اليوم · آخر يوم في الشهر اللي فات · اليوم اللي قبل بداية الشهر المالي الحالي (من غير تكرار). */
fun endDateChoices(today: IsoDate, payday: Int): List<IsoDate> =
    listOf(today, plusDays(firstOfMonth(today), -1), plusDays(periodForDate(today, payday).start, -1)).distinct()

/** «بدأ إمتى؟» بعد «غيّرت شغلي»: بكرة · أول الشهر الجاي · اليوم. ولإضافة مصدر قديم: اليوم · بداية الشهر المالي · أول السنة. */
fun startDateChoices(today: IsoDate, payday: Int, change: Boolean): List<IsoDate> =
    if (change) listOf(plusDays(today, 1), firstOfMonth(today, 1), today).distinct()
    else listOf(today, periodForDate(today, payday).start, today.take(4) + "-01-01").distinct()

/** كلمة اليوم في الشريحة: «اليوم» بدل تاريخ النهارده. */
fun dateChipLabel(iso: IsoDate, today: IsoDate): String = if (iso == today) t(UiKey.MORE_TODAY) else fullDate(iso) ?: iso
