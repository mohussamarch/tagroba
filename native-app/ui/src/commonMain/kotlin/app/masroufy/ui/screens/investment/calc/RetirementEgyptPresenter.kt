package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.CalcReason
import app.masroufy.core.Currency
import app.masroufy.core.EG_EMPLOYEE_LEGAL_AGE
import app.masroufy.core.EG_MIN_PENSION_NUMERIC_MINOR
import app.masroufy.core.EG_UNIFIED_LEGAL_AGE
import app.masroufy.core.EgyptInsuredCategory
import app.masroufy.core.EgyptPensionInput
import app.masroufy.core.EosEnd
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_CALC_MONTHS
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.usecase.RetirementDefaults
import app.masroufy.usecase.RetirementOutcome
import app.masroufy.usecase.RetirementRequest

/** «حاسبة التقاعد» في مصر (لوحة `RetirementEgypt` — قانون 148/2019): الخانات ⇒ `RetirementRequest`، والشرح والملاحظات من تفصيل المعاش. */
object EgyptFields {
    const val BIRTH = "eg_birth"
    const val SO_FAR = "eg_soFar"
    const val PRE = "eg_pre"
    const val LEGAL = "eg_legal"
    const val RETIRE = "eg_retire"
    const val WAGE = "eg_wage"
    const val MIN = "eg_min"
    const val MAX = "eg_max"
    const val WANT = "eg_want"
    const val YEARS = "eg_years"
    const val HAVE = "eg_have"
}

fun checkEgypt(f: Map<String, String>, category: EgyptInsuredCategory, currency: Currency, today: IsoDate): RetirementCheck {
    val e = mutableMapOf<String, String>()
    val birth = parseDateField(f[EgyptFields.BIRTH].orEmpty()).orNull?.takeIf { it < today }
    if (birth == null) e[EgyptFields.BIRTH] = t(TextKey.CALC_BIRTH_DATE)
    val soFar = parseCountField(f[EgyptFields.SO_FAR].orEmpty()).orNull?.takeIf { it <= MAX_CALC_MONTHS }
    if (soFar == null) e[EgyptFields.SO_FAR] = t(TextKey.RETCALC_SOFAR_RANGE)
    val pre = optionalCount(f, EgyptFields.PRE, e) ?: 0
    if (soFar != null && pre > soFar) e[EgyptFields.PRE] = t(TextKey.CALC_MONTHS_2020)
    val legal = optionalCount(f, EgyptFields.LEGAL, e)
    if (legal != null && legal !in EG_EMPLOYEE_LEGAL_AGE..EG_UNIFIED_LEGAL_AGE) e[EgyptFields.LEGAL] = t(TextKey.CALC_EGYPT_LEGAL_AGE_RANGE)
    val retire = optionalCount(f, EgyptFields.RETIRE, e)
    if (retire != null && retire !in 1..100) e[EgyptFields.RETIRE] = t(TextKey.CALC_RETIRE_AGE)
    val wage = optionalAmount(f, EgyptFields.WAGE, currency, 1, TextKey.CALC_WAGE_POSITIVE, e)
    val min = optionalAmount(f, EgyptFields.MIN, currency, 1, TextKey.CALC_WAGE_POSITIVE, e)
    val max = optionalAmount(f, EgyptFields.MAX, currency, 1, TextKey.CALC_WAGE_POSITIVE, e)
    if (min != null && max != null && max < min) e[EgyptFields.MAX] = t(TextKey.CALC_EGYPT_MAX_BELOW_MIN)
    val pensionOk = e.isEmpty()
    val living = checkLiving(f, Triple(EgyptFields.WANT, EgyptFields.YEARS, EgyptFields.HAVE), currency, e)
    if (!pensionOk || birth == null || soFar == null) return RetirementCheck(e, null, eosChosen = true)
    val input = EgyptPensionInput(
        birthDate = birth,
        today = today,
        monthsSoFar = soFar,
        monthsBefore2020 = pre,
        settlementWageMinor = wage,
        category = category,
        legalAgeYears = legal,
        minContributionWageMinor = min,
        retireAtAgeMonths = retire?.let { it * 12 },
        maxContributionWageMinor = max,
    )
    // مصر: المكافأة «لا تنطبق» من حالة الاستخدام نفسها — [RetirementRequest.eosEnd] مالوش أثر هنا
    val request = RetirementRequest("EG", egypt = input, eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END, desiredMonthlyMinor = living.desired, savedNowMinor = living.have, years = living.years)
    return RetirementCheck(e, request, eosChosen = true)
}

/** «التقاعد على ٦٠ سنة في يونيو ٢٠٥٤، ٤٥٩ شهر اشتراك». */
fun egyptHeroDetails(o: RetirementOutcome): String? {
    val p = o.pension
    val date = p.retirementDate ?: return null
    return t(TextKey.RETEG_HERO_SUB, agePhrase(p.retireAgeMonths!! / 12 * 12), monthYearOf(date), sentenceNumber(p.contributionMonths!!))
}

/** معامل جدول 5 (× 10) ⇒ «81.8». */
fun coefficientText(tenths: Int): String = sentenceDigits("${tenths / 10}.${tenths % 10}")

/** سطور «المعاش اتحسب إزاي؟» من `EgyptPension`. */
fun egyptHow(o: RetirementOutcome, currency: Currency): List<String> {
    val d = o.pension.egyptDetail ?: return emptyList()
    val floorValue = d.floorMinor ?: EG_MIN_PENSION_NUMERIC_MINOR
    val floorApplied = !d.early && d.pensionMinor == floorValue && d.pensionMinor != d.accruedMinor && d.pensionMinor != d.capMinor && d.pensionMinor != d.maxWageCapMinor
    val floorLine = if (d.early) t(TextKey.RETEG_HOW_EARLY)
    else t(TextKey.RETEG_HOW_FLOOR, money(floorValue, currency)) + t(if (d.floorMinor == null) TextKey.RETEG_HOW_FLOOR_900_ONLY else TextKey.RETEG_HOW_FLOOR_RULE) + applied(floorApplied)
    return listOf(
        t(TextKey.RETEG_HOW_WAGE, money(d.settlementWageMinor, currency)) + (if (d.settlementCappedToMax) t(TextKey.RETEG_HOW_WAGE_CAPPED) else ""),
        t(TextKey.RETEG_HOW_ACCRUED, sentenceNumber(d.contributionMonths), coefficientText(d.coefficientTenths), sentenceNumber(d.ageYears), sentenceNumber(d.legalAgeYears), money(d.accruedMinor, currency)),
        t(TextKey.RETEG_HOW_CAP, money(d.capMinor, currency)) + applied(d.capMinor < d.accruedMinor) + (d.maxWageCapMinor?.let { t(TextKey.RETEG_HOW_MAX_CAP, money(it, currency)) } ?: ""),
        floorLine,
    )
}

/** الملاحظات الكهرماني: زيادة م163 دايمًا، ولو المعاش اتحسب: الحد الأدنى أو السقف المجهولين (بآخر رقم رسمي كاقتراح) وقص أجر التسوية. */
fun egyptNotes(o: RetirementOutcome?, defaults: RetirementDefaults?, currency: Currency): List<String> {
    val notes = mutableListOf(t(TextKey.CALC_EGYPT_ART163_NOTE))
    val d = o?.pension?.egyptDetail ?: return notes
    val latest = defaults?.egyptLatestLimits
    if (d.minContributionWageMinor == null && latest != null) notes += t(TextKey.CALC_EGYPT_FLOOR_UNKNOWN, money(latest.minMinor, currency), sentenceNumber(latest.year))
    if (d.maxContributionWageMinor == null && latest != null) notes += t(TextKey.CALC_EGYPT_MAX_CAP_UNKNOWN, money(latest.maxMinor, currency), sentenceNumber(latest.year))
    if (d.settlementCappedToMax && d.maxContributionWageMinor != null) notes += t(TextKey.CALC_EGYPT_WAGE_CAPPED, money(d.maxContributionWageMinor!!, currency))
    return notes
}

/** الملاحظة تحت «سن المعاش»: التلقائي لو اتعرف، أو «مطلوب» لو القانون مش مؤكد. */
fun egyptLegalNote(o: RetirementOutcome?, typed: Boolean, category: EgyptInsuredCategory): String = when {
    typed -> t(TextKey.RETEG_LEGAL_NOTE_TYPED)
    o?.pension?.reason == CalcReason.NEED_EGYPT_LEGAL_AGE -> t(TextKey.RETEG_LEGAL_NOTE_REQUIRED)
    o?.pension?.legalAgeMonths != null -> t(
        if (category == EgyptInsuredCategory.SELF_EMPLOYED_OR_ABROAD) TextKey.RETEG_LEGAL_NOTE_AUTO_SELF else TextKey.RETEG_LEGAL_NOTE_AUTO_2040,
        sentenceNumber(o!!.pension.legalAgeMonths!! / 12),
    )
    else -> t(TextKey.RETEG_LEGAL_NOTE_TYPED)
}
