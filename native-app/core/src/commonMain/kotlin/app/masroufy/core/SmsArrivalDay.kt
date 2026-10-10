package app.masroufy.core

/**
 * **§77-C — الرسالة اللي مفيهاش تاريخ** (قرار المالك 2026-10-09): «ياخد يوم وصول الرسالة بتوقيت البلد — لكل الأشكال اللي مفيهاش تاريخ».
 * التوقيت: منطقة البلد ([localMillisIn] — `CountryTime.kt`، §88): السعودية +3 · مصر بتوقيت القاهرة (+2، والصيفي +3 من 2023).
 *
 * **الساعة المكتوبة** (اختيار Claude — من الرسالة نفسها مش تخمين): رسالة فيها ساعة بس من غير تاريخ والساعة المكتوبة **بعد** ساعة الوصول
 * بأكتر من [CLOCK_SKEW_MINUTES] دقيقة ⇒ يا إما الرسالة عدّت نص الليل وهي جاية، يا إما الساعتين مش متفقين:
 * - **عدّت نص الليل** = اتكتبت بالليل ووصلت الصبح بدري: التأخير اللي ده معناه (الوصول + 24 ساعة − المكتوب) **[MAX_OVERNIGHT_DELAY_MINUTES]
 *   بالكتير** ⇒ **اليوم اللي قبل** يوم الوصول («23:58» ووصلت 00:03 ⇒ امبارح · «23:30» والجوال كان مقفول لحد 06:00 ⇒ امبارح).
 * - **غير كده الساعتين مش متفقين** (ساعة البنك أو الجوال قدام · فرق ساعة في التوقيت الصيفي) ⇒ **يوم الوصول** (قاعدة المالك) **والرسالة
 *   بتستنى تأكيد** ([datelessClockConflict]) — مراجعة S1: «14:45» ووصلت 14:25 كانت بتتسجل لوحدها امبارح (يعني تأخير 23 ساعة و40 دقيقة).
 * الهامش عشان ساعة البنك وساعة الجوال مش متطابقين بالثانية (البنك كاتب 14:23 ووصلت 14:22 ⇒ النهارده، مش امبارح).
 * قبل كده (الجولتين السابعة والتامنة) الرسالة دي كانت **بتستنى** تأكيد المالك عشان ممكن تتسجل يوم متأخر.
 */
private const val MINUTE_MS = 60_000L
private const val DAY_MINUTES = 24 * 60

/** هامش فرق الساعة بين البنك والجوال (دقايق) — اختيار Claude. */
internal const val CLOCK_SKEW_MINUTES = 15

/** أطول تأخير معقول لرسالة اتكتبت بالليل ووصلت الصبح (الجوال مقفول طول الليل) — 8 ساعات، اختيار Claude (مراجعة S1). */
internal const val MAX_OVERNIGHT_DELAY_MINUTES = 8 * 60

/**
 * يوم الوصول بتوقيت القاهرة («أول 10 حروف» من وقت جرينتش كانت بتسجل رسالة وصلت بعد نص الليل على اليوم اللي قبله — مراجعة جلسة 33).
 * `receivedAt` من أندرويد = `Instant.toString()` بتوقيت جرينتش.
 */
internal fun cairoDayOf(receivedAt: String): IsoDate? = dayIn(EGYPT_TIME_ZONE, receivedAt)

/** وقت الوصول بالساعة المحلية في [timeZone] (منطقة البلد). */
private fun localArrivalMillis(receivedAt: String, timeZone: String): Long? =
    JsText.parseIsoMillis(receivedAt)?.let { localMillisIn(timeZone, it) }

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

/** الساعة المكتوبة (أول ساعة) قصاد ساعة الوصول: نفس اليوم · عدّت نص الليل · مش متفقين. */
private enum class WrittenClock { SAME_DAY, CROSSED_MIDNIGHT, CONFLICT }

/** يوم الوصول (رقم اليوم بتوقيت البلد) والساعة المكتوبة قصاده، أو null لو وقت الوصول مش مقروء. */
private fun arrivalAndClock(body: String, receivedAt: String, timeZone: String): Pair<Long, WrittenClock>? {
    val local = localArrivalMillis(receivedAt, timeZone) ?: return null
    val day = local.floorDiv(DAY_MS)
    val arrivalMinute = ((local - day * DAY_MS) / MINUTE_MS).toInt()
    val written = writtenClockTimes(body).firstOrNull() ?: return day to WrittenClock.SAME_DAY
    val verdict = when {
        written <= arrivalMinute + CLOCK_SKEW_MINUTES -> WrittenClock.SAME_DAY
        // التأخير لو الرسالة اتكتبت امبارح الساعة دي: من المكتوب لنص الليل + من نص الليل للوصول
        DAY_MINUTES - written + arrivalMinute <= MAX_OVERNIGHT_DELAY_MINUTES -> WrittenClock.CROSSED_MIDNIGHT
        else -> WrittenClock.CONFLICT
    }
    return day to verdict
}

/**
 * يوم رسالة **مفيهاش تاريخ** (§77-C): يوم الوصول بتوقيت البلد، أو اللي قبله لو الساعة المكتوبة بتقول إنها عدّت نص الليل وهي جاية (اتكتبت
 * بالليل ووصلت الصبح — [MAX_OVERNIGHT_DELAY_MINUTES]). الساعتين مش متفقين ⇒ يوم الوصول (والرسالة بتستنى — [datelessClockConflict]).
 * وقت الوصول تاريخ بس («2026-10-09») ⇒ اليوم ده زي ما هو (مفيش ساعة نقارن بيها).
 */
internal fun datelessDay(body: String, receivedAt: String, timeZone: String): IsoDate? {
    if (receivedAt.length == 10) return receivedAt.takeIf(::isValidIsoDate)
    val (day, written) = arrivalAndClock(body, receivedAt, timeZone) ?: return null
    return dayNumberToIso((if (written == WrittenClock.CROSSED_MIDNIGHT) day - 1 else day).toInt())
}

/**
 * رسالة من غير تاريخ **ساعتها المكتوبة قدام ساعة الوصول** ومش بفرق ليلة معقول (مراجعة S1): ساعة البنك أو الجوال غلط، أو فرق ساعة في التوقيت
 * الصيفي ⇒ مش واضح اليوم ⇒ الشكل الواضح بيستنى تأكيد المالك (اليوم = يوم الوصول). وقت الوصول تاريخ بس ⇒ مفيش حاجة نقارنها ⇒ false.
 */
internal fun datelessClockConflict(body: String, receivedAt: String, timeZone: String): Boolean =
    receivedAt.length != 10 && arrivalAndClock(body, receivedAt, timeZone)?.second == WrittenClock.CONFLICT

/** رسالة من غير تاريخ فيها **أكتر من ساعة مختلفة** ⇒ مش واضح أنهي ساعة العملية ⇒ الشكل الواضح بيستنى (القراية بأول ساعة). */
internal fun ambiguousClock(body: String): Boolean = writtenClockTimes(body).size > 1
