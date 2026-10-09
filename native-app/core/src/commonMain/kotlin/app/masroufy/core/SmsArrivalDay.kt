package app.masroufy.core

/**
 * **§77-C — الرسالة اللي مفيهاش تاريخ** (قرار المالك 2026-10-09): «ياخد يوم وصول الرسالة بتوقيت البلد — لكل الأشكال اللي مفيهاش تاريخ».
 * التوقيت: السعودية +3 · مصر بتوقيتها (+2، والصيفي +3 من 2023).
 *
 * **الساعة المكتوبة** (اختيار Claude — من الرسالة نفسها مش تخمين): رسالة فيها ساعة بس من غير تاريخ والساعة المكتوبة **بعد** ساعة الوصول
 * بأكتر من [CLOCK_SKEW_MINUTES] دقيقة ⇒ الرسالة عدّت نص الليل وهي جاية ⇒ **اليوم اللي قبل** يوم الوصول («23:58» ووصلت 00:03 ⇒ امبارح).
 * الهامش عشان ساعة البنك وساعة الجوال مش متطابقين بالثانية (البنك كاتب 14:23 ووصلت 14:22 ⇒ النهارده، مش امبارح).
 * قبل كده (الجولتين السابعة والتامنة) الرسالة دي كانت **بتستنى** تأكيد المالك عشان ممكن تتسجل يوم متأخر.
 */
internal enum class SmsClock { RIYADH, CAIRO }

private const val HOUR_MS = 3_600_000L
private const val MINUTE_MS = 60_000L

/** هامش فرق الساعة بين البنك والجوال (دقايق) — اختيار Claude. */
internal const val CLOCK_SKEW_MINUTES = 15

/** آخر [weekday] في الشهر (الأحد = 0). رقم اليوم 0 = 1970-01-01 = خميس. */
private fun lastWeekday(year: Int, month: Int, lastDay: Int, weekday: Int): Int {
    val last = toDayNumber(DateParts(year, month, lastDay))
    return last - (last + 4 - weekday).mod(7)
}

/**
 * وقت الوصول بتوقيت القاهرة (ملي ثانية من 1970 بالساعة المحلية). `receivedAt` من أندرويد = `Instant.toString()` بتوقيت جرينتش.
 * مصر +2، والتوقيت الصيفي (+3، من 2023) من أول آخر جمعة في أبريل لحد آخر خميس في أكتوبر.
 */
private fun cairoLocalMillis(receivedAt: String): Long? {
    val standard = (JsText.parseIsoMillis(receivedAt) ?: return null) + 2 * HOUR_MS
    val year = parseIsoDate(dayNumberToIso(standard.floorDiv(DAY_MS).toInt())).year
    val summer = year >= 2023 &&
        standard >= lastWeekday(year, 4, 30, 5) * DAY_MS &&
        standard < (lastWeekday(year, 10, 31, 4) + 1) * DAY_MS - HOUR_MS
    return if (summer) standard + HOUR_MS else standard
}

/**
 * يوم الوصول بتوقيت القاهرة («أول 10 حروف» من وقت جرينتش كانت بتسجل رسالة وصلت بعد نص الليل على اليوم اللي قبله — مراجعة جلسة 33).
 */
internal fun cairoDayOf(receivedAt: String): IsoDate? {
    if (receivedAt.length == 10) return receivedAt.takeIf(::isValidIsoDate)
    return dayNumberToIso((cairoLocalMillis(receivedAt) ?: return null).floorDiv(DAY_MS).toInt())
}

private fun localArrivalMillis(receivedAt: String, clock: SmsClock): Long? = when (clock) {
    SmsClock.RIYADH -> JsText.parseIsoMillis(receivedAt)?.plus(3 * HOUR_MS)
    SmsClock.CAIRO -> cairoLocalMillis(receivedAt)
}

/** «14:22» · «2:22:05 PM» · «11:58 م» — الساعة والدقيقة (والثواني بتتساب)، وص/م أو AM/PM لو مكتوبين. */
private val WRITTEN_CLOCK = Regex(
    "(?<![\\d:])(\\d{1,2}):(\\d{2})(?::\\d{2})?(?![\\d])(?:[ \\t]?(am|pm|ص|م)(?![A-Za-z\\u0600-\\u06FF]))?",
    RegexOption.IGNORE_CASE,
)

/** الساعات المكتوبة في الرسالة بالدقايق من نص الليل، **مختلفة** وبترتيب الكتابة. ساعة مش صالحة («25:70») بتتساب. */
internal fun writtenClockTimes(body: String): List<Int> = WRITTEN_CLOCK.findAll(latinizeDigits(body)).mapNotNull { m ->
    val hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].toInt()
    val half = m.groupValues[3].lowercase()
    if (minute > 59) return@mapNotNull null
    when (half) {
        "" -> hour.takeIf { it <= 23 }?.let { it * 60 + minute }
        "am", "ص" -> hour.takeIf { it in 1..12 }?.let { (it % 12) * 60 + minute }
        else -> hour.takeIf { it in 1..12 }?.let { (it % 12 + 12) * 60 + minute }
    }
}.distinct().toList()

/**
 * يوم رسالة **مفيهاش تاريخ** (§77-C): يوم الوصول بتوقيت البلد، أو اللي قبله لو الساعة المكتوبة (أول ساعة) بعد ساعة الوصول بأكتر من
 * [CLOCK_SKEW_MINUTES]. وقت الوصول تاريخ بس («2026-10-09») ⇒ اليوم ده زي ما هو (مفيش ساعة نقارن بيها).
 */
internal fun datelessDay(body: String, receivedAt: String, clock: SmsClock): IsoDate? {
    if (receivedAt.length == 10) return receivedAt.takeIf(::isValidIsoDate)
    val local = localArrivalMillis(receivedAt, clock) ?: return null
    val day = local.floorDiv(DAY_MS)
    val arrivalMinute = ((local - day * DAY_MS) / MINUTE_MS).toInt()
    val written = writtenClockTimes(body).firstOrNull()
    val crossedMidnight = written != null && written > arrivalMinute + CLOCK_SKEW_MINUTES
    return dayNumberToIso((if (crossedMidnight) day - 1 else day).toInt())
}

/** رسالة من غير تاريخ فيها **أكتر من ساعة مختلفة** ⇒ مش واضح أنهي ساعة العملية ⇒ الشكل الواضح بيستنى (القراية بأول ساعة). */
internal fun ambiguousClock(body: String): Boolean = writtenClockTimes(body).size > 1
