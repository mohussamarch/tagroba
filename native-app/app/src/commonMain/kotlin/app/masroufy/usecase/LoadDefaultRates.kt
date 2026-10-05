package app.masroufy.usecase

import app.masroufy.core.AverageEntry
import app.masroufy.core.AverageKeys
import app.masroufy.core.AveragesFeed
import app.masroufy.core.CurrencyNote
import app.masroufy.core.DefaultRate
import app.masroufy.core.GrowthClass
import app.masroufy.core.GrowthComparison
import app.masroufy.core.GrowthLine
import app.masroufy.core.GrowthRatesConfig
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.averagesFileIsOld
import app.masroufy.core.compareGrowth
import app.masroufy.core.defaultRates
import app.masroufy.core.describeEntry
import app.masroufy.core.inflationEntry
import app.masroufy.core.stockNote
import app.masroufy.core.uiText

/**
 * «هتوصل لكام؟» (OVERRIDES §69.6) — حالات استخدام من غير شاشة:
 * - [LoadDefaultRates]: المعدل الافتراضي لكل نوع **بمصدره** من ملف المتوسطات (`public/averages.json`) لبلد المساحة الشغالة.
 *   الملف بيتقري برا (زي ملف الأسعار — اللي بينادي بيدّي `AveragesFeed` أو null لو لسه ما وصلش).
 * - [CompareSavingsGrowth]: حاسبة الادخار ⇒ الخمسة جنب بعض، بالافتراضي أو باللي المستخدم كتبه، و«بقيمة فلوس النهارده».
 * الإعداد ([GrowthRatesConfig]) فيه القرارين اللي مستنيين كروت المالك (مصدر رقم العقار · أسهم مصر).
 */

data class DefaultRatesView(
    /** الخمسة بالترتيب (ذهب · عقار · وديعة · أسهم · كاش). */
    val rates: List<DefaultRate>,
    /** التضخم الرسمي للبلد (زرار «بقيمة فلوس النهارده») — null = «غير متاح». */
    val inflation: AverageEntry?,
    val inflationStale: Boolean,
) {
    fun of(cls: GrowthClass): DefaultRate = rates.first { it.growthClass == cls }
}

class LoadDefaultRates(private val config: GrowthRatesConfig = GrowthRatesConfig()) {
    fun load(feed: AveragesFeed?, countryCode: String?, today: IsoDate): DefaultRatesView {
        val inflation = inflationEntry(feed, countryCode)
        val old = feed != null && averagesFileIsOld(feed, today)
        return DefaultRatesView(defaultRates(feed, countryCode, config, today), inflation, inflation != null && (inflation.stale || old))
    }
}

/** سطر في المقارنة بعد ما اتحدد المعدل: الرقم + جاي منين + سطر الجنيه لو فيه. */
data class GrowthChoice(
    val line: GrowthLine,
    val default: DefaultRate,
    /** المستخدم كتب الرقم ده بنفسه (بيغلب الافتراضي). */
    val typedByUser: Boolean,
    /** سطر المصدر: مصدر الافتراضي، أو «النسبة اللي كتبتها»، أو سبب «غير متاح». */
    val sourceText: String,
    val note: CurrencyNote?,
)

data class GrowthCompareOutcome(
    val comparison: GrowthComparison,
    val choices: List<GrowthChoice>,
    val inflation: AverageEntry?,
    /** سبب إن «بقيمة فلوس النهارده» مش متاح (الزرار شغال والتضخم مش معروف) — null غير كده. */
    val todayMoneyReason: String?,
    /** سطر مصدر التضخم لو اتستخدم. */
    val inflationText: String?,
)

class CompareSavingsGrowth(private val rates: LoadDefaultRates = LoadDefaultRates()) {
    /**
     * [monthlyMinor] شهريًا لمدة [months] شهر ومعاك [startMinor] — في الخمسة. [userRates] اللي المستخدم كتبه (بيغلب الافتراضي، والوديعة
     * والأسهم في مصر لازم تتكتب). [todayMoney] = زرار «بقيمة فلوس النهارده».
     */
    fun compare(
        monthlyMinor: Halalas,
        months: Int,
        startMinor: Halalas,
        today: IsoDate,
        feed: AveragesFeed?,
        countryCode: String?,
        userRates: Map<GrowthClass, Int> = emptyMap(),
        todayMoney: Boolean = false,
    ): GrowthCompareOutcome {
        val view = rates.load(feed, countryCode, today)
        val used = GrowthClass.entries.associateWith { cls -> if (cls == GrowthClass.CASH) 0 else userRates[cls] ?: view.of(cls).rateBp }
        val comparison = compareGrowth(monthlyMinor, months, startMinor, today, used, todayMoney, view.inflation?.valueBp)
        val choices = comparison.lines.map { line ->
            val d = view.of(line.growthClass)
            val typed = line.growthClass != GrowthClass.CASH && userRates.containsKey(line.growthClass)
            val source = when {
                typed -> uiText(TextKey.GROWTH_YOUR_RATE)
                d.rateBp != null || line.growthClass == GrowthClass.CASH -> d.sourceText
                else -> d.reason ?: uiText(TextKey.GROWTH_NA_MISSING)
            }
            // رقم بالجنيه كتبه المستخدم (ذهب أو أسهم): سطر الجنيه بيتحسب على رقمه هو (نزول الجنيه من الملف)
            val fall = feed?.entries?.get(AverageKeys.EGP_PER_USD)
            val note = if (typed && d.note != null) fall?.let { stockNote(line.rateBp, it) } else d.note
            GrowthChoice(line, d, typed, source, note)
        }
        val reason = if (todayMoney && view.inflation == null) uiText(TextKey.GROWTH_NA_INFLATION) else null
        val inflationText = if (todayMoney) view.inflation?.let { describeEntry(it, view.inflationStale) } else null
        return GrowthCompareOutcome(comparison, choices, view.inflation, reason, inflationText)
    }
}
