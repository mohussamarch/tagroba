package app.masroufy.core

/**
 * «آخر مرة قرا لوحده» (الخلفية): الوقت منسّق **من غير لغة الجوال ولا تقويمه** ولا أرقام غير 0-9 (WesternDigitsTest) — الدالة صافية:
 * وقت القراءة + الوقت دلوقتي + فرق منطقة الجهاز بالملّي ثانية ⇒ [LastReadWhen]. عمرها ما بتخترع وقت (قاعدة 10): مفيش قيمة ⇒ الشاشة بتقول «لسه».
 */
sealed interface LastReadWhen {
    data object JustNow : LastReadWhen

    data class MinutesAgo(val minutes: Int) : LastReadWhen

    data class HoursAgo(val hours: Int) : LastReadWhen

    /** [text] = `2026-10-22 14:05` بتوقيت الجهاز. */
    data class At(val text: String) : LastReadWhen
}

private const val LR_MIN_MS = 60_000L
private const val LR_HOUR_MS = 3_600_000L
private const val LR_DAY_MS = 86_400_000L

/** أقل من دقيقة (أو وقت في المستقبل — ساعة الجهاز اتغيرت) ⇒ «دلوقتي» · أقل من ساعة ⇒ دقايق · أقل من 6 ساعات ⇒ ساعات · غير كده ⇒ تاريخ وساعة. */
fun lastReadWhen(lastMillis: Long, nowMillis: Long, offsetMillis: Long): LastReadWhen {
    val age = nowMillis - lastMillis
    return when {
        age < LR_MIN_MS -> LastReadWhen.JustNow
        age < LR_HOUR_MS -> LastReadWhen.MinutesAgo((age / LR_MIN_MS).toInt())
        age < 6 * LR_HOUR_MS -> LastReadWhen.HoursAgo((age / LR_HOUR_MS).toInt())
        else -> {
            val local = lastMillis + offsetMillis
            val minuteOfDay = (local.mod(LR_DAY_MS) / LR_MIN_MS).toInt()
            LastReadWhen.At("${dayNumberToIso(local.floorDiv(LR_DAY_MS).toInt())} ${two(minuteOfDay / 60)}:${two(minuteOfDay % 60)}")
        }
    }
}

private fun two(n: Int) = if (n < 10) "0$n" else "$n"
