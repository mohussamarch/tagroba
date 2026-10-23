package app.masroufy.core

/**
 * **حدود اليوم بتوقيت كل بلد** (OVERRIDES §88 — رد المالك 2026-10-11): بنوك ومساحات مصر بتوقيت القاهرة (بالتوقيت الصيفي)، والسعودية بتوقيت
 * الرياض — مش +3 مثبّتة. المنطقة من حزمة البلد (`CountryPack.timeZone`). بتستعملها قراية التاريخ (§83: «أقدم رسالة يوم كذا» · «اقرأ من يوم
 * كذا») والرسالة اللي مفيهاش تاريخ (§77-C · §82-7) و«النهاردة؟» في الرسالة الملصوقة (§82-14).
 * من غير مكتبة: البلدين قواعدهم بسيطة ومتجربة يوم بيوم وساعة بساعة قصاد `java.time` (`CountryTimeTest`).
 */
const val SAUDI_TIME_ZONE = "Asia/Riyadh"
const val EGYPT_TIME_ZONE = "Africa/Cairo"

/** المناطق اللي الحساب ده بيعرفها — حزمة بلد بمنطقة تانية لازم تتضاف هنا الأول (`CountryTimeTest` بيفشل). */
val KNOWN_TIME_ZONES: Set<String> = setOf(SAUDI_TIME_ZONE, EGYPT_TIME_ZONE)

private const val HOUR_MS = 3_600_000L

/** آخر [weekday] في الشهر (الأحد = 0). رقم اليوم 0 = 1970-01-01 = خميس. */
private fun lastWeekday(year: Int, month: Int, lastDay: Int, weekday: Int): Int {
    val last = toDayNumber(DateParts(year, month, lastDay))
    return last - (last + 4 - weekday).mod(7)
}

/**
 * القاهرة: +2، والتوقيت الصيفي (+3، من 2023) من أول آخر جمعة في أبريل لحد آخر خميس في أكتوبر الساعة 24:00 (= 23:00 العادية).
 * قبل 2023 من غير صيفي (مصر لغته 2015–2022؛ اللي قبل 2015 مش محسوب — مفيش رسايل بالقِدم ده).
 */
private fun cairoLocal(utcMillis: Long): Long {
    val standard = utcMillis + 2 * HOUR_MS
    val year = parseIsoDate(dayNumberToIso(standard.floorDiv(DAY_MS).toInt())).year
    val summer = year >= 2023 &&
        standard >= lastWeekday(year, 4, 30, 5) * DAY_MS &&
        standard < (lastWeekday(year, 10, 31, 4) + 1) * DAY_MS - HOUR_MS
    return if (summer) standard + HOUR_MS else standard
}

/** الوقت المحلي في [timeZone] (ملي ثانية من 1970 بالساعة المحلية) للحظة [utcMillis] بتوقيت جرينتش. */
fun localMillisIn(timeZone: String, utcMillis: Long): Long = when (timeZone) {
    SAUDI_TIME_ZONE -> utcMillis + 3 * HOUR_MS
    EGYPT_TIME_ZONE -> cairoLocal(utcMillis)
    else -> throw IllegalArgumentException("unknown time zone $timeZone — add it to KNOWN_TIME_ZONES")
}

/** يوم اللحظة [utcMillis] في [timeZone]. */
fun dayAt(timeZone: String, utcMillis: Long): IsoDate = dayNumberToIso(localMillisIn(timeZone, utcMillis).floorDiv(DAY_MS).toInt())

/**
 * cty-8 — **«النهارده» الوحيد** للبلد: يوم اللحظة [nowIso] بتوقيت حزمة البلد [countryCode] (القاهرة أو الرياض) — مش تاريخ ساعة الجوال
 * ولا تاريخ جرينتش. الرئيسية والشهر والاستثمار (`DeviceEnv.today` للبلد) وحساب الأصول بيقروا منه.
 */
fun todayInCountry(countryCode: String?, nowIso: String): IsoDate = dayIn(countryPack(countryCode).timeZone, nowIso) ?: nowIso.take(10)

/** يوم الوقت [iso] (بأي منطقة مكتوبة فيه) في [timeZone]. تاريخ بس («2026-10-09») ⇒ زي ما هو · مش مقروء ⇒ null. */
fun dayIn(timeZone: String, iso: String): IsoDate? {
    if (iso.length == 10) return iso.takeIf(::isValidIsoDate)
    return dayAt(timeZone, JsText.parseIsoMillis(iso) ?: return null)
}

/**
 * أول لحظة في اليوم [day] بتوقيت [timeZone] (ملي جرينتش). يوم بداية الصيفي في مصر مفيهوش 00:00 (الساعة بتنط لـ01:00) ⇒ أول لحظة فيه.
 */
fun dayStartMillisIn(timeZone: String, day: IsoDate): Long {
    val midnight = toDayNumber(parseIsoDate(day)) * DAY_MS
    // الفرق عن جرينتش بين +2 و+3 في البلدين: الأبكر الأول، وأول واحدة يومها هو [day] هي بداية اليوم
    return (3 downTo 2).map { midnight - it * HOUR_MS }.firstOrNull { dayAt(timeZone, it) == day } ?: (midnight - 2 * HOUR_MS)
}

/** نفس [dayStartMillisIn] كوقت ISO بتوقيت جرينتش («2026-10-09T21:00:00Z»). */
fun dayStartIn(timeZone: String, day: IsoDate): String = utcIsoOf(dayStartMillisIn(timeZone, day))

/** «2026-10-09T21:00:00Z» من ملي جرينتش (بالثانية). */
fun utcIsoOf(utcMillis: Long): String {
    val day = utcMillis.floorDiv(DAY_MS)
    val seconds = (utcMillis - day * DAY_MS) / 1000
    fun two(n: Long) = n.toString().padStart(2, '0')
    return dayNumberToIso(day.toInt()) + "T" + two(seconds / 3600) + ":" + two(seconds / 60 % 60) + ":" + two(seconds % 60) + "Z"
}
