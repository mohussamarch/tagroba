package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MIN_PERIODS_FOR_AVERAGE
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.factsFromProfile
import app.masroufy.core.periodForDate
import app.masroufy.core.presentCategories
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import app.masroufy.usecase.BudgetScreenData
import app.masroufy.usecase.LoadBudgetScreenRequest
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.TransactionsScreenData

/**
 * مقدِّم «ميزانية تصنيف» (`CategoryBudget`): من `LoadBudgetScreen` (سطر التصنيف وسقفه ومتوسطه وحكم الصرف غير المعتاد) و`LoadTransactionsScreen`
 * (عمليات الفترة — بتتفلتر بالتصنيف وفرعياته للعرض بس). **مفيش حساب فلوس**: المصروف والسقف والباقي والنسبة من `BudgetStatus`.
 * «يوم 10 من 30» = عدّ أيام (مش فلوس). ⚠️ مالهاش حالة استخدام (missingLogic): توقّع نهاية الشهر للتصنيف · يوم تجاوز السقف ·
 * المتاح يوميًا للتصنيف · نصيب كل فرعي من أبوه · ضمّ مصروف الفرعيات على أبوها في سطر الميزانية.
 */
data class SubUi(val id: String, val name: String, val colorHex: String?, val spentMinor: Halalas?)

data class TxUi(val id: String, val title: String, val subtitle: String, val amountMinor: Halalas, val currency: Currency)

data class CategoryBudgetUi(
    val categoryId: String,
    val name: String,
    val colorHex: String?,
    val iconKey: String?,
    val period: Period,
    val periodLine: String,
    val monthName: String,
    val currency: Currency,
    /** المصروف معروف؟ لأ ⇒ كارت «غير معروف حاليًا» + «راجعها». */
    val known: Boolean,
    val spentMinor: Halalas?,
    val limitMinor: Halalas?,
    val chip: String,
    val tone: Tone,
    val percent: Int?,
    val usedPercent: Int?,
    val dayIndex: Int,
    val totalDays: Int,
    val elapsedPercent: Int,
    val leftLine: String?,
    val pace1: String,
    val pace2: String?,
    val averageMinor: Halalas?,
    val averageNote: String,
    val anomalyText: String,
    val anomalyAlert: Boolean,
    val limit: LimitCurrent,
    val subs: List<SubUi>,
    val txCount: Int,
    val txRows: List<TxUi>,
    val approxNote: String?,
)

private const val SHOWN_TRANSACTIONS = 4

/** null = التصنيف مش موجود (اتشال). */
suspend fun loadCategoryBudget(deps: BudgetsDeps, categoryId: String, today: IsoDate, currency: Currency): CategoryBudgetUi? {
    val profile = deps.profile.load()
    val period = periodForDate(today, profile.payday)
    val data = deps.budgetScreen.load(LoadBudgetScreenRequest(period, today, profile.payday, profile.duesInBudget ?: true))
    val txns = deps.transactions.load(LoadTransactionsScreenRequest(period = period))
    val shown = presentCategories(data.categories, factsFromProfile(profile))
    return mapCategoryBudget(data, txns, shown, categoryId, today, currency)
}

fun mapCategoryBudget(
    data: BudgetScreenData,
    txns: TransactionsScreenData,
    categories: List<Category>,
    categoryId: String,
    today: IsoDate,
    currency: Currency,
): CategoryBudgetUi? {
    val category = categories.firstOrNull { it.id == categoryId } ?: return null
    val period = data.period
    val month = periodMonthName(period)
    val known = data.spentKnown
    val line = data.lines.firstOrNull { it.categoryId == categoryId }
    val status = line?.status
    val cb = data.categoryBudgets.firstOrNull { it.categoryId == categoryId }
    // التصنيف من غير صرف ولا سقف مالوش سطر ⇒ مصروفه صفر **معروف** (لو أنواع الشهر معروفة)
    val spent = if (known) line?.spentMinor ?: 0L else null
    val tone = when {
        !known -> Tone.MUTED
        status == null -> Tone.MUTED
        else -> toneOf(status)
    }
    val chip = when {
        !known -> t(TextKey.NOT_AVAILABLE)
        status == null -> t(UiKey.CAT_BUDGET_CHIP_NO_LIMIT)
        status.level == app.masroufy.core.BudgetLevel.OVER -> t(UiKey.BUDGETS_CHIP_OVER)
        status.thresholdCrossed -> t(UiKey.CAT_BUDGET_CHIP_THRESHOLD)
        else -> t(UiKey.BUDGETS_CHIP_OK)
    }
    val day = (daysBetween(period.start, today) + 1).coerceIn(1, period.days)
    val used = status?.let { usedPercent(it) }
    val pace1 = when {
        !known -> t(TextKey.NOT_AVAILABLE)
        spent == 0L -> t(UiKey.CAT_BUDGET_PACE_NONE)
        status == null -> t(UiKey.CAT_BUDGET_PACE_NO_LIMIT)
        status.level == app.masroufy.core.BudgetLevel.OVER -> t(UiKey.BUDGETS_CAT_OVER, plain(status.remainingMinor, currency))
        else -> t(UiKey.CAT_BUDGET_PACE_USED, sentenceNumber(used ?: 0), sentenceNumber(day * 100 / period.days))
    }
    val ids = setOf(categoryId) + categories.filter { it.parentId == categoryId }.map { it.id }
    val names = categories.associate { it.id to it.name }
    val rows = txns.transactions
        .filter { it.observedDirection == Direction.OUT && it.categoryId in ids }
        .sortedWith(compareByDescending<app.masroufy.core.Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder })
    return CategoryBudgetUi(
        categoryId = categoryId,
        name = category.name,
        colorHex = category.lightColor,
        iconKey = category.iconKey,
        period = period,
        periodLine = t(UiKey.CAT_BUDGET_PERIOD, month, dayMonth(period.start), dayMonth(period.end)),
        monthName = month,
        currency = currency,
        known = known,
        spentMinor = spent,
        limitMinor = status?.limitMinor ?: cb?.limitMinor,
        chip = chip,
        tone = tone,
        percent = used?.coerceAtMost(100),
        usedPercent = used,
        dayIndex = day,
        totalDays = period.days,
        elapsedPercent = day * 100 / period.days,
        leftLine = status?.takeIf { known }?.let {
            if (it.remainingMinor >= 0) t(UiKey.BUDGETS_LEFT, plain(it.remainingMinor, currency)) else t(UiKey.BUDGETS_OVER_BY, plain(it.remainingMinor, currency))
        },
        pace1 = pace1,
        pace2 = if (known && status == null && spent != 0L) t(UiKey.CAT_BUDGET_PACE_HINT) else null,
        averageMinor = line?.averageMinor,
        averageNote = if (line?.averageMinor != null) t(UiKey.BUDGETS_AVG_NOTE) else t(UiKey.CAT_BUDGET_AVG_NA_NOTE, sentenceNumber(MIN_PERIODS_FOR_AVERAGE)),
        anomalyText = when {
            !known -> t(TextKey.NOT_AVAILABLE)
            spent == 0L -> t(UiKey.CAT_BUDGET_ANOM_NONE)
            line == null -> t(TextKey.NOT_AVAILABLE)
            else -> line.anomaly.reason
        },
        anomalyAlert = known && line?.anomaly?.isAnomaly == true,
        limit = LimitCurrent(cb?.limitMinor, cb?.thresholdPercent, cb?.notifyEnabled == true),
        subs = categories.filter { it.parentId == categoryId && it.active }.map { sub ->
            SubUi(sub.id, sub.name, sub.lightColor, if (known) data.lines.firstOrNull { it.categoryId == sub.id }?.spentMinor ?: 0L else null)
        },
        txCount = rows.size,
        txRows = rows.take(SHOWN_TRANSACTIONS).map { tx ->
            val title = txns.merchantNamesByTransaction[tx.id]?.firstOrNull() ?: tx.rawMerchantName ?: tx.rawDescription ?: category.name
            val sub = tx.categoryId?.takeIf { it != categoryId }?.let { names[it] }
            val date = dayMonth(tx.occurredAt.take(10))
            TxUi(tx.id, title, if (sub != null) t(UiKey.BUDGETS_UP_WHEN, sub, date) else date, tx.amountMinor, tx.currency)
        },
        approxNote = if (known && !data.spentReliable) data.spentNote else null,
    )
}
