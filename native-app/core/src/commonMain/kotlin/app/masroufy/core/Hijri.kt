package app.masroufy.core

/**
 * التقويم الهجري — **أم القرى** (التقويم الرسمي في السعودية). الحول سنة هجرية (OVERRIDES §62).
 * خاص بكل جهاز: `java.time.chrono.HijrahDate` على الكمبيوتر وأندرويد (أم القرى هو الافتراضي فيه) و`NSCalendar`
 * بمعرّف `islamic-umalqura` على الآيفون. مفيش مكتبة جديدة.
 */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/** التاريخ الميلادي [date] (yyyy-MM-dd) بالهجري. */
expect fun hijriOf(date: IsoDate): HijriDate

/** نفس اليوم الهجري بعد [years] سنة هجرية، بالميلادي. لو اليوم مش موجود في الشهر الجديد (30 والشهر 29) ⇒ آخر الشهر. */
expect fun addHijriYears(date: IsoDate, years: Int): IsoDate
