package app.masroufy.usecase

import app.masroufy.core.Period
import app.masroufy.core.buildPeriod

/**
 * تحريك الفترة شهور لقدام أو لورا — مشترك بين شاشات بتقرا تاريخ فترات سابقة.
 * قسمة جافاسكربت `Math.floor` على السالب: -1/12 ⇒ -1 مش 0.
 */
internal fun shiftPeriod(period: Period, delta: Int, payday: Int): Period {
    val parts = period.key.split("-")
    val year = parts[0].toInt()
    val month = parts[1].toInt()
    val total = year * 12 + (month - 1) + delta
    val years = if (total >= 0) total / 12 else -((-total + 11) / 12)
    val monthIndex = total - years * 12
    return buildPeriod(years, monthIndex + 1, payday)
}
