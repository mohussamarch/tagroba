package app.masroufy.core

/**
 * «حاسبة الادخار» (قرار المالك 2026-10-05، OVERRIDES §69) — دوال نقية بأعداد صحيحة (الهللة)، من غير أي كسر عائم:
 * - **الاتجاه الأول:** عايز أوصل لمبلغ بتاريخ ⇒ محتاج كام في الشهر ([savingsPerMonth]) — **لفوق** بهللة، بنفس حسبة «المطلوب في الشهر»
 *   في خطة الادخار (§68: الشهور **بالتقويم**، `monthsUntil`) عشان «حوّلها لخطة» تطلع نفس الرقم بالظبط.
 * - **الاتجاه التاني:** بحوّش كام في الشهر لمدة كام شهر ⇒ هوصل لكام ([savingsReach]) — **من غير أرباح** (جمع بس).
 * - **المقارنة باللي بتحوّشه فعلًا** في `ActualSaving.kt`.
 * - **مكان النمو (seam) للمهمة الجاية** (الذهب · العقار · …): [projectMonthly] متبنية ومتختبرة بمعدل صفر ومعدل مش صفر — بس **مفيش
 *   ولا معدل افتراضي متوصّل** (القاعدة 10: المعدل لازم يبقى له مصدر مكتوب جنبه، أو المستخدم يكتبه).
 */

/** أطول مدة في الحاسبة: 100 سنة بالشهور — أي حاجة أطول غالبًا غلطة كتابة. */
const val MAX_CALC_MONTHS = 1200

/** أعلى معدل سنوي مقبول في [projectMonthly]: 1000% (100,000 نقطة أساس) — حد أمان للحساب، مش رقم متوقع. */
const val MAX_ANNUAL_RATE_BP = 100_000

/** أقل معدل سنوي: −99.99% (المعدل السالب مسموح عشان التضخم مثلًا في المهمة الجاية). */
const val MIN_ANNUAL_RATE_BP = -9_999

/** نقاط الأساس في الواحد الصحيح (10000 = 100%). */
const val BASIS_POINTS = 10_000L

class SavingsCalcError(message: String) : IllegalArgumentException(message)

/** نتيجة «عايز أوصل لمبلغ»: الباقي ÷ الشهور (بالتقويم) لفوق. */
data class SavingsPlanResult(
    val targetMinor: Halalas,
    val alreadySavedMinor: Halalas,
    val remainingMinor: Halalas,
    val fromDate: IsoDate,
    val targetDate: IsoDate,
    val months: Int,
    val perMonthMinor: Halalas,
)

/**
 * محتاج كام في الشهر عشان توصل [targetMinor] يوم [targetDate] بداية من [fromDate] (النهارده)، ومعاك [alreadySavedMinor] خلاص.
 * الشهور = `monthsUntil` (نفس خطة الادخار — 31 يناير + شهر = 28 فبراير). القسمة **لفوق** (`ceilDivMoney`) عشان ما توصلش ناقص هللة.
 * اللي معاك ≥ الهدف ⇒ صفر في الشهر.
 */
fun savingsPerMonth(targetMinor: Halalas, alreadySavedMinor: Halalas, fromDate: IsoDate, targetDate: IsoDate): SavingsPlanResult {
    if (targetMinor <= 0 || targetMinor > MAX_SAFE_HALALAS) throw SavingsCalcError(uiText(TextKey.CALC_TARGET_POSITIVE))
    if (alreadySavedMinor < 0 || alreadySavedMinor > MAX_SAFE_HALALAS) throw SavingsCalcError(uiText(TextKey.CALC_SAVED_NOT_NEGATIVE))
    if (!isValidIsoDate(fromDate) || !isValidIsoDate(targetDate) || targetDate <= fromDate) throw SavingsCalcError(uiText(TextKey.CALC_DATE_AFTER_TODAY))
    val months = monthsUntil(fromDate, targetDate)
    if (months > MAX_CALC_MONTHS) throw SavingsCalcError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    val remaining = maxOf(0L, subtractMoney(targetMinor, alreadySavedMinor))
    return SavingsPlanResult(targetMinor, alreadySavedMinor, remaining, fromDate, targetDate, months, ceilDivMoney(remaining, months))
}

/** نتيجة «بحوّش كام في الشهر»: اللي معاك + الشهري × الشهور (من غير أرباح — اختيار المالك). */
data class SavingsReachResult(
    val monthlyMinor: Halalas,
    val months: Int,
    val alreadySavedMinor: Halalas,
    val reachedMinor: Halalas,
    /** يوم ما المدة تخلص (من النهارده + الشهور بالتقويم). */
    val endDate: IsoDate,
)

fun savingsReach(monthlyMinor: Halalas, months: Int, alreadySavedMinor: Halalas, fromDate: IsoDate): SavingsReachResult {
    if (monthlyMinor <= 0 || monthlyMinor > MAX_SAFE_HALALAS) throw SavingsCalcError(uiText(TextKey.CALC_MONTHLY_POSITIVE))
    if (months < 1 || months > MAX_CALC_MONTHS) throw SavingsCalcError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    if (alreadySavedMinor < 0 || alreadySavedMinor > MAX_SAFE_HALALAS) throw SavingsCalcError(uiText(TextKey.CALC_SAVED_NOT_NEGATIVE))
    if (!isValidIsoDate(fromDate)) throw SavingsCalcError(uiText(TextKey.CALC_DATE_AFTER_TODAY))
    val reached = addMoney(alreadySavedMinor, multiplyMoneyByInt(monthlyMinor, months.toLong()))
    return SavingsReachResult(monthlyMinor, months, alreadySavedMinor, reached, addMonthsClamped(fromDate, months))
}

/**
 * [a] × [num] ÷ [den] **بالظبط** بأعداد صحيحة، والتقريب مرة واحدة **لأقرب هللة (النص لبعيد عن الصفر)** — زي `rateOfMoney` بس من غير
 * ما الضرب يعدّي حد `Long`: [a] = q×den + r ⇒ الناتج = q×num + (r×num ÷ den). [den] موجب و|num| ≤ 10^6.
 * النتيجة برا الحد الآمن ⇒ `MoneyError` (مش رقم غلط بصمت).
 */
internal fun mulDivHalfUp(a: Halalas, num: Long, den: Long): Halalas {
    assertHalalas(a)
    require(den > 0 && num in -1_000_000L..1_000_000L)
    val sign = if ((a < 0) != (num < 0) && a != 0L && num != 0L) -1L else 1L
    val absA = if (a < 0) -a else a
    val absN = if (num < 0) -num else num
    val q = absA / den
    val r = absA % den
    val whole = multiplyMoneyByInt(q, absN)
    val part = r * absN // r < den ≤ 10^6 و absN ≤ 10^6 ⇒ أقل من 10^12
    val partQ = part / den
    val rounded = if ((part % den) * 2 >= den) partQ + 1 else partQ
    return sign * addMoney(whole, rounded)
}

/**
 * **مكان النمو (seam)** — المهمة الجاية (توقّع الذهب والعقار والأصول §69). مبلغ شهري [monthlyMinor] لمدة [months] شهر بمعدل سنوي
 * [annualRateBp] (نقاط أساس: 1200 = 12% في السنة) + مبلغ بداية اختياري [startMinor].
 *
 * **قاعدة الحساب والتقريب (مكتوبة عشان أي حد يراجعها على الورق):**
 * 1. المعدل الشهري = المعدل السنوي ÷ 12 (اسمي nominal، مركّب شهريًا) ⇒ فايدة الشهر = الرصيد × bp ÷ 120,000.
 * 2. كل شهر بالترتيب: **الفايدة على رصيد أول الشهر الأول** (تتقرب لأقرب هللة، النص لبعيد عن الصفر — زي البنك اللي بيضيف الفايدة
 *    بالهللة كل شهر)، **وبعدين الإيداع آخر الشهر** (ordinary annuity: أول إيداع ما بياخدش فايدة في شهره).
 * 3. كله `Long` بالهللة (`mulDivHalfUp`)، وأي ناتج برا الحد الآمن ⇒ `MoneyError`.
 * بمعدل صفر النتيجة = [startMinor] + الشهري × الشهور بالظبط (نفس [savingsReach]).
 * **مفيش معدل افتراضي هنا ولا في أي حالة استخدام** — اللي بينادي لازم يجيب معدل ليه مصدر، أو المستخدم يكتبه.
 */
fun projectMonthly(monthlyMinor: Halalas, months: Int, annualRateBp: Int, startMinor: Halalas = 0L): Halalas {
    if (monthlyMinor < 0 || startMinor < 0) throw SavingsCalcError(uiText(TextKey.CALC_MONTHLY_POSITIVE))
    if (months < 0 || months > MAX_CALC_MONTHS) throw SavingsCalcError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    if (annualRateBp < MIN_ANNUAL_RATE_BP || annualRateBp > MAX_ANNUAL_RATE_BP) throw SavingsCalcError(uiText(TextKey.CALC_RATE_RANGE))
    var balance = startMinor
    repeat(months) {
        balance = addMoney(balance, mulDivHalfUp(balance, annualRateBp.toLong(), 12 * BASIS_POINTS), monthlyMinor)
    }
    return balance
}
