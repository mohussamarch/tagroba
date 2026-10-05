package app.masroufy.core

/**
 * مكافأة نهاية الخدمة (حاسبة التقاعد — قرار المالك §69: «مكافأة نهاية الخدمة تتحسب»). أعداد صحيحة بس.
 *
 * **السعودية — نظام العمل** (المرسوم م/51 وتاريخ 23/8/1426هـ وتعديلاته لحد م/44 وتاريخ 8/2/1446هـ)، النسخة الرسمية:
 * https://www.hrsd.gov.sa/sites/default/files/2025-05/hrsd.pdf (اتقرت المواد من الملف نفسه):
 * - **م84:** أجر **نص شهر** عن كل سنة من **الخمس سنين الأولى**، و**شهر** عن كل سنة بعدها، على أساس **الأجر الأخير**،
 *   و«يستحق العامل مكافأة عن أجزاء السنة بنسبة ما قضاه منها في العمل».
 * - **م2:** «الأجر: الأجر الفعلي» = الأساسي + سائر الزيادات المستحقة ⇒ المدخل = آخر أجر **كامل** (مش الأساسي بس).
 * - **م85 (الاستقالة):** أقل من سنتين ⇒ صفر · من سنتين لحد 5 سنين (شامل) ⇒ **الثلث** · أكتر من 5 وأقل من 10 ⇒ **الثلثين** · 10 أو أكتر ⇒ كاملة.
 * - **م87:** كاملة لو ساب الشغل لـ**قوة قاهرة**، أو العاملة لو أنهت العقد خلال **6 شهور من عقد زواجها** أو **3 شهور من الولادة** ⇒ فلاج [art87].
 * - **م80:** حالات الفسخ من غير مكافأة (اعتداء · إخلال بالالتزامات · تزوير · غياب …) ⇒ سبب [EosEnd.DISMISSED_ART80] ⇒ صفر.
 *
 * **مصر:** مفيش مكافأة نهاية خدمة لمن له حق في التأمينات (قانون العمل 14/2025 م172 — ثقة C، ما اتراجعتش على النص الرسمي) ⇒ «لا ينطبق».
 */
enum class EosEnd {
    /** صاحب العمل أنهى العقد، أو العقد خلص، أو السن النظامية — المكافأة كاملة (م84). */
    EMPLOYER_OR_CONTRACT_END,
    /** استقالة (م85) — إلا لو [art87]. */
    RESIGNATION,
    /** فسخ بإحدى حالات م80 ⇒ مفيش مكافأة. */
    DISMISSED_ART80,
}

enum class EosShare { NONE, THIRD, TWO_THIRDS, FULL }

/** المدة بالسنين الكاملة + أيام من السنة اللي ما كملتش ÷ طولها الحقيقي (365 أو 366) — «بنسبة ما قضاه منها». */
data class ServiceLength(val fullYears: Int, val extraDays: Int, val partialYearDays: Int)

data class EndOfServiceResult(
    val lastWageMinor: Halalas,
    val service: ServiceLength,
    /** المكافأة الكاملة حسب م84 (قبل نسبة الاستقالة). */
    val fullAwardMinor: Halalas,
    val share: EosShare,
    /** المستحق فعلًا بعد م85 / م87 / م80. */
    val payableMinor: Halalas,
)

class EndOfServiceError(message: String) : IllegalArgumentException(message)

/** المدة من [start] لـ[end]: السنين الكاملة بالتقويم (ذكرى البداية — 29 فبراير ⇒ 28 فبراير)، والباقي أيام من طول السنة الجاية فعلًا. */
fun serviceLength(start: IsoDate, end: IsoDate): ServiceLength {
    require(end >= start)
    val months = fullMonthsBetween(start, end)
    val years = months / 12
    val anniversary = addMonthsClamped(start, years * 12)
    val next = addMonthsClamped(start, (years + 1) * 12)
    return ServiceLength(years, daysBetween(anniversary, end), daysBetween(anniversary, next))
}

/**
 * المكافأة في السعودية. **حسبة واحدة وتقريب واحد** (لأقرب هللة، النص لفوق): بنحوّل المدة لـ«أنصاص أجر شهري» على مقام 2 × أيام السنة الناقصة:
 * - أقل من 5 سنين: N = السنين × L + الأيام ⇒ المكافأة = الأجر × N ÷ (2L)  (نص شهر لكل سنة).
 * - 5 أو أكتر: N = 5L + 2L × (السنين − 5) + 2 × الأيام ⇒ نفس القسمة (شهر كامل لكل سنة بعد الخامسة، وجزء السنة بنسبته).
 * والثلث والثلثين بيتضربوا في نفس الكسر قبل التقريب (من غير تقريب مرتين).
 */
fun saudiEndOfService(lastWageMinor: Halalas, start: IsoDate, end: IsoDate, reason: EosEnd, art87: Boolean = false): EndOfServiceResult {
    if (lastWageMinor <= 0 || lastWageMinor > MAX_SAFE_HALALAS) throw EndOfServiceError(uiText(TextKey.CALC_WAGE_POSITIVE))
    if (!isValidIsoDate(start) || !isValidIsoDate(end) || end < start) throw EndOfServiceError(uiText(TextKey.CALC_SERVICE_DATES))
    val s = serviceLength(start, end)
    val l = s.partialYearDays.toLong()
    val n = if (s.fullYears < 5) s.fullYears * l + s.extraDays else 5 * l + 2 * l * (s.fullYears - 5) + 2L * s.extraDays
    val share = when {
        reason == EosEnd.DISMISSED_ART80 -> EosShare.NONE
        reason == EosEnd.EMPLOYER_OR_CONTRACT_END || art87 -> EosShare.FULL
        s.fullYears < 2 -> EosShare.NONE
        s.fullYears < 5 || (s.fullYears == 5 && s.extraDays == 0) -> EosShare.THIRD
        s.fullYears < 10 -> EosShare.TWO_THIRDS
        else -> EosShare.FULL
    }
    val thirds = when (share) { EosShare.NONE -> 0L; EosShare.THIRD -> 1L; EosShare.TWO_THIRDS -> 2L; EosShare.FULL -> 3L }
    val full = mulDivBig(lastWageMinor, n, 2 * l)
    val payable = if (thirds == 3L) full else mulDivBig(lastWageMinor, n * thirds, 2 * l * 3)
    return EndOfServiceResult(lastWageMinor, s, full, share, payable)
}

/** [a] × [num] ÷ [den] بتقريب واحد — [num] ممكن يعدّي حد `mulDivHalfUp` (مدة طويلة × 2L) ⇒ بنقسمه على [den] الأول. */
private fun mulDivBig(a: Halalas, num: Long, den: Long): Halalas {
    val whole = num / den
    val rest = num % den
    return addMoney(multiplyMoneyByInt(a, whole), mulDivHalfUp(a, rest, den))
}
