package app.masroufy.usecase

import app.masroufy.core.DefaultRate
import app.masroufy.core.GrowthClass
import app.masroufy.core.InvestRates
import app.masroufy.core.InvestRatesState
import app.masroufy.core.IsoDate
import app.masroufy.core.Space

/**
 * المعدلات الرسمية للبلد (مواصفة r05 §2.1 · §4.0 بند 4): الغلاء (CPI_SA / CPI_EG) · الدهب (نفس سطر «هتوصل لكام؟» — §69.6) · الأسهم
 * (STOCKS_SA / STOCKS_EG). ناقص واحد ⇒ [InvestRatesState.Unavailable] (الكل أو لا شيء زي النموذج — سؤال مفتوح).
 * ⚠️ «الخطوة 0» — اتعملت في حزمة HOLDINGS لأن الحزم بدأت قبلها؛ بنفس اسم وتوقيع المواصفة.
 */
class LoadInvestRates(private val feeds: LoadOnlineFeeds, private val defaults: LoadDefaultRates) {
    suspend fun load(space: Space, today: IsoDate): InvestRatesState {
        val averages = (runCatching { feeds.averages() }.getOrNull() as? FeedState.Ready)?.feed
        return investRatesFrom(defaults.load(averages, space.countryCode, today))
    }
}

/** من «المعدلات الافتراضية» لحالة المعدلات — دالة نقية للاختبار. */
fun investRatesFrom(view: DefaultRatesView): InvestRatesState {
    fun pick(cls: GrowthClass): DefaultRate? = view.rates.firstOrNull { it.growthClass == cls }?.takeIf { it.rateBp != null && it.source != null }
    val cpi = view.inflation
    val gold = pick(GrowthClass.GOLD)
    val shares = pick(GrowthClass.LOCAL_STOCKS)
    val missing = listOfNotNull("cpi".takeIf { cpi == null }, "gold".takeIf { gold == null }, "shares".takeIf { shares == null })
    if (cpi == null || gold == null || shares == null) return InvestRatesState.Unavailable(missing)
    return InvestRatesState.Ready(InvestRates(cpi.valueBp, gold.rateBp!!, shares.rateBp!!, cpi, gold.source!!, shares.source!!))
}
