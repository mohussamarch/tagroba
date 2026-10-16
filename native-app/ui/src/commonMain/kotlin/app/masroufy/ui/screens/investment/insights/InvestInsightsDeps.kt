package app.masroufy.ui.screens.investment.insights

import app.masroufy.core.IsoDate
import app.masroufy.usecase.LoadIncomeRope
import app.masroufy.usecase.LoadInvestReturns
import app.masroufy.usecase.LoadInvestTip

/**
 * حزمة INSIGHTS في «الاستثمار» الجديد (مواصفة النقل §4.3): حالات الاستخدام اللي الأقسام محتاجاها **بس** — التنفيذ `:wiring` →
 * `InvestInsightsGraph`. «حاجاتي» والمتوسطات بتيجي من شاشة الاستثمار مرة واحدة لكل تحميل (§1.0 — الأداء).
 */
interface InvestInsightsDeps {
    /** `current(today, holdings)` · `close(tipId)` · `all(space)`. */
    val tips: LoadInvestTip

    /** `load(today, currency)` — null = مفيش دخل متسجّل. */
    val rope: LoadIncomeRope

    /** `load(today, currency, holdings, rates)` — null = ما وصلكش عائد السنة دي. */
    val returns: LoadInvestReturns

    fun today(): IsoDate
}
