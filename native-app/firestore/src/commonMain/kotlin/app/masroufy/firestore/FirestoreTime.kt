package app.masroufy.firestore

import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import dev.gitlive.firebase.firestore.Timestamp

/**
 * وقت فايربيز (`Timestamp`) ⇐⇒ نص ISO بنفس شكل `toDate().toISOString()` في التطبيق الحالي (`2026-10-01T10:00:00.123Z`):
 * المللي ثانية بتتقص لتحت زي `Date` في جافاسكربت. كود نقي (من غير مكتبة تواريخ).
 */
internal fun isoOfEpoch(seconds: Long, nanoseconds: Int): String {
    val day = seconds.floorDiv(86_400L)
    val secOfDay = seconds.mod(86_400L)
    fun two(n: Long) = n.toString().padStart(2, '0')
    val millis = (nanoseconds / 1_000_000).toString().padStart(3, '0')
    return "${dayNumberToIso(day.toInt())}T${two(secOfDay / 3600)}:${two(secOfDay % 3600 / 60)}:${two(secOfDay % 60)}.${millis}Z"
}

/** `2026-10-01T10:00:00.123Z` (أو من غير مللي) ⇒ `Timestamp`. */
internal fun timestampOf(iso: String): Timestamp {
    require(iso.length >= 20 && iso[10] == 'T' && iso.endsWith("Z")) { "وقت مش بصيغة ISO: $iso" }
    val day = toDayNumber(parseIsoDate(iso.substring(0, 10))).toLong()
    val h = iso.substring(11, 13).toLong()
    val m = iso.substring(14, 16).toLong()
    val s = iso.substring(17, 19).toLong()
    val millis = if (iso[19] == '.') iso.substring(20, iso.length - 1).padEnd(3, '0').take(3).toInt() else 0
    return Timestamp(day * 86_400L + h * 3600 + m * 60 + s, millis * 1_000_000)
}
