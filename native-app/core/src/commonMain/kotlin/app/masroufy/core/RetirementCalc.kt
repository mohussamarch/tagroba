package app.masroufy.core

/**
 * «حاسبة التقاعد» — بتجاوب السؤالين (قرار المالك §69): **المعاش المتوقع** و**كام تحوّش في الشهر** عشان تعيش بالمبلغ اللي عايزه.
 * المعادلات نفسها في `RetirementSaudi.kt` و`EndOfService.kt`، وهنا: مين مستحق وإمتى، والفجوة.
 *
 * **فروض الحسبة (مكتوبة في النتيجة، ومش أرقام مخترعة):** الاشتراك **مستمر** من النهارده لحد التقاعد بنفس الأجر اللي اتكتب
 * (الأجر من مصادر الدخل تلقائيًا ويتعدّل — §69) · الفلوس المتحوشة **من غير أرباح** (اختيار المالك «من غير أرباح»).
 * **القاعدة 10:** أي مدخل ناقص ⇒ النتيجة «غير متاح» **ومعاها السبب** ([CalcReason]) — مش رقم افتراضي.
 */
enum class PensionLaw { SA_NEW, SA_OLD, EG_148_2019 }

/** ليه الرقم «غير متاح» أو ليه مفيش معاش — كل سبب ليه نص للشاشة. */
enum class CalcReason(val key: TextKey) {
    /** مكافأة نهاية الخدمة: الأجر الفعلي (نظام العمل م2) مش معروف. */
    NEED_WAGE(TextKey.CALC_REASON_NEED_WAGE),
    /** المعاش في السعودية: الأجر الأساسي مش معروف (§69.3). */
    NEED_BASIC(TextKey.CALC_REASON_NEED_BASIC),
    /** المعاش في السعودية: بدل السكن ما اتكتبش (صفر لازم يتكتب صراحة — القاعدة 10). */
    NEED_HOUSING(TextKey.CALC_REASON_NEED_HOUSING),
    NEED_MONTHS_AT_2024(TextKey.CALC_REASON_NEED_MONTHS_AT_2024),
    TOO_FEW_MONTHS(TextKey.CALC_REASON_TOO_FEW_MONTHS),
    EARLY_NOT_ALLOWED(TextKey.CALC_REASON_EARLY_NOT_ALLOWED),
    /** مصر م21(6)(أ): المعاش المبكر أقل من 50% من أجر التسوية. */
    EGYPT_EARLY_BELOW_HALF(TextKey.CALC_REASON_EGYPT_EARLY_BELOW_HALF),
    /** مصر م21(6)(أ) + م24 آخر فقرة: المعاش المبكر أقل من 65% من الحد الأدنى لأجر الاشتراك (لما المستخدم كتبه). */
    EGYPT_EARLY_BELOW_MINIMUM(TextKey.CALC_REASON_EGYPT_EARLY_BELOW_MINIMUM),
    /** مصر م22: متوسط أجر التسوية ما اتكتبش (معدلات التضخم الرسمية مش في القانون). */
    NEED_SETTLEMENT_WAGE(TextKey.CALC_REASON_NEED_SETTLEMENT_WAGE),
    /** مصر م41: سن الشيخوخة بيتحدد بقرار رئيس الوزراء لحد يوليو 2040 — الجدول مش في نص القانون. */
    NEED_EGYPT_LEGAL_AGE(TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE),
    /** مصر م156: مدد قبل 2020 (أجرها بقانون 79/1975 ومعدلات التضخم) — مش متحسبة. */
    EGYPT_PRE_2020(TextKey.CALC_REASON_EGYPT_PRE_2020),
    /** مصر: جدول 5 مفيهوش سطور بعد سن الشيخوخة. */
    EGYPT_AFTER_LEGAL_AGE(TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE),
    NEED_YEARS(TextKey.CALC_REASON_NEED_YEARS),
    NEED_PENSION(TextKey.CALC_REASON_NEED_PENSION),
    NEED_JOB_START(TextKey.CALC_REASON_NEED_JOB_START),
    NO_MONTHS_TO_SAVE(TextKey.CALC_REASON_NO_MONTHS_TO_SAVE),
    ;

    val text: String get() = uiText(key)
}

enum class PensionStatus { ESTIMATED, NOT_ELIGIBLE, UNAVAILABLE }

data class SaudiPensionInput(
    val birthDate: IsoDate,
    val today: IsoDate,
    /** السؤال الوحيد: «اشتركت في التأمينات قبل يوليو 2024؟» ⇒ القديم، وإلا الجديد. */
    val contributedBeforeJuly2024: Boolean,
    /** شهور الاشتراك لحد النهارده (من كشف التأمينات). */
    val monthsSoFar: Int,
    /**
     * القديم بس: شهور الاشتراك يوم 2024-07-03 (بند «خامسًا» من م/273 بيحدد السن منها). null ⇒ «غير متاح» — إلا لو عمرك وقتها
     * 48 سنة و6 شهور أو أكتر (البند ما ينطبقش أصلًا ⇒ مش محتاجينها).
     */
    val monthsAtEffective: Int? = null,
    /** القديم بس (اختياري): كام شهر من [monthsSoFar] كان **قبل 1/1/1422هـ** (بتتقسم على 600 بدل 480). */
    val monthsBefore1422: Int = 0,
    /**
     * **الأجر الأساسي** الشهري (قرار المالك §69.3: «الأساسي والسكن لوحدهم»). الأجر الخاضع للاشتراك = الأساسي + بدل السكن، وبيتاخد
     * كمتوسط ثابت (الجديد: أعلى 180 شهر — م26(1) · القديم: آخر سنتين). null ⇒ «غير متاح» ([CalcReason.NEED_BASIC]).
     * ⚠️ إن الخاضع للاشتراك = الأساسي + السكن بس ده قرار المالك؛ النسخة الإنجليزية من م/273 ما اتراجعتش على تعريف الأجر بالحرف هنا.
     */
    val basicWageMinor: Halalas?,
    /** **بدل السكن** الشهري. null ⇒ «غير متاح» ([CalcReason.NEED_HOUSING]) — لو مفيش بدل سكن يتكتب صفر صراحة (القاعدة 10). */
    val housingAllowanceMinor: Halalas?,
    /** السن اللي ناوي تتقاعد فيه بالشهور — null = السن النظامية بتاعتك. */
    val retireAtAgeMonths: Int? = null,
)

data class PensionEstimate(
    val law: PensionLaw,
    val status: PensionStatus,
    val reason: CalcReason?,
    val pensionMinor: Halalas?,
    val legalAgeMonths: Int?,
    val retireAgeMonths: Int?,
    val retirementDate: IsoDate?,
    /** شهور الاشتراك يوم التقاعد (لو الاشتراك استمر). */
    val contributionMonths: Int?,
    /** الشهور من النهارده لحد التقاعد — دي اللي هتحوّش فيها. */
    val monthsUntilRetirement: Int?,
    val newDetail: SaudiNewPension? = null,
    val oldDetail: SaudiOldPension? = null,
    val egyptDetail: EgyptPension? = null,
)

class RetirementCalcError(message: String) : IllegalArgumentException(message)

internal const val MAX_AGE_MONTHS = 100 * 12

private fun checkInput(i: SaudiPensionInput) {
    if (!isValidIsoDate(i.birthDate) || !isValidIsoDate(i.today) || i.birthDate >= i.today) throw RetirementCalcError(uiText(TextKey.CALC_BIRTH_DATE))
    if (i.monthsSoFar < 0 || i.monthsSoFar > MAX_CALC_MONTHS) throw RetirementCalcError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    if (i.monthsBefore1422 < 0 || i.monthsBefore1422 > i.monthsSoFar) throw RetirementCalcError(uiText(TextKey.CALC_MONTHS_1422))
    if (i.monthsAtEffective != null && (i.monthsAtEffective < 0 || i.monthsAtEffective > i.monthsSoFar)) throw RetirementCalcError(uiText(TextKey.CALC_MONTHS_AT_2024))
    if (i.basicWageMinor != null && (i.basicWageMinor <= 0 || i.basicWageMinor > MAX_SAFE_HALALAS)) throw RetirementCalcError(uiText(TextKey.CALC_WAGE_POSITIVE))
    if (i.housingAllowanceMinor != null && (i.housingAllowanceMinor < 0 || i.housingAllowanceMinor > MAX_SAFE_HALALAS)) throw RetirementCalcError(uiText(TextKey.CALC_HOUSING_NOT_NEGATIVE))
    if (i.retireAtAgeMonths != null && i.retireAtAgeMonths !in 1..MAX_AGE_MONTHS) throw RetirementCalcError(uiText(TextKey.CALC_RETIRE_AGE))
}

/** الأجر الخاضع للاشتراك = الأساسي + بدل السكن (§69.3) — أو سبب «غير متاح» لو واحد منهم ناقص. */
private fun contributionWage(i: SaudiPensionInput): Pair<Halalas?, CalcReason?> {
    val basic = i.basicWageMinor ?: return null to CalcReason.NEED_BASIC
    val housing = i.housingAllowanceMinor ?: return null to CalcReason.NEED_HOUSING
    return addMoney(basic, housing) to null
}

/** المعاش المتوقع في السعودية. */
fun saudiPensionEstimate(i: SaudiPensionInput): PensionEstimate {
    checkInput(i)
    val law = if (i.contributedBeforeJuly2024) PensionLaw.SA_OLD else PensionLaw.SA_NEW
    fun unavailable(reason: CalcReason) = PensionEstimate(law, PensionStatus.UNAVAILABLE, reason, null, null, null, null, null, null)
    // شهور يوليو 2024 لازمة بس لو العمر وقتها أقل من 48 و6 شهور (غير كده بند «خامسًا» ما ينطبقش مهما كانت الشهور ⇒ 60 و300)
    val outsideFifthByAge = fullMonthsBetween(i.birthDate, SA_NEW_LAW_EFFECTIVE) >= SA_DECREE_FIFTH_AGE_LIMIT_MONTHS
    val atEffective = i.monthsAtEffective ?: if (outsideFifthByAge) SA_DECREE_FIFTH_MONTHS_LIMIT else 0
    if (law == PensionLaw.SA_OLD && i.monthsAtEffective == null && !outsideFifthByAge) return unavailable(CalcReason.NEED_MONTHS_AT_2024)
    val legal = if (law == PensionLaw.SA_NEW) SA_NEW_LEGAL_AGE_MONTHS else saudiOldLawLegalAgeMonths(i.birthDate, atEffective)
    val retireAge = i.retireAtAgeMonths ?: legal
    val retirementDate = addMonthsClamped(i.birthDate, retireAge)
    val future = fullMonthsBetween(i.today, retirementDate)
    val months = i.monthsSoFar + future
    val before = maxOf(0, legal - retireAge)
    val after = maxOf(0, retireAge - legal)
    fun result(status: PensionStatus, reason: CalcReason?, pension: Halalas?, nd: SaudiNewPension? = null, od: SaudiOldPension? = null) =
        PensionEstimate(law, status, reason, pension, legal, retireAge, retirementDate, months, future, nd, od)
    val (wage, wageMissing) = contributionWage(i)
    if (law == PensionLaw.SA_NEW) {
        if (before > 0 && (before > SA_NEW_EARLY_MAX_MONTHS || months < SA_NEW_EARLY_MIN_CONTRIB_MONTHS)) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.EARLY_NOT_ALLOWED, null)
        if (months < SA_NEW_MIN_CONTRIB_MONTHS) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.TOO_FEW_MONTHS, null)
        wage ?: return result(PensionStatus.UNAVAILABLE, wageMissing, null)
        val d = saudiNewPension(wage, months, before, after)
        return result(PensionStatus.ESTIMATED, null, d.pensionMinor, nd = d)
    }
    if (before > 0 && months < saudiOldLawEarlyMonths(i.birthDate, atEffective)) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.EARLY_NOT_ALLOWED, null)
    if (months < SA_OLD_MIN_CONTRIB_MONTHS) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.TOO_FEW_MONTHS, null)
    wage ?: return result(PensionStatus.UNAVAILABLE, wageMissing, null)
    val d = saudiOldPension(wage, months - i.monthsBefore1422, i.monthsBefore1422)
    return result(PensionStatus.ESTIMATED, null, d.pensionMinor, od = d)
}

/** المكافأة في حسبة التقاعد: معروفة · مش منطبقة (مصر · مالكش شغل) · مش معروفة (ناقص تاريخ بداية الشغل أو الأجر). */
sealed interface EosPart {
    data class Known(val result: EndOfServiceResult) : EosPart
    data class NotApplicable(val key: TextKey) : EosPart
    data class Unavailable(val reason: CalcReason) : EosPart
}

data class RetirementGap(
    val desiredMonthlyMinor: Halalas,
    val pensionMinor: Halalas,
    /** اللي ناقصك كل شهر بعد التقاعد = المطلوب − المعاش (مش أقل من صفر). */
    val shortfallPerMonthMinor: Halalas,
    val lastingMonths: Int,
    val totalNeedMinor: Halalas,
    val eosMinor: Halalas,
    val savedNowMinor: Halalas,
    /** الباقي بعد المكافأة واللي معاك (مش أقل من صفر). */
    val remainingMinor: Halalas,
    val monthsToSave: Int,
    /** كام تحوّش في الشهر لحد التقاعد — لفوق بهللة. */
    val perMonthMinor: Halalas,
)

/** الفجوة أو سبب إنها «غير متاح». */
data class GapOutcome(val gap: RetirementGap?, val reason: CalcReason?)

/**
 * كام تحوّش في الشهر: (المطلوب − المعاش) × شهور التقاعد − المكافأة − اللي معاك ⇒ ÷ الشهور لحد التقاعد (لفوق). من غير أرباح.
 * مدة التقاعد **إجبارية** (مفيش رقم ليه مصدر نفترضه): [years] سنين، **أو** [untilAgeMonths] «لحد سن كذا» (− سن التقاعد).
 */
fun retirementGap(desiredMonthlyMinor: Halalas, pension: PensionEstimate, eos: EosPart, savedNowMinor: Halalas, years: Int?, untilAgeMonths: Int?): GapOutcome {
    if (desiredMonthlyMinor <= 0 || desiredMonthlyMinor > MAX_SAFE_HALALAS) throw RetirementCalcError(uiText(TextKey.CALC_DESIRED_POSITIVE))
    if (savedNowMinor < 0 || savedNowMinor > MAX_SAFE_HALALAS) throw RetirementCalcError(uiText(TextKey.CALC_SAVED_NOT_NEGATIVE))
    if (years != null && years !in 1..100) throw RetirementCalcError(uiText(TextKey.CALC_YEARS_RANGE))
    // مش مستحق معاش (أقل من المدة) ⇒ التعويض بدفعة واحدة (م/273 م25) مش متحسب ⇒ الفجوة «غير متاح» بنفس السبب، مش معاش صفر مؤكد
    val p = pension.pensionMinor ?: return GapOutcome(null, pension.reason ?: CalcReason.NEED_PENSION)
    val retireAge = pension.retireAgeMonths!!
    if (untilAgeMonths != null && untilAgeMonths !in (retireAge + 1)..MAX_AGE_MONTHS) throw RetirementCalcError(uiText(TextKey.CALC_UNTIL_AGE))
    val lasting = years?.let { it * 12 } ?: untilAgeMonths?.let { it - retireAge } ?: return GapOutcome(null, CalcReason.NEED_YEARS)
    val eosMinor = when (eos) {
        is EosPart.Known -> eos.result.payableMinor
        is EosPart.NotApplicable -> 0L
        is EosPart.Unavailable -> return GapOutcome(null, eos.reason)
    }
    val shortfall = maxOf(0L, subtractMoney(desiredMonthlyMinor, p))
    val need = multiplyMoneyByInt(shortfall, lasting.toLong())
    val remaining = maxOf(0L, subtractMoney(need, addMoney(eosMinor, savedNowMinor)))
    val months = pension.monthsUntilRetirement!!
    if (months == 0 && remaining > 0) return GapOutcome(null, CalcReason.NO_MONTHS_TO_SAVE)
    val perMonth = if (remaining == 0L) 0L else ceilDivMoney(remaining, months)
    return GapOutcome(RetirementGap(desiredMonthlyMinor, p, shortfall, lasting, need, eosMinor, savedNowMinor, remaining, months, perMonth), null)
}
