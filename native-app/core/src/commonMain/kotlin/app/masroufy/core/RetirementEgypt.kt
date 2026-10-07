package app.masroufy.core

/**
 * «حاسبة التقاعد» — المعاش في **مصر** (قانون التأمينات الاجتماعية والمعاشات 148 لسنة 2019). أعداد صحيحة بس (القرش والشهور ونقاط الأساس).
 *
 * **المصدر:** كتاب القانون الرسمي من الهيئة القومية للتأمين الاجتماعي (NOSI):
 * https://www.nosi.gov.eg/ar/Lists/NOSILibrary/2-12-2019%20كتاب%20القانون%20الجديد.pdf (155 صفحة). قارئ الـPDF بيطلّع الأرقام معكوسة
 * («0.150» بدل 150.0) والعربي متلخبط ⇒ **كل حاجة هنا اتقرت بالعين من صور الصفحات** (pdfjs ⇒ PNG): جدول 5 ص153، م21 ص27–28،
 * م22 ص29، م23–24 ص30–31، م1(8)(10) ص10، م41 ص43، م156 ص96، مواد الإصدار م7 ص7. جدول 5 اتقرا **مرتين لوحده** والقرايتين اتطابقوا
 * (81 خانة، صفر اختلاف — OVERRIDES §69.5).
 *
 * **اللي القانون بيقوله (ومتنفّذ):**
 * - **م24:** المعاش = **جزء واحد من المعامل** المناظر لسن المؤمن عليه (جدول 5) **عن كل سنة**، بحد أقصى **80%** من أجر التسوية.
 *   يعني أجر التسوية × السنين ÷ المعامل (عند سن الشيخوخة المعامل 45 ⇒ 1/45 عن كل سنة).
 * - **جدول 5 ملاحظة (1):** «في حالة حساب السن يهمل كسر السنة» ⇒ السن بالسنين الكاملة. السن أقل من 50 ⇒ سطر «50 فأقل».
 * - **م21(1):** المعاش عند سن الشيخوخة بمدة **120 شهر فعلية**، وتبقى **180** «بعد خمس سنوات من تاريخ العمل بهذا القانون»
 *   (العمل بيه من 2020-01-01 — مواد الإصدار م7) ⇒ 180 من 2025-01-01.
 * - **م21(6) (المبكر):** (أ) المعاش ≥ **50%** من أجر التسوية الأخير و≥ الحد الأدنى في آخر فقرة من م24 · (ب) **240 شهر فعلية**، وتبقى
 *   **300** بعد خمس سنين (2025-01-01).
 * - **م24 آخر فقرة:** للحالات (1–5) من م21: المعاش ما يقلش عن **65% من الحد الأدنى لأجر الاشتراك** يوم الاستحقاق.
 * - **م1(10):** سن الشيخوخة **60** للبندين أولًا وثالثًا من م2 (العاملين)، و**65** للبندين ثانيًا ورابعًا (أصحاب الأعمال والمصريين في
 *   الخارج) «مع مراعاة حكم المادة 41». **م41:** رئيس الوزراء يصدر قرار بتوحيده **تدريجيًا** ليكون **65 من أول يوليو 2040**.
 *
 * **اللائحة التنفيذية (قرار رئيس الوزراء 2437/2021 — `RetirementEgyptLimits.kt`، OVERRIDES §69.8) — متنفّذ:**
 * - **الحد الأدنى والأقصى لأجر الاشتراك يوم الاستحقاق:** من الجدول الرسمي بالسنة (2020–2026)، أو اللي المستخدم كتبه (بيعلى على الجدول).
 *   سنة التقاعد لسه ما اتعلنتش (2027 وبعدها) ⇒ «غير متاح» إلا لو اتكتب — و[egyptLatestContributionWageLimits] اقتراح بس.
 * - **الأرضية:** 65% من الحد الأدنى «**وبما لا يقل عن 900 جنيه**» (م105 ثالثًا(4)). الـ900 بتتطبق حتى لو الحد الأدنى مش معروف.
 * - **سقف تاني:** 80% من **الحد الأقصى** لأجر الاشتراك (م24 آخر فقرة + م105) لما يبقى معروف.
 * - **أجر التسوية ما يزيدش عن الحد الأقصى لأجر الاشتراك** (م22 + م103 أولًا(4)) لما يبقى معروف.
 * - **شرط المبكر** بيتقارن بـ65% من الحد الأدنى بس (م102(7)(ب) — من غير الـ900).
 *
 * **اللي مش متحسب (والسبب — من غير تخمين):**
 * - **أجر التسوية (م22):** المتوسط الشهري لأجور الاشتراك من 2020، **زايد** بمتوسط نسب التضخم عن كل سنة كاملة. نسب التضخم بقرار من رئيس
 *   الهيئة و**ما لقيناهاش منشورة** (القانون م1(6) يوليو · اللائحة م1(7) أبريل) ⇒ **المستخدم بيكتبه** — [EgyptPensionInput.settlementWageMinor].
 * - **جدول التدرّج لحد 2040 (م41):** في قرار رئيس الوزراء — **ما اتنشرش** (اللائحة م303(3): الوزير «يعرض مشروع قرار») ⇒ السن **تلقائي**
 *   بس لو مؤكد (65 لأصحاب الأعمال، أو العامل اللي يتم 60 في يوليو 2040 أو بعده)، وغير كده المستخدم بيكتبه ([CalcReason.NEED_EGYPT_LEGAL_AGE]).
 * - **زيادة م163** (الفرق بين 450 جنيه و33%): النص مش واضح ⇒ مش محسوبة، والشاشة تعرض [TextKey.CALC_EGYPT_ART163_NOTE] مع كل نتيجة لمصر.
 * - **مدد قبل 2020 (م156):** أجرها «عن كل من الأجر الأساسي والأجر المتغير وفقًا لأحكام قانون التأمين الاجتماعي» القديم (79/1975) +
 *   التضخم ⇒ **«غير متاح»** ([CalcReason.EGYPT_PRE_2020]).
 * - **بعد سن الشيخوخة:** جدول 5 مفيهوش سطور ⇒ «غير متاح» ([CalcReason.EGYPT_AFTER_LEGAL_AGE]).
 *
 * **اختيارات Claude (⚠️ مش نص صريح — المالك يقدر يغيّرها):**
 * - «عن كل سنة» اتحسبت **بالشهور** (الشهور ÷ 12)، مش سنين كاملة بس.
 * - م23 آخر فقرة: «يجبر كسر السنة سنة كاملة … إذا كان من شأن ذلك استحقاق المؤمن عليه معاشًا» **مش متطبّقة** على شروط الاستحقاق
 *   (م21 بتقول «شهرًا فعلية») — الأضمن. يعني 175 شهر ⇒ «أقل من 180»، مع إن القانون ممكن يجبرها 15 سنة.
 * - حد 120/180 و240/300 بيتحدد بـ**يوم التقاعد**.
 * - شرط «50% من أجر التسوية الأخير» بيتقارن بأجر التسوية اللي اتكتب.
 */

/** مواد الإصدار م7: «يُعمل به اعتبارًا من 2020/1/1». */
const val EG_LAW_EFFECTIVE: IsoDate = "2020-01-01"

/** م21(1) و21(6)(ب): «بعد خمس سنوات من تاريخ العمل بهذا القانون». */
const val EG_FIVE_YEARS_AFTER: IsoDate = "2025-01-01"

/** م21(1): أقل مدة للمعاش عند سن الشيخوخة — قبل/بعد [EG_FIVE_YEARS_AFTER]. */
const val EG_MIN_MONTHS_BEFORE_2025 = 120
const val EG_MIN_MONTHS = 180

/** م21(6)(ب): أقل مدة فعلية للمعاش المبكر — قبل/بعد [EG_FIVE_YEARS_AFTER]. */
const val EG_EARLY_MONTHS_BEFORE_2025 = 240
const val EG_EARLY_MONTHS = 300

/** م21(6)(أ): المعاش المبكر ≥ 50% من أجر التسوية الأخير. */
const val EG_EARLY_MIN_SHARE_BP = 5_000L

/** م24: الحد الأقصى 80% من أجر التسوية. */
const val EG_MAX_PENSION_BP = 8_000L

/** م24 آخر فقرة: الحد الأدنى 65% من الحد الأدنى لأجر الاشتراك. */
const val EG_FLOOR_OF_MIN_WAGE_BP = 6_500L

/** م1(10): سن الشيخوخة 60 (العاملين — م2 أولًا وثالثًا) و65 (أصحاب الأعمال والمصريين في الخارج — ثانيًا ورابعًا). */
const val EG_EMPLOYEE_LEGAL_AGE = 60
const val EG_UNIFIED_LEGAL_AGE = 65

/** م41: سن الشيخوخة يبقى 65 «اعتبارًا من أول يوليو 2040». */
const val EG_UNIFIED_FROM: IsoDate = "2040-07-01"

/**
 * **جدول 5 «معامل حساب المعاش»** (ص153/155) — المعامل × 10 (عشان أعداد صحيحة: 45.0 ⇒ 450). المفتاح سن الشيخوخة (60–65)، والقايمة من
 * سن **«50 فأقل»** لحد سن الشيخوخة. منقول بالعين **مرتين** من صورة الصفحة الرسمية (OVERRIDES §69.5)، والاختبار بيقارن كل خانة.
 */
internal val EG_TABLE5_TENTHS: Map<Int, List<Int>> = mapOf(
    60 to listOf(818, 763, 714, 672, 634, 600, 563, 529, 500, 474, 450),
    61 to listOf(900, 833, 776, 726, 682, 643, 600, 563, 529, 500, 474, 450),
    62 to listOf(1000, 918, 849, 789, 738, 692, 643, 600, 563, 529, 500, 474, 450),
    63 to listOf(1125, 1023, 938, 865, 804, 750, 692, 643, 600, 563, 529, 500, 474, 450),
    64 to listOf(1286, 1154, 1047, 957, 882, 818, 750, 692, 643, 600, 563, 529, 500, 474, 450),
    65 to listOf(1500, 1324, 1184, 1071, 978, 900, 818, 750, 692, 643, 600, 563, 529, 500, 474, 450),
)

/** معامل جدول 5 × 10 لسن [ageYears] (سنين كاملة) وسن شيخوخة [legalAgeYears]. بعد سن الشيخوخة أو سن شيخوخة برا 60–65 ⇒ null. */
fun egyptCoefficientTenths(legalAgeYears: Int, ageYears: Int): Int? {
    val column = EG_TABLE5_TENTHS[legalAgeYears] ?: return null
    if (ageYears > legalAgeYears) return null
    return column[maxOf(ageYears, 50) - 50]
}

/** فئة المؤمن عليه في م2 (بتحدد سن الشيخوخة — م1(10)). */
enum class EgyptInsuredCategory {
    /** م2 أولًا وثالثًا: العاملين لدى الغير (حكومة · قطاع خاص …) ⇒ 60 (مع م41). */
    EMPLOYEE,
    /** م2 ثانيًا ورابعًا: أصحاب الأعمال ومن في حكمهم · المصريين العاملين في الخارج ⇒ 65. */
    SELF_EMPLOYED_OR_ABROAD,
}

data class EgyptPensionInput(
    val birthDate: IsoDate,
    val today: IsoDate,
    /** شهور الاشتراك لحد النهارده (من كشف التأمينات). */
    val monthsSoFar: Int,
    /** كام شهر منهم **قبل 2020-01-01** — أي رقم أكبر من صفر ⇒ «غير متاح» (م156). */
    val monthsBefore2020: Int = 0,
    /** **متوسط أجر التسوية الشهري** (م22) — المستخدم بيكتبه. null ⇒ «غير متاح». */
    val settlementWageMinor: Halalas?,
    val category: EgyptInsuredCategory = EgyptInsuredCategory.EMPLOYEE,
    /** سن الشيخوخة لو المستخدم كتبه (60–65) — لازم لما مش مؤكد من القانون (م41). */
    val legalAgeYears: Int? = null,
    /** الحد الأدنى لأجر الاشتراك يوم الاستحقاق لو المستخدم كتبه — بيعلى على الجدول الرسمي. null + سنة مش في الجدول ⇒ أرضية الـ65% «غير متاح». */
    val minContributionWageMinor: Halalas? = null,
    /** السن اللي ناوي تتقاعد فيه بالشهور — null = سن الشيخوخة. */
    val retireAtAgeMonths: Int? = null,
    /** الحد الأقصى لأجر الاشتراك يوم الاستحقاق لو المستخدم كتبه — بيعلى على الجدول. null + سنة مش في الجدول ⇒ سقفه مش متطبّق. */
    val maxContributionWageMinor: Halalas? = null,
)

/** معاش مصر بالتفصيل — كل خطوة لوحدها عشان الشاشة تشرحها. */
data class EgyptPension(
    /** أجر التسوية اللي اتحسب بيه — بعد سقف الحد الأقصى لأجر الاشتراك (م22) لو معروف. */
    val settlementWageMinor: Halalas,
    /** أجر التسوية المكتوب كان أكبر من الحد الأقصى لأجر الاشتراك واتقص (م22). */
    val settlementCappedToMax: Boolean,
    /** الحد الأدنى/الأقصى لأجر الاشتراك يوم الاستحقاق — المكتوب أو الجدول الرسمي، null = مش معروف. */
    val minContributionWageMinor: Halalas?,
    val maxContributionWageMinor: Halalas?,
    /** سنة الجدول الرسمي لو حد منهم جه منه (مش مكتوب) — null = الاتنين مكتوبين أو مش معروفين. */
    val limitsYear: Int?,
    val contributionMonths: Int,
    /** السن يوم التقاعد بالسنين الكاملة (جدول 5 ملاحظة 1). */
    val ageYears: Int,
    val legalAgeYears: Int,
    /** معامل جدول 5 × 10. */
    val coefficientTenths: Int,
    /** قبل السقف: الأجر × الشهور ÷ (12 × المعامل). */
    val accruedMinor: Halalas,
    /** 80% من أجر التسوية (م24). */
    val capMinor: Halalas,
    /** 80% من الحد الأقصى لأجر الاشتراك (م24 آخر فقرة) — null = الحد الأقصى مش معروف. */
    val maxWageCapMinor: Halalas?,
    /** max(65% من الحد الأدنى لأجر الاشتراك، 900 جنيه) (م105 ثالثًا(4)) — null = الحد الأدنى مش معروف (والـ900 لوحدها بتتطبق). */
    val floorMinor: Halalas?,
    val early: Boolean,
    val pensionMinor: Halalas,
)

private fun checkEgyptInput(i: EgyptPensionInput) {
    if (!isValidIsoDate(i.birthDate) || !isValidIsoDate(i.today) || i.birthDate >= i.today) throw RetirementCalcError(uiText(TextKey.CALC_BIRTH_DATE))
    if (i.monthsSoFar < 0 || i.monthsSoFar > MAX_CALC_MONTHS) throw RetirementCalcError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    if (i.monthsBefore2020 < 0 || i.monthsBefore2020 > i.monthsSoFar) throw RetirementCalcError(uiText(TextKey.CALC_MONTHS_2020))
    if (i.settlementWageMinor != null && (i.settlementWageMinor <= 0 || i.settlementWageMinor > MAX_SAFE_HALALAS)) throw RetirementCalcError(uiText(TextKey.CALC_WAGE_POSITIVE))
    if (i.minContributionWageMinor != null && (i.minContributionWageMinor <= 0 || i.minContributionWageMinor > MAX_SAFE_HALALAS)) throw RetirementCalcError(uiText(TextKey.CALC_WAGE_POSITIVE))
    if (i.maxContributionWageMinor != null && (i.maxContributionWageMinor <= 0 || i.maxContributionWageMinor > MAX_SAFE_HALALAS)) throw RetirementCalcError(uiText(TextKey.CALC_WAGE_POSITIVE))
    if (i.minContributionWageMinor != null && i.maxContributionWageMinor != null && i.maxContributionWageMinor < i.minContributionWageMinor) throw RetirementCalcError(uiText(TextKey.CALC_EGYPT_MAX_BELOW_MIN))
    if (i.legalAgeYears != null && i.legalAgeYears !in EG_EMPLOYEE_LEGAL_AGE..EG_UNIFIED_LEGAL_AGE) throw RetirementCalcError(uiText(TextKey.CALC_EGYPT_LEGAL_AGE_RANGE))
    if (i.retireAtAgeMonths != null && i.retireAtAgeMonths !in 1..MAX_AGE_MONTHS) throw RetirementCalcError(uiText(TextKey.CALC_RETIRE_AGE))
}

/** سن الشيخوخة بالسنين: اللي المستخدم كتبه، أو المؤكد من القانون (م1(10) + م41)، أو null (مش مؤكد). */
fun egyptLegalAgeYears(birthDate: IsoDate, category: EgyptInsuredCategory, typed: Int?): Int? = when {
    typed != null -> typed
    category == EgyptInsuredCategory.SELF_EMPLOYED_OR_ABROAD -> EG_UNIFIED_LEGAL_AGE
    addMonthsClamped(birthDate, EG_EMPLOYEE_LEGAL_AGE * 12) >= EG_UNIFIED_FROM -> EG_UNIFIED_LEGAL_AGE
    else -> null
}

/** المعاش المتوقع في مصر (قانون 148/2019). الاشتراك **مستمر** لحد التقاعد (نفس فرض السعودية). */
fun egyptPensionEstimate(i: EgyptPensionInput): PensionEstimate {
    checkEgyptInput(i)
    val law = PensionLaw.EG_148_2019
    val legal = egyptLegalAgeYears(i.birthDate, i.category, i.legalAgeYears)
        ?: return PensionEstimate(law, PensionStatus.UNAVAILABLE, CalcReason.NEED_EGYPT_LEGAL_AGE, null, null, null, null, null, null)
    val retireAge = i.retireAtAgeMonths ?: (legal * 12)
    val retirementDate = addMonthsClamped(i.birthDate, retireAge)
    val future = fullMonthsBetween(i.today, retirementDate)
    val months = i.monthsSoFar + future
    val ageYears = retireAge / 12
    val early = ageYears < legal
    fun result(status: PensionStatus, reason: CalcReason?, d: EgyptPension? = null) =
        PensionEstimate(law, status, reason, d?.pensionMinor, legal * 12, retireAge, retirementDate, months, future, egyptDetail = d)
    val coefficient = egyptCoefficientTenths(legal, ageYears) ?: return result(PensionStatus.UNAVAILABLE, CalcReason.EGYPT_AFTER_LEGAL_AGE)
    val after2025 = retirementDate >= EG_FIVE_YEARS_AFTER
    if (early && months < (if (after2025) EG_EARLY_MONTHS else EG_EARLY_MONTHS_BEFORE_2025)) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.EARLY_NOT_ALLOWED)
    if (months < (if (after2025) EG_MIN_MONTHS else EG_MIN_MONTHS_BEFORE_2025)) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.TOO_FEW_MONTHS)
    if (i.monthsBefore2020 > 0) return result(PensionStatus.UNAVAILABLE, CalcReason.EGYPT_PRE_2020)
    val typedWage = i.settlementWageMinor ?: return result(PensionStatus.UNAVAILABLE, CalcReason.NEED_SETTLEMENT_WAGE)
    // الحدين يوم الاستحقاق: المكتوب ⇒ الجدول الرسمي لسنة التقاعد (اللائحة م53 + أخبار الهيئة) ⇒ مش معروف
    val table = egyptContributionWageLimits(retirementDate)
    val minWage = i.minContributionWageMinor ?: table?.minMinor
    val maxWage = i.maxContributionWageMinor ?: table?.maxMinor
    val limitsYear = table?.year?.takeIf { i.minContributionWageMinor == null || i.maxContributionWageMinor == null }
    // م22 + اللائحة م103 أولًا(4): أجر التسوية (بعد زيادة التضخم) ما يزيدش عن الحد الأقصى لأجر الاشتراك
    val wage = maxWage?.let { minOf(typedWage, it) } ?: typedWage
    val accrued = mulDivHalfUp(wage, months * 10L, 12L * coefficient)
    val cap = mulDivHalfUp(wage, EG_MAX_PENSION_BP, BASIS_POINTS)
    val maxWageCap = maxWage?.let { mulDivHalfUp(it, EG_MAX_PENSION_OF_MAX_WAGE_BP, BASIS_POINTS) }
    // سقف الحد الأقصى (م24 آخر فقرة) ما يقدرش يقفل لوحده هنا طول ما الأجر متقص لنفس الحد الأقصى (80% من أجر ≤ 80% من الحد) —
    // متساب بنص القانون عشان يشتغل لو مدد ما قبل 2020 اتبنت («أجر التسوية الأكبر» — اللائحة م105 ثالثًا). تحوير بيشيله = مكافئ (§69.8)
    val base = minOf(accrued, cap, maxWageCap ?: Long.MAX_VALUE)
    val minWageShare = minWage?.let { mulDivHalfUp(it, EG_FLOOR_OF_MIN_WAGE_BP, BASIS_POINTS) }
    val floor = minWageShare?.let { maxOf(it, EG_MIN_PENSION_NUMERIC_MINOR) }
    if (early) {
        // م21(6)(أ) + اللائحة م102(7)(ب): شرط استحقاق مش رفع (50% من الأجر و65% من الحد الأدنى — من غير الـ900)
        if (base < mulDivHalfUp(wage, EG_EARLY_MIN_SHARE_BP, BASIS_POINTS)) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.EGYPT_EARLY_BELOW_HALF)
        if (minWageShare != null && base < minWageShare) return result(PensionStatus.NOT_ELIGIBLE, CalcReason.EGYPT_EARLY_BELOW_MINIMUM)
    }
    // م24 آخر فقرة (البنود 1–5) + اللائحة م105 ثالثًا(4): يترفع للأرضية، و900 جنيه على الأقل حتى لو الحد الأدنى مش معروف.
    // المبكر (البند 6) ما بيترفعش — شرطه فوق بيضمن إنه مش تحت الـ65%
    val pension = if (early) base else maxOf(base, floor ?: EG_MIN_PENSION_NUMERIC_MINOR)
    val detail = EgyptPension(wage, wage < typedWage, minWage, maxWage, limitsYear, months, ageYears, legal, coefficient, accrued, cap, maxWageCap, floor, early, pension)
    return result(PensionStatus.ESTIMATED, null, detail)
}
