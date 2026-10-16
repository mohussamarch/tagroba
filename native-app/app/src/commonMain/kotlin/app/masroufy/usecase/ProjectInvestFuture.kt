package app.masroufy.usecase

import app.masroufy.core.AverageEntry
import app.masroufy.core.AverageKeys
import app.masroufy.core.CountriesView
import app.masroufy.core.CountryRow
import app.masroufy.core.FUTURE_MAX_YEARS
import app.masroufy.core.FutureChoice
import app.masroufy.core.FutureInputs
import app.masroufy.core.FutureMix
import app.masroufy.core.FuturePath
import app.masroufy.core.FutureProjection
import app.masroufy.core.FxRate
import app.masroufy.core.GrowthClass
import app.masroufy.core.InvestRates
import app.masroufy.core.InvestRatesState
import app.masroufy.core.IsoDate
import app.masroufy.core.Space
import app.masroufy.core.countriesTotal
import app.masroufy.core.inflationEntry
import app.masroufy.core.projectFuture

/**
 * «هتبقى معاك كام» (مواصفة الاستثمار §2.3 — §2.5): التوقع لاختيار المستخدم و«في البلدين». **من غير شاشة** — الحساب كله في
 * `core/InvestFutureMath.kt` (أعداد صحيحة)، والشاشة بتناديها على `Dispatchers.Default` (الحلقة 480 شهر).
 */
class ProjectInvestFuture {
    /** null = المتوسطات مش متاحة ⇒ البطاقة «غير متاح» وسببها، والطرق والتشارت والكروت مستخبيين. */
    fun project(i: FutureInputs, c: FutureChoice): FutureProjection? = projectFuture(i, c)

    /**
     * «في البلدين سنة …»: كل بلد بطريق «زي ما إنت» بعملته بعد نفس المدة وبنفس شكل الأرقام، والمجموع بعملة البلد الشغال بسعر صرف النهارده.
     * [others] فاضية ⇒ null (الكارت مستخبي — سؤال Q15).
     */
    fun countries(mine: FutureInputs, others: List<FutureInputs>, c: FutureChoice, fx: FxRate?): CountriesView? {
        if (others.isEmpty()) return null
        val years = c.years.coerceIn(1, FUTURE_MAX_YEARS)
        fun keep(i: FutureInputs) = projectFuture(i, c.copy(path = FuturePath.KEEP, mix = FutureMix.MINE, amountMinor = null))?.valueAt(FuturePath.KEEP, years)
        val own = CountryRow(mine.spaceId, mine.countryCode, mine.currency, keep(mine))
        val rows = others.map { CountryRow(it.spaceId, it.countryCode, it.currency, keep(it)) }
        return CountriesView(mine.thisYear + years, listOf(own) + rows, countriesTotal(own, rows, fx), mine.currency, fx)
    }
}

/** التلاتة موجودين ⇒ جاهز؛ غير كده ⇒ المفاتيح الناقصة (للسبب). */
fun ratesState(cpi: AverageEntry?, gold: AverageEntry?, shares: AverageEntry?, saudi: Boolean): InvestRatesState {
    if (cpi != null && gold != null && shares != null) return InvestRatesState.Ready(InvestRates(cpi.valueBp, gold.valueBp, shares.valueBp, cpi, gold, shares))
    val missing = listOfNotNull(
        if (cpi == null) (if (saudi) AverageKeys.CPI_SA else AverageKeys.CPI_EG) else null,
        if (gold == null) (if (saudi) AverageKeys.GOLD_USD else AverageKeys.GOLD_EGP) else null,
        if (shares == null) (if (saudi) AverageKeys.STOCKS_SA else AverageKeys.STOCKS_EG) else null,
    )
    return InvestRatesState.Unavailable(missing)
}
