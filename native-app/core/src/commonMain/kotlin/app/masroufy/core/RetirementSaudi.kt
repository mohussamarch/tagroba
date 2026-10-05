package app.masroufy.core

/**
 * «حاسبة التقاعد» — المعاش في السعودية (قرار المالك 2026-10-05، OVERRIDES §69). أعداد صحيحة بس (الهللة والشهور ونقاط الأساس).
 * النظام بيتحدد من سؤال واحد: **اشتركت قبل يوليو 2024؟** ⇒ القديم (م/33 + بند «خامسًا» من المرسوم م/273)، وإلا ⇒ الجديد (م/273).
 *
 * **المصادر (كل ثابت جنبه مادته):**
 * - النظام الجديد: «نظام التأمينات الاجتماعية» بالمرسوم الملكي **م/273 وتاريخ 26/12/1445هـ** (ساري 2024-07-03)، النسخة الإنجليزية الرسمية:
 *   https://misa.gov.sa/app/uploads/2025/07/Social-Insurance-Law.pdf — وقرار مجلس الوزراء **1022** في نفس الملف.
 * - النظام القديم: صفحة «المعاش» الرسمية في التأمينات https://www.gosi.gov.sa/GOSIOnline/Annuity&locale=en_US (المعادلة والحد الأدنى
 *   و120 شهر و300 شهر للمبكر). ⚠️ الصفحة دي لسه بتقول «سن 60» لكل الناس — **وده اتغير** لناس كتير ببند «خامسًا» من المرسوم (تحت).
 */

/** تاريخ سريان النظام الجديد (اليوم التالي لمرور سنة على النشر — المرسوم م/273 ومادة السريان؛ GOSI: 3 يوليو 2024). */
const val SA_NEW_LAW_EFFECTIVE: IsoDate = "2024-07-03"

/** م/273 المادة 16(1): السن النظامية 65 سنة. */
const val SA_NEW_LEGAL_AGE_MONTHS = 65 * 12

/** م/273 المادة 16(3): المعاش المبكر لحد 120 شهر قبل السن النظامية… */
const val SA_NEW_EARLY_MAX_MONTHS = 120

/** …بشرط مدة اشتراك 360 شهر على الأقل (م/273 المادة 16(3)). */
const val SA_NEW_EARLY_MIN_CONTRIB_MONTHS = 360

/** قرار مجلس الوزراء 1022 البند «ثالثًا»(5): مدة الاشتراك المشار إليها في المادة 16(2) = 180 شهر. */
const val SA_NEW_MIN_CONTRIB_MONTHS = 180

/** م/273 المادة 17(1): 2.25% (225 نقطة أساس) × متوسط الأجر × الشهور ÷ 12، وبحد أقصى 100% من المتوسط. */
const val SA_NEW_ACCRUAL_BP = 225

/** م/273 المادة 17(2)(أ): 480 شهر = مدة الحد الأدنى الكامل (وكمان سقف التخفيض في 17(3)). */
const val SA_NEW_FULL_MONTHS = 480

/** م/273 المادة 17(2)(أ): الحد الأدنى 4,000 ر.س عند 480 شهر. */
const val SA_NEW_MIN_PENSION_FULL_MINOR = 400_000L

/** م/273 المادة 17(2)(ب): الحد الأدنى لمن وصل مدة الاستحقاق 2,000 ر.س. */
const val SA_NEW_MIN_PENSION_FLOOR_MINOR = 200_000L

/** م/273 المادة 17(3) و17(4): 3% (300 نقطة أساس) عن كل 12 شهر — وكل شهر بنسبته. */
const val SA_ADJUST_BP_PER_YEAR = 300

/** م/273 المادة 8(2): الحد الأعلى للأجر الخاضع للاشتراك 45,000 ر.س في الشهر. */
const val SA_MAX_CONTRIB_WAGE_MINOR = 4_500_000L

/** م/273 المادة 26(1): المتوسط = متوسط **أعلى** الأجور الخاضعة عن 180 شهر من مدد الاشتراك. */
const val SA_NEW_AVERAGE_MONTHS = 180

/** النظام القديم (صفحة التأمينات): سن الشيخوخة 60 — لمن **مش** داخل في بند «خامسًا» من م/273. */
const val SA_OLD_LEGAL_AGE_MONTHS = 60 * 12

/** النظام القديم (صفحة التأمينات): أقل مدة اشتراك 120 شهر. */
const val SA_OLD_MIN_CONTRIB_MONTHS = 120

/** النظام القديم (صفحة التأمينات): المعاش المبكر بمدة اشتراك فعلية 300 شهر على الأقل (من غير تخفيض). */
const val SA_OLD_EARLY_CONTRIB_MONTHS = 300

/** النظام القديم (صفحة التأمينات): الحد الأدنى للمعاش 1,983.75 ر.س. ⚠️ الصفحة قديمة في السن، والرقم ده ما اتغيرش فيها (ثقة A في الرقم). */
const val SA_OLD_MIN_PENSION_MINOR = 198_375L

/** م/273 بند «خامسًا»: ينطبق على من لم يبلغ 50 هجري يوم السريان — ويُعتبر بلغها من بلغ 48 سنة و6 شهور ميلادي. */
const val SA_DECREE_FIFTH_AGE_LIMIT_MONTHS = 48 * 12 + 6

/** م/273 بند «خامسًا»: ومدد اشتراكه قبل السريان **أقل من 240 شهر**. */
const val SA_DECREE_FIFTH_MONTHS_LIMIT = 240

/** شهور كاملة من [from] لـ[to] بالتقويم (أكبر n بحيث [from] + n شهر ≤ [to] — 31 يناير + شهر = 28 فبراير). [to] قبل [from] ⇒ 0. */
fun fullMonthsBetween(from: IsoDate, to: IsoDate): Int {
    if (to <= from) return 0
    val a = parseIsoDate(from)
    val b = parseIsoDate(to)
    var n = (b.year - a.year) * 12 + (b.month - a.month)
    while (n > 0 && addMonthsClamped(from, n) > to) n--
    while (addMonthsClamped(from, n + 1) <= to) n++
    return n
}

/**
 * سن الاستحقاق في **النظام القديم** بالشهور (م/273 بند «خامسًا»):
 * - [birthDate] عمره يوم السريان (2024-07-03) ≥ 48 سنة و6 شهور، **أو** [monthsAtEffective] ≥ 240 ⇒ البند ما ينطبقش ⇒ **60 سنة** (م/33).
 * - أقل من 29 سنة يوم السريان ⇒ **65 سنة** (خامسًا (1)).
 * - من 29 لأقل من 48 سنة و6 شهور ⇒ الجدول في خامسًا (2): 29 ⇒ 64 و8 شهور، وكل سنة زيادة في العمر ⇒ 4 شهور أقل،
 *   لحد 48 (لأقل من 48 و6 شهور) ⇒ 58 و4 شهور. **= 780 − 4 × (العمر بالسنين − 28)** — متراجعة سطر سطر على الجدول (اختبار).
 */
fun saudiOldLawLegalAgeMonths(birthDate: IsoDate, monthsAtEffective: Int): Int {
    val ageAtEffective = fullMonthsBetween(birthDate, SA_NEW_LAW_EFFECTIVE)
    if (ageAtEffective >= SA_DECREE_FIFTH_AGE_LIMIT_MONTHS || monthsAtEffective >= SA_DECREE_FIFTH_MONTHS_LIMIT) return SA_OLD_LEGAL_AGE_MONTHS
    val years = ageAtEffective / 12
    return if (years < 29) SA_NEW_LEGAL_AGE_MONTHS else SA_NEW_LEGAL_AGE_MONTHS - 4 * (years - 28)
}

/**
 * مدة الاشتراك اللازمة للمعاش **قبل** السن في النظام القديم:
 * - لمن عليه بند «خامسًا»: أقل من 180 شهر يوم السريان ⇒ 360 (خامسًا (3))؛ من 180 لـ239 ⇒ الجدول في خامسًا (4): 180–191 ⇒ 348 ·
 *   192–203 ⇒ 336 · 204–215 ⇒ 324 · 216–227 ⇒ 312 · 228–239 ⇒ 300 (= 348 − 12 × كل 12 شهر فوق 180).
 * - لغيره ⇒ 300 شهر (صفحة التأمينات).
 */
fun saudiOldLawEarlyMonths(birthDate: IsoDate, monthsAtEffective: Int): Int {
    val ageAtEffective = fullMonthsBetween(birthDate, SA_NEW_LAW_EFFECTIVE)
    if (ageAtEffective >= SA_DECREE_FIFTH_AGE_LIMIT_MONTHS || monthsAtEffective >= SA_DECREE_FIFTH_MONTHS_LIMIT) return SA_OLD_EARLY_CONTRIB_MONTHS
    if (monthsAtEffective < SA_NEW_MIN_CONTRIB_MONTHS) return SA_NEW_EARLY_MIN_CONTRIB_MONTHS
    return 348 - 12 * ((monthsAtEffective - SA_NEW_MIN_CONTRIB_MONTHS) / 12)
}

/** متوسط أعلى [count] أجر (م/273 المادة 26(1)) — كل أجر بيتقص الأول عند 45,000 (المادة 8(2)). أقل من [count] شهر ⇒ null (مش معروف). */
fun averageOfHighestWages(monthlyWages: List<Halalas>, count: Int = SA_NEW_AVERAGE_MONTHS): Halalas? {
    if (monthlyWages.size < count || monthlyWages.any { it < 0 }) return null
    val top = monthlyWages.map { minOf(it, SA_MAX_CONTRIB_WAGE_MINOR) }.sortedDescending().take(count)
    return mulDivHalfUp(sumMoney(top), 1, count.toLong())
}

/** المعاش الجديد بالتفصيل — كل خطوة لوحدها عشان الشاشة تشرحها. */
data class SaudiNewPension(
    /** المتوسط بعد سقف 45,000. */
    val averageWageMinor: Halalas,
    val contributionMonths: Int,
    /** 17(1) قبل السقف: المتوسط × 2.25% × الشهور ÷ 12. */
    val accruedMinor: Halalas,
    /** بعد سقف 100% من المتوسط. */
    val baseMinor: Halalas,
    /** 17(3): شهور التخفيض = الأقل من (الشهور قبل السن) و(480 − مدة الاشتراك). */
    val reductionMonths: Int,
    val reductionMinor: Halalas,
    /** 17(4): شهور الاشتراك بعد السن. */
    val increaseMonths: Int,
    val increaseMinor: Halalas,
    /** 17(2): الحد الأدنى لمدته. */
    val minimumMinor: Halalas,
    val pensionMinor: Halalas,
)

/**
 * معاش النظام الجديد لشخص **مستحق** (الشروط بتتفحص في `RetirementCalc.kt`). [monthsBeforeAge] الشهور قبل السن النظامية (مبكر)،
 * [monthsAfterAge] شهور الاشتراك بعدها (متأخر) — واحد بس منهم أكبر من صفر.
 *
 * **الترتيب والتقريب (لأقرب هللة، النص لفوق):** (1) الأجر يتقص عند 45,000 (م8(2)) ⇒ (2) الأساس = المتوسط × 225 × الشهور ÷ 120,000
 * ⇒ (3) سقف 100% من المتوسط (17(1)) ⇒ (4) التخفيض المبكر = الأساس × 300 × شهور التخفيض ÷ 120,000 (17(3))، أو الزيادة المتأخرة
 * بنفس الطريقة (17(4)) ⇒ (5) **الحد الأدنى على الناتج النهائي** (17(2)): 4,000 × الشهور ÷ 480 (بحد أقصى 4,000) وبأرضية 2,000.
 * ⚠️ اختيار Claude في (5): الحد الأدنى بيتطبق **بعد** التخفيض والزيادة (النص بيقول إن معاش 17(1) «يخضع» للحد الأدنى، وبيقول برضه إن
 * التخفيض على معاش 17(1) — الترتيب مش صريح). مكتوب كسؤال للمالك.
 * ⚠️ 17(2)(ب) بيقول «يُخفّض عن كل 12 شهر أقل من 480 — وكل شهر بنسبته» من غير ما يقول بكام: فهمناه **بالتناسب** (4,000 × الشهور ÷ 480)
 * — يعني 100 ر.س عن كل سنة ناقصة (ثقة B).
 */
fun saudiNewPension(averageWageMinor: Halalas, contributionMonths: Int, monthsBeforeAge: Int, monthsAfterAge: Int): SaudiNewPension {
    require(averageWageMinor >= 0 && contributionMonths >= 0 && monthsBeforeAge >= 0 && monthsAfterAge >= 0)
    val wage = minOf(averageWageMinor, SA_MAX_CONTRIB_WAGE_MINOR)
    val accrued = mulDivHalfUp(wage, SA_NEW_ACCRUAL_BP.toLong() * contributionMonths, 12 * BASIS_POINTS)
    val base = minOf(accrued, wage)
    val reductionMonths = minOf(monthsBeforeAge, maxOf(0, SA_NEW_FULL_MONTHS - contributionMonths))
    val reduction = mulDivHalfUp(base, SA_ADJUST_BP_PER_YEAR.toLong() * reductionMonths, 12 * BASIS_POINTS)
    val increase = mulDivHalfUp(base, SA_ADJUST_BP_PER_YEAR.toLong() * monthsAfterAge, 12 * BASIS_POINTS)
    val minimum = maxOf(SA_NEW_MIN_PENSION_FLOOR_MINOR, mulDivHalfUp(SA_NEW_MIN_PENSION_FULL_MINOR, minOf(contributionMonths, SA_NEW_FULL_MONTHS).toLong(), SA_NEW_FULL_MONTHS.toLong()))
    val adjusted = addMoney(subtractMoney(base, reduction), increase)
    return SaudiNewPension(wage, contributionMonths, accrued, base, reductionMonths, reduction, monthsAfterAge, increase, minimum, maxOf(adjusted, minimum))
}

/** المعاش القديم بالتفصيل. */
data class SaudiOldPension(
    val averageWageMinor: Halalas,
    val monthsBefore1422: Int,
    val monthsAfter1422: Int,
    /** قبل السقف: المتوسط × شهور بعد 1422 ÷ 480 + المتوسط × شهور قبل 1422 ÷ 600. */
    val accruedMinor: Halalas,
    val baseMinor: Halalas,
    val pensionMinor: Halalas,
)

/**
 * معاش النظام القديم (صفحة التأمينات): **متوسط أجر آخر سنتين** × الشهور ÷ 480، والشهور قبل 1/1/1422هـ ÷ 600 (اختياري).
 * حسبة واحدة بتقريب واحد: ÷480 = 5/2400 و÷600 = 4/2400. السقف 100% من المتوسط (المرسوم م/273 بند «ثامنًا» بيشير لـ«100% من
 * الأجر الذي يحسب على أساسه المعاش» حسب المادة 38 من نظام م/33). الحد الأدنى 1,983.75.
 * ⚠️ مش متحسب: إضافات المُعالين (10% · 15% · 20% — نفس الصفحة) ولا سقف الأجر في النظام القديم (ما اتراجعش على نص رسمي).
 */
fun saudiOldPension(averageWageMinor: Halalas, monthsAfter1422: Int, monthsBefore1422: Int): SaudiOldPension {
    require(averageWageMinor >= 0 && monthsAfter1422 >= 0 && monthsBefore1422 >= 0)
    val accrued = mulDivHalfUp(averageWageMinor, 5L * monthsAfter1422 + 4L * monthsBefore1422, 2400)
    val base = minOf(accrued, averageWageMinor)
    return SaudiOldPension(averageWageMinor, monthsBefore1422, monthsAfter1422, accrued, base, maxOf(base, SA_OLD_MIN_PENSION_MINOR))
}
