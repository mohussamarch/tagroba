package app.masroufy.device

import app.masroufy.core.LocalMoment
import app.masroufy.core.dayNumberToIso
import java.util.TimeZone

/**
 * اليوم والساعة بتوقيت الجوال **من غير لغة الجوال ولا تقويمه** (عطل 0.5/0.5.1 على شاومي — HANDOVER):
 * `"%04d".format(...)` بيكتب بأرقام اللغة (عربي ⇒ ٢٠٢٦ ⇒ `parseIsoDate` بيرفض ⇒ التطبيق بيقفل في أول شاشة)، و`Calendar.getInstance()`
 * بيمشي على تقويم اللغة (تايلاندي ⇒ سنة 2569). هنا: الوقت العالمي + فرق المنطقة الزمنية بس ⇒ أيام جريجوري وأرقام 0-9 دايمًا.
 * ⚠️ مشترك بين أندرويد والكمبيوتر (`src/jvmShared`) عشان يتختبر على الكمبيوتر بنفس الكود.
 */
object LocalClock {
    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L

    fun now(): LocalMoment = at(System.currentTimeMillis(), TimeZone.getDefault())

    fun at(utcMillis: Long, zone: TimeZone): LocalMoment {
        val local = utcMillis + zone.getOffset(utcMillis)
        return LocalMoment(dayNumberToIso(local.floorDiv(DAY_MS).toInt()), (local.mod(DAY_MS) / HOUR_MS).toInt())
    }
}
