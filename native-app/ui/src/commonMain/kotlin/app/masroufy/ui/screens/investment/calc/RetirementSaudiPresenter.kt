package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.Currency
import app.masroufy.core.EosEnd
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_CALC_MONTHS
import app.masroufy.core.PensionLaw
import app.masroufy.core.SA_OLD_LEGAL_AGE_MONTHS
import app.masroufy.core.SA_OLD_MIN_PENSION_MINOR
import app.masroufy.core.SaudiPensionInput
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.usecase.RetirementOutcome
import app.masroufy.usecase.RetirementRequest

/** «حاسبة التقاعد» في السعودية (لوحة `RetirementSaudi`): قراية الخانات ⇒ `RetirementRequest`، وشرح «كيف حُسب المعاش؟» من تفصيل حالة الاستخدام. */
object SaudiFields {
    const val BIRTH = "birth"
    const val SO_FAR = "soFar"
    const val AT_2024 = "at2024"
    const val M_1422 = "m1422"
    const val RETIRE = "retire"
    const val BASIC = "basic"
    const val HOUSING = "housing"
    const val JOB = "job"
    const val EOS_WAGE = "eosWage"
    const val WANT = "want"
    const val YEARS = "years"
    const val HAVE = "have"
}

data class RetirementCheck(val errors: Map<String, String>, val request: RetirementRequest?, val eosChosen: Boolean)

/** الخانات المشتركة (اللي عايز تعيش بيه · كم سنة · معك كام) — غلطهم ما بيوقفش المعاش، بيوقف الفجوة بس. */
internal data class Living(val desired: Halalas?, val years: Int?, val have: Halalas)

internal fun checkLiving(f: Map<String, String>, ids: Triple<String, String, String>, currency: Currency, errors: MutableMap<String, String>): Living {
    val (wantId, yearsId, haveId) = ids
    val desired = when (val p = parseAmountField(f[wantId].orEmpty(), currency)) {
        Parsed.Empty -> null
        is Parsed.Ok -> p.value.takeIf { it > 0 } ?: null.also { errors[wantId] = t(TextKey.CALC_DESIRED_POSITIVE) }
        Parsed.Bad -> null.also { errors[wantId] = t(TextKey.CALC_DESIRED_POSITIVE) }
    }
    val years = when (val p = parseCountField(f[yearsId].orEmpty())) {
        Parsed.Empty -> null
        is Parsed.Ok -> p.value.takeIf { it in 1..100 } ?: null.also { errors[yearsId] = t(TextKey.CALC_YEARS_RANGE) }
        Parsed.Bad -> null.also { errors[yearsId] = t(TextKey.CALC_YEARS_RANGE) }
    }
    val have = when (val p = parseAmountField(f[haveId].orEmpty(), currency)) {
        Parsed.Empty -> 0L
        is Parsed.Ok -> p.value.takeIf { it >= 0 } ?: 0L.also { errors[haveId] = t(TextKey.CALC_SAVED_NOT_NEGATIVE) }
        Parsed.Bad -> 0L.also { errors[haveId] = t(TextKey.MONEY_BAD_FORMAT) }
    }
    return Living(desired, years, have)
}

/** عدد شهور اختياري: فاضي ⇒ null · مش رقم ⇒ خطأ. */
internal fun optionalCount(f: Map<String, String>, id: String, errors: MutableMap<String, String>): Int? = when (val p = parseCountField(f[id].orEmpty())) {
    Parsed.Empty -> null
    Parsed.Bad -> null.also { errors[id] = t(TextKey.CALCUI_WHOLE_NUMBER) }
    is Parsed.Ok -> p.value
}

/** مبلغ اختياري: فاضي ⇒ null · مش مقروء ⇒ خطأ · [min] أقل قيمة مقبولة. */
internal fun optionalAmount(f: Map<String, String>, id: String, currency: Currency, min: Long, minKey: TextKey, errors: MutableMap<String, String>): Halalas? =
    when (val p = parseAmountField(f[id].orEmpty(), currency)) {
        Parsed.Empty -> null
        Parsed.Bad -> null.also { errors[id] = t(TextKey.MONEY_BAD_FORMAT) }
        is Parsed.Ok -> p.value.takeIf { it >= min } ?: null.also { errors[id] = t(minKey) }
    }

fun checkSaudi(f: Map<String, String>, contributedBefore2024: Boolean, end: EosEnd?, currency: Currency, today: IsoDate): RetirementCheck {
    val e = mutableMapOf<String, String>()
    val birth = parseDateField(f[SaudiFields.BIRTH].orEmpty()).orNull?.takeIf { it < today }
    if (birth == null) e[SaudiFields.BIRTH] = t(TextKey.CALC_BIRTH_DATE)
    val soFar = parseCountField(f[SaudiFields.SO_FAR].orEmpty()).orNull?.takeIf { it <= MAX_CALC_MONTHS }
    if (soFar == null) e[SaudiFields.SO_FAR] = t(TextKey.RETCALC_SOFAR_RANGE)
    val at2024 = if (contributedBefore2024) optionalCount(f, SaudiFields.AT_2024, e) else null
    if (at2024 != null && soFar != null && at2024 > soFar) e[SaudiFields.AT_2024] = t(TextKey.CALC_MONTHS_AT_2024)
    val m1422 = if (contributedBefore2024) optionalCount(f, SaudiFields.M_1422, e) ?: 0 else 0
    if (soFar != null && m1422 > soFar) e[SaudiFields.M_1422] = t(TextKey.CALC_MONTHS_1422)
    val retire = optionalCount(f, SaudiFields.RETIRE, e)
    if (retire != null && retire !in 1..100) e[SaudiFields.RETIRE] = t(TextKey.CALC_RETIRE_AGE)
    val basic = optionalAmount(f, SaudiFields.BASIC, currency, 1, TextKey.CALC_WAGE_POSITIVE, e)
    val housing = optionalAmount(f, SaudiFields.HOUSING, currency, 0, TextKey.CALC_HOUSING_NOT_NEGATIVE, e)
    val job = parseDateField(f[SaudiFields.JOB].orEmpty()).orNull
    val eosWage = optionalAmount(f, SaudiFields.EOS_WAGE, currency, 1, TextKey.CALC_WAGE_POSITIVE, e)
    val pensionOk = e.isEmpty()
    val living = checkLiving(f, Triple(SaudiFields.WANT, SaudiFields.YEARS, SaudiFields.HAVE), currency, e)
    if (!pensionOk || birth == null || soFar == null) return RetirementCheck(e, null, end != null)
    val input = SaudiPensionInput(
        birthDate = birth,
        today = today,
        contributedBeforeJuly2024 = contributedBefore2024,
        monthsSoFar = soFar,
        monthsAtEffective = at2024,
        monthsBefore1422 = m1422,
        basicWageMinor = basic,
        housingAllowanceMinor = housing,
        retireAtAgeMonths = retire?.let { it * 12 },
    )
    // إزاي الشغل هيخلص بيتسأل كل مرة: لحد ما يتختار المعاش بيتحسب والمكافأة والفجوة «اختر كيف سينتهي عملك» (الاختيار هنا مكانه بس)
    val request = RetirementRequest(
        countryCode = "SA",
        saudi = input,
        eosWageMinor = eosWage,
        jobStartedAt = job,
        eosEnd = end ?: EosEnd.EMPLOYER_OR_CONTRACT_END,
        desiredMonthlyMinor = if (end == null) null else living.desired,
        savedNowMinor = living.have,
        years = living.years,
    )
    return RetirementCheck(e, request, end != null)
}

/** «النظام القديم، التقاعد عند ٦٥ سنة في أبريل ٢٠٥٥، ٤٥٨ شهر اشتراك». */
fun saudiHeroDetails(o: RetirementOutcome): String? {
    val p = o.pension
    val date = p.retirementDate ?: return null
    val law = t(if (p.law == PensionLaw.SA_OLD) TextKey.RETSA_LAW_OLD else TextKey.RETSA_LAW_NEW)
    return t(TextKey.RETSA_HERO_SUB, law, agePhrase(p.retireAgeMonths!!), monthYearOf(date), sentenceNumber(p.contributionMonths!!))
}

/** سطور «كيف حُسب المعاش؟» من تفصيل المعاش (`SaudiNewPension` · `SaudiOldPension`). */
fun saudiHow(o: RetirementOutcome, currency: Currency): List<String> {
    val p = o.pension
    p.newDetail?.let { d ->
        val adjust = when {
            d.reductionMinor > 0 -> t(TextKey.RETSA_HOW_NEW_REDUCE, money(d.reductionMinor, currency), sentenceNumber(d.reductionMonths))
            d.increaseMinor > 0 -> t(TextKey.RETSA_HOW_NEW_INCREASE, money(d.increaseMinor, currency))
            else -> t(TextKey.RETSA_HOW_NEW_NONE)
        }
        return listOf(
            t(TextKey.RETSA_HOW_NEW_WAGE, money(d.averageWageMinor, currency)),
            t(TextKey.RETSA_HOW_NEW_ACCRUED, sentenceNumber(d.contributionMonths), money(d.accruedMinor, currency)) + (if (d.baseMinor < d.accruedMinor) t(TextKey.RETSA_HOW_CAPPED) else ""),
            t(TextKey.RETSA_HOW_NEW_MIN, money(d.minimumMinor, currency)) + applied(d.minimumMinor > d.baseMinor),
            adjust,
        )
    }
    p.oldDetail?.let { d ->
        val accrued = if (d.monthsBefore1422 > 0) t(TextKey.RETSA_HOW_OLD_ACCRUED_1422, sentenceNumber(d.monthsAfter1422), sentenceNumber(d.monthsBefore1422), money(d.accruedMinor, currency))
        else t(TextKey.RETSA_HOW_OLD_ACCRUED, sentenceNumber(d.monthsAfter1422), money(d.accruedMinor, currency))
        val legal = p.legalAgeMonths ?: SA_OLD_LEGAL_AGE_MONTHS
        return listOf(
            t(TextKey.RETSA_HOW_OLD_WAGE, money(d.averageWageMinor, currency)),
            accrued + (if (d.baseMinor < d.accruedMinor) t(TextKey.RETSA_HOW_CAPPED) else ""),
            t(TextKey.RETSA_HOW_OLD_MIN, money(SA_OLD_MIN_PENSION_MINOR, currency)) + applied(d.baseMinor < SA_OLD_MIN_PENSION_MINOR),
            t(if (legal == SA_OLD_LEGAL_AGE_MONTHS) TextKey.RETSA_HOW_OLD_AGE_60 else TextKey.RETSA_HOW_OLD_AGE_FIFTH, agePhrase(legal)),
        )
    }
    return emptyList()
}
