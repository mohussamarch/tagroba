package app.masroufy.ui.screens.budgets

import app.masroufy.core.BudgetLevel
import app.masroufy.core.BudgetStatus
import app.masroufy.core.CalendarItem
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.DueFlow
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.LeftoverMode
import app.masroufy.core.LeftoverProjection
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.absMoney
import app.masroufy.core.dayMonth
import app.masroufy.core.factsFromProfile
import app.masroufy.core.monthName
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.presentCategories
import app.masroufy.core.sentenceNumber
import app.masroufy.core.shiftPeriodWithin
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.shell.daysLeftText
import app.masroufy.ui.text.t
import app.masroufy.usecase.BudgetScreenData
import app.masroufy.usecase.LoadBudgetScreenRequest

/**
 * مقدِّم «الميزانيات» (`Budgets` — خانة جوه مبدّل «العمليات»): بيحمّل من حالات الاستخدام بس (`LoadBudgetScreen` · `LoadCalendar` ·
 * `LoadLeftover`) وبيحوّل النتيجة لنصوص وأرقام جاهزة للعرض. **مفيش حساب فلوس هنا** (CLAUDE.md #4): المصروف والسقف والباقي والمتاح يوميًا
 * والمتوسط والمتبقي كلهم من حالة الاستخدام؛ النسبة المعروضة = `usedTenthPercent ÷ 10` من `BudgetStatus`.
 */
data class LimitCurrent(val limitMinor: Halalas?, val thresholdPercent: Int?, val notify: Boolean)

/** بطاقة السقف الإجمالي بحالاتها الثلاثة (النموذج: عادي · فاضي «لم تحدّد سقفًا» · غير متاح). */
sealed interface TotalCard {
    data class NoLimit(val title: String, val body: String) : TotalCard

    data class Unknown(val title: String, val limitMinor: Halalas?, val reason: String) : TotalCard

    data class Known(
        val title: String,
        val spentMinor: Halalas,
        val limitMinor: Halalas,
        val percent: Int,
        val tone: Tone,
        val chip: String,
        val leftLine: String,
        val dailyLine: String?,
        /** المصروف مبني على أنواع مش مؤكدة (§18) ⇒ «تقريبي» + السبب. */
        val approxNote: String?,
    ) : TotalCard
}

data class LineUi(
    val categoryId: String,
    val name: String,
    val colorHex: String?,
    /** null = المصروف مش معروف (القاعدة 10). */
    val spentMinor: Halalas?,
    val limitMinor: Halalas?,
    val percent: Int?,
    val over: Boolean,
    val note: String,
    val noLimit: Boolean,
)

data class UpcomingUi(val item: CalendarItem, val whenText: String, val reserved: Boolean)

data class LeftoverUi(val text: String, val negative: Boolean, val approximate: Boolean)

data class BudgetsUi(
    val period: Period,
    val monthName: String,
    val prevMonthName: String,
    val prevPeriodKey: String,
    val currency: Currency,
    val total: TotalCard,
    val totalLimit: LimitCurrent,
    /** «المصروف حتى الآن» في لوحة السقف — null = مش معروف. */
    val spentMinor: Halalas?,
    val averageMinor: Halalas?,
    val averageReason: String,
    val anomalyText: String,
    val anomalyAlert: Boolean,
    val lines: List<LineUi>,
    val upcoming: List<UpcomingUi>,
    val leftover: LeftoverUi?,
)

/** اسم الشهر المالي = الشهر اللي **بيخلص** فيه (٢٨ سبتمبر–٢٧ أكتوبر = «أكتوبر» — OVERRIDES §76 ٣ بترقيم فرع التصميم). */
fun periodMonthName(period: Period): String = monthName(parseIsoDate(period.end).month)

/** النسبة المعروضة من `BudgetStatus` (عُشر في المية ⇒ نسبة صحيحة لتحت). */
fun usedPercent(status: BudgetStatus): Int = (status.usedTenthPercent / 10).toInt()

/** نبرة السقف: عدّى ⇒ أحمر · قرّب أو عدّى نسبة التنبيه ⇒ كهرماني · غير كده أخضر. */
fun toneOf(status: BudgetStatus): Tone = when {
    status.level == BudgetLevel.OVER -> Tone.OVER
    status.level == BudgetLevel.NEAR || status.thresholdCrossed -> Tone.NEAR
    else -> Tone.OK
}

/** المبلغ داخل جملة («1,350.00 ر.س») — موجب دايمًا والسياق بيقول «بقي» ولا «تجاوز». */
internal fun plain(minor: Halalas, currency: Currency): String = amountLabel(absMoney(minor), currency)

suspend fun loadBudgets(deps: BudgetsDeps, today: IsoDate, currency: Currency): BudgetsUi {
    val profile = deps.profile.load()
    val period = periodForDate(today, profile.payday)
    val data = deps.budgetScreen.load(LoadBudgetScreenRequest(period, today, profile.payday, profile.duesInBudget ?: true))
    val items = deps.calendar.items(today, period.end, today)
    val leftover = deps.leftover.load(today)
    val shown = presentCategories(data.categories, factsFromProfile(profile))
    return mapBudgets(data, shown, items, leftover, currency, profile.payday)
}

fun mapBudgets(
    data: BudgetScreenData,
    categories: List<Category>,
    items: List<CalendarItem>,
    leftover: LeftoverProjection?,
    currency: Currency,
    payday: Int,
): BudgetsUi {
    val period = data.period
    val month = periodMonthName(period)
    val prev = shiftPeriodWithin(period, -1, period, payday)
    val limit = data.budget?.totalLimitMinor
    val status = data.totalStatus
    val total: TotalCard = when {
        limit == null -> TotalCard.NoLimit(t(TextKey.BUDGETS_NO_TOTAL_TITLE, month), t(TextKey.BUDGETS_NO_TOTAL_BODY))
        !data.spentKnown || status == null -> TotalCard.Unknown(t(TextKey.BUDGETS_TOTAL_TITLE, month), limit, data.spentNote ?: t(TextKey.BUDGETS_NA_REASON))
        else -> {
            val tone = toneOf(status)
            TotalCard.Known(
                title = t(TextKey.BUDGETS_TOTAL_TITLE, month),
                spentMinor = status.spentMinor,
                limitMinor = status.limitMinor,
                percent = usedPercent(status).coerceAtMost(100),
                tone = tone,
                chip = t(chipKey(tone)),
                leftLine = if (status.remainingMinor >= 0) t(TextKey.BUDGETS_LEFT, plain(status.remainingMinor, currency))
                else t(TextKey.BUDGETS_OVER_BY, plain(status.remainingMinor, currency)),
                dailyLine = data.allowance.amountMinor?.takeIf { it > 0 }?.let { t(TextKey.BUDGETS_DAILY, plain(it, currency)) },
                approxNote = if (data.spentReliable) null else data.spentNote,
            )
        }
    }
    val names = categories.associateBy { it.id }
    val lines = data.lines.map { line -> lineUi(line, names[line.categoryId], data.spentKnown, currency) }
    val anomaly = data.anomaly
    return BudgetsUi(
        period = period,
        monthName = month,
        prevMonthName = periodMonthName(prev),
        prevPeriodKey = prev.key,
        currency = currency,
        total = total,
        totalLimit = LimitCurrent(limit, data.budget?.thresholdPercent, data.budget?.thresholdPercent != null),
        spentMinor = data.spentMinor.takeIf { data.spentKnown },
        averageMinor = data.average.averageMinor,
        averageReason = data.average.reason,
        // الحكم بالمقارنة بالفترات اللي فاتت: null = ما نقدرش نحكم (مش «مفيش») — السبب بيتعرض زي ما هو
        anomalyText = if (data.spentKnown) anomaly.reason else t(TextKey.NOT_AVAILABLE),
        anomalyAlert = data.spentKnown && anomaly.isAnomaly == true,
        lines = lines,
        upcoming = items.filter { it.flow == DueFlow.PAY && it.daysLeft >= 0 }.take(MAX_UPCOMING).map { upcomingUi(it) },
        leftover = leftover?.let { leftoverUi(it, currency) },
    )
}

private const val MAX_UPCOMING = 6

internal fun chipKey(tone: Tone): TextKey = when (tone) {
    Tone.OVER -> TextKey.BUDGETS_CHIP_OVER
    Tone.NEAR -> TextKey.BUDGETS_CHIP_NEAR
    else -> TextKey.BUDGETS_CHIP_OK
}

private fun lineUi(line: app.masroufy.core.CategoryBudgetLine, category: Category?, known: Boolean, currency: Currency): LineUi {
    val status = line.status
    val over = known && status != null && status.level == BudgetLevel.OVER
    val note = when {
        status == null -> t(TextKey.BUDGETS_CAT_NO_LIMIT)
        !known -> t(TextKey.NOT_AVAILABLE)
        over -> t(TextKey.BUDGETS_CAT_OVER, plain(status.remainingMinor, currency))
        else -> t(TextKey.BUDGETS_CAT_LEFT, plain(status.remainingMinor, currency), sentenceNumber(usedPercent(status)))
    }
    return LineUi(
        categoryId = line.categoryId,
        name = category?.name ?: t(TextKey.CATEGORY_DELETED),
        colorHex = category?.lightColor,
        spentMinor = line.spentMinor.takeIf { known },
        limitMinor = status?.limitMinor,
        percent = status?.let { usedPercent(it).coerceAtMost(100) },
        over = over,
        note = note,
        noLimit = status == null,
    )
}

private fun upcomingUi(item: CalendarItem): UpcomingUi =
    UpcomingUi(item, t(TextKey.BUDGETS_UP_WHEN, dayMonth(item.date), daysLeftText(item.daysLeft)), reserved = item.reservedMinor != null)

private fun leftoverUi(p: LeftoverProjection, currency: Currency): LeftoverUi {
    val label = t(
        when (p.mode) {
            LeftoverMode.UNTIL_MONTH_END -> TextKey.LEFTOVER_MONTH_END
            LeftoverMode.UNTIL_NEXT_PAY -> TextKey.LEFTOVER_NEXT_PAY
            LeftoverMode.FROM_WHAT_YOU_HAVE -> TextKey.LEFTOVER_FROM_WHAT_YOU_HAVE
        },
    )
    val value = p.leftoverMinor?.let { amountLabel(it, currency) } ?: t(TextKey.NOT_AVAILABLE)
    return LeftoverUi(t(TextKey.BUDGETS_LEFTOVER, label, value), negative = (p.leftoverMinor ?: 0L) < 0L, approximate = p.approximate)
}
