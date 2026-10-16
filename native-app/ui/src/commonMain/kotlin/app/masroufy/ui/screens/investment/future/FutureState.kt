package app.masroufy.ui.screens.investment.future

import androidx.compose.ui.graphics.Color
import app.masroufy.core.Currency
import app.masroufy.core.FutureInputs
import app.masroufy.core.FutureMix
import app.masroufy.core.FuturePath
import app.masroufy.core.FutureProjection
import app.masroufy.core.Halalas
import app.masroufy.core.InvestFutureKey.*
import app.masroufy.core.InvestFutureKey
import app.masroufy.core.MileState
import app.masroufy.core.formatBp
import app.masroufy.core.uiText
import app.masroufy.core.withCurrency

/**
 * «هتبقى معاك كام» — من ناتج حالة الاستخدام لنصوص الشاشة (**عرض بس**، دوال نقية — JVM): كل رقم جاي جاهز بالهللة من `ProjectInvestFuture`،
 * وهنا بيتكتب بس (المبالغ الكبيرة بالوحدات الكاملة زي النموذج `fmt`).
 */

/** ألوان الطرق (اتفحصت في النموذج لعمى الألوان والتباين): زي ما إنت أزرق · استثمر تحويشك أخضر · استثمر أكتر كهرماني. */
internal fun pathColor(p: FuturePath): Color = when (p) {
    FuturePath.KEEP -> Color(0xFF3866A7)
    FuturePath.INVEST -> Color(0xFF0E8A6E)
    FuturePath.MORE -> Color(0xFFB7791F)
}

private fun t(key: InvestFutureKey, vararg args: String) = uiText(key, *args)

/** الرقم بالوحدات الكاملة من غير عملة: «1,134,480» · السالب بـ«−» الحقيقية (النموذج `fmt` — الكسور بتتشال). */
internal fun whole(minor: Halalas): String {
    val major = (if (minor < 0) -minor else minor) / 100
    val grouped = major.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (minor < 0) "−" else "") + grouped
}

internal fun wholeMoney(minor: Halalas, currency: Currency): String = withCurrency(whole(minor), currency)

/** «≈ ⃁ 1,134,480». */
internal fun approx(minor: Halalas, currency: Currency): String = "≈ " + wholeMoney(minor, currency)

/** اسم المدة: سنة · سنتين · 5 سنين · 15 سنة. */
fun yearsName(k: Int): String = when {
    k == 1 -> t(FUT_YEARS_ONE)
    k == 2 -> t(FUT_YEARS_TWO)
    k <= 10 -> t(FUT_YEARS_FEW, k.toString())
    else -> t(FUT_YEARS_MANY, k.toString())
}

/** سطر «سنة 2036 · هتبقى 46 سنة». */
internal fun yearLine(year: Int, age: Int?): String = t(FUT_YEAR_LINE, year.toString()) + (age?.let { t(FUT_AGE_SUFFIX, it.toString()) } ?: "")

internal fun pathTitle(p: FuturePath, i: FutureInputs): String = when (p) {
    FuturePath.KEEP -> t(FUT_KEEP_TITLE)
    FuturePath.INVEST -> t(FUT_INVEST_TITLE)
    FuturePath.MORE -> t(if (i.low || i.saveUnknown) FUT_MORE_TITLE_LOW else FUT_MORE_TITLE)
}

internal fun pathDesc(p: FuturePath, pr: FutureProjection): String {
    val i = pr.inputs
    val save = i.saveMinor
    return when (p) {
        FuturePath.KEEP -> when {
            save == null -> t(FUT_KEEP_DESC_UNKNOWN)
            save <= 0L -> t(FUT_KEEP_DESC_LOW, whole(-save))
            else -> t(FUT_KEEP_DESC, whole(save))
        }
        FuturePath.INVEST -> t(FUT_INVEST_DESC, whole(save ?: 0L))
        FuturePath.MORE -> t(FUT_MORE_DESC, whole(pr.amountMinor))
    }
}

/** جملة البطاقة البطلة للطريق المختار (+ «بالرقم زي ما هيكون وقتها» لو الشكل ده). */
internal fun heroSentence(pr: FutureProjection): String {
    val i = pr.inputs
    val s = when (pr.selected) {
        FuturePath.KEEP -> t(FUT_KEEP_SENTENCE)
        FuturePath.INVEST -> t(FUT_INVEST_SENTENCE, whole(i.saveMinor ?: 0L))
        FuturePath.MORE -> t(if (i.low || i.saveUnknown) FUT_MORE_SENTENCE_LOW else FUT_MORE_SENTENCE, whole(pr.amountMinor))
    }
    return if (pr.choice.todayMoney) s else s + t(FUT_THEN_SUFFIX)
}

/** «−» و«+» بنص مختلف حسب الدخل والتحويش (مواصفة §1.3 — والدخل المش معروف بيقول كده، مش «10% من دخلك»). */
internal fun amountHint(pr: FutureProjection): String {
    val i = pr.inputs
    val fallback = wholeMoney(app.masroufy.core.futureRules(i.countryCode).fallbackStartMinor, i.currency)
    val known = i.incomeMinor != null
    return if (i.low || i.saveUnknown) {
        if (known) t(FUT_HINT_LOW_INCOME) else t(FUT_HINT_NO_INCOME, fallback)
    } else {
        if (known) t(FUT_HINT_NORMAL, whole(i.saveMinor!!)) else t(FUT_HINT_NORMAL_NO_INCOME, whole(i.saveMinor!!), fallback)
    }
}

internal fun mixLabel(m: FutureMix): String = t(
    when (m) {
        FutureMix.MINE -> FUT_MIX_MINE
        FutureMix.SHARES -> FUT_MIX_SHARES
        FutureMix.GOLD -> FUT_MIX_GOLD
        FutureMix.PROPERTY -> FUT_MIX_PROPERTY
    },
)

/** سطر المصدر تحت «بتستثمرها في»: النسبة + اسم المصدر من سطر ملف المتوسطات نفسه (رسمي/مش رسمي — مش مكتوب في الكود). */
internal fun mixSourceLine(pr: FutureProjection): String {
    val src = pr.mixSource ?: return t(FUT_MIX_SRC_MINE, formatBp(pr.mixBp))
    return t(FUT_MIX_SRC, formatBp(pr.mixBp), sourceText(pr.choice.mix, src))
}

/** نفس سطر المصدر بتاع «هتوصل لكام؟» (الاسم · الفترة · رسمي/خاص · قديم) — من [DefaultRate.sourceText]. */
internal fun sourceText(mix: FutureMix, src: app.masroufy.core.AverageEntry): String {
    val cls = when (mix) {
        FutureMix.GOLD -> app.masroufy.core.GrowthClass.GOLD
        FutureMix.PROPERTY -> app.masroufy.core.GrowthClass.REAL_ESTATE
        else -> app.masroufy.core.GrowthClass.LOCAL_STOCKS
    }
    return app.masroufy.core.DefaultRate(cls, src.valueBp, src, src.stale, false, null).sourceText
}

/** صف محطة: (النص، الميعاد، اتعدّت؟). */
internal fun mileWhen(state: MileState, years: Int?, thisYear: Int): String = when (state) {
    MileState.PASSED -> t(FUT_MILE_PASSED)
    MileState.NEVER -> t(FUT_MILE_NEVER)
    MileState.REACHED -> t(FUT_MILE_REACHED, (thisYear + years!!).toString(), yearsName(years))
}

/** المحاور (عرض بس): «1.2 مليون» · «850 ألف» — تقريب للأقرب بأعداد صحيحة. */
internal fun shortAxis(minor: Halalas): String {
    val v = (if (minor < 0) -minor else minor) / 100
    val sign = if (minor < 0) "−" else ""
    return when {
        v >= 1_000_000 -> {
            val tenths = (v + 50_000) / 100_000
            sign + t(FUT_AXIS_MILLION, if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}")
        }
        v >= 1_000 -> sign + t(FUT_AXIS_THOUSAND, ((v + 500) / 1_000).toString())
        else -> sign + v.toString()
    }
}

/** «⃁ 1 = 13.93 ج.م» — من جنيه لكل ريال × 10,000 (عرض بس، رقمين بعد العلامة). */
internal fun fxText(egpPerSar4: Long): String {
    val hundredths = (egpPerSar4 + 50) / 100
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
