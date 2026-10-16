package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.InvestRatesState
import app.masroufy.core.IsoDate
import app.masroufy.core.ReinvestMath
import app.masroufy.core.holdingsMixBp
import app.masroufy.core.investReturns
import app.masroufy.core.investableSplit
import app.masroufy.core.monthName
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository

/** سطر في «اللي رجعلك»: اسم التصنيف · «10 أشهر» أو «مرة: مارس» أو «مرتين: مارس وسبتمبر» · المبلغ. */
data class ReturnsRowView(val label: String, val sub: String, val amountMinor: Halalas)

/** [math] null = المتوسطات مش متاحة ⇒ صندوق «لو رجّعته» والملاحظة «غير متاح». [cpiSource] اسم مصدر الغلاء (للملاحظة). */
data class ReturnsView(val currency: Currency, val rows: List<ReturnsRowView>, val totalMinor: Halalas, val math: ReinvestMath?)

data class LoadInvestReturnsDeps(val txns: TransactionRepository, val categories: CategoryRepository)

/**
 * «اللي رجعلك» (مواصفة النقل §1.12 · §2.7): العائد من أول يناير ⇒ [investReturns] بمتوسط أملاكك (`holdingsMixBp`، وإلا متوسط الأسهم)
 * والغلاء الرسمي. null = ما وصلكش عائد السنة دي.
 */
class LoadInvestReturns(private val deps: LoadInvestReturnsDeps) {
    suspend fun load(today: IsoDate, currency: Currency, holdings: HoldingsSnapshot?, rates: InvestRatesState?): ReturnsView? {
        val txns = deps.txns.listByDateRange(today.take(4) + "-01-01", today)
        val ready = (rates as? InvestRatesState.Ready)?.rates
        val mix = ready?.let { r -> holdings?.let { holdingsMixBp(investableSplit(it.items), r) } ?: r.sharesBp }
        val result = investReturns(txns, currency, today, mix, ready?.cpiBp) ?: return null
        val names = if (result.rows.any { it.categoryId != null }) deps.categories.listAll().associate { it.id to it.name } else emptyMap()
        val rows = result.rows.map { r ->
            ReturnsRowView(r.categoryId?.let { names[it] } ?: uiText(InvestInsightKey.RET_DEFAULT_LABEL), monthsSub(r.months), r.totalMinor)
        }
        return ReturnsView(currency, rows, result.totalMinor, result.math)
    }
}

/** «مرة: مارس» · «مرتين: مارس وسبتمبر» · «10 أشهر» (11 و12 ⇒ «شهرًا»). */
internal fun monthsSub(months: List<Int>): String = when (months.size) {
    1 -> uiText(InvestInsightKey.RET_ONCE, monthName(months[0]))
    2 -> uiText(InvestInsightKey.RET_TWICE, monthName(months[0]), monthName(months[1]))
    in 3..10 -> uiText(InvestInsightKey.RET_MONTHS, sentenceNumber(months.size))
    else -> uiText(InvestInsightKey.RET_MONTHS_MANY, sentenceNumber(months.size))
}
