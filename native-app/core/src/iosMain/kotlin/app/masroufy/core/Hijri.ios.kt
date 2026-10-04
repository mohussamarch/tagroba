@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.masroufy.core

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarIdentifierIslamicUmmAlQura
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents

// أم القرى من تقويم النظام (`islamic-umalqura`) — نفس جدول الجافا. التقويمين بنفس منطقة الوقت (بتاعة الجهاز)،
// واليوم بيتحسب **الضهر** (12:00) عشان أي فرق توقيت أو تغيير ساعة ما يزحلقش التاريخ يوم.
private fun calendar(identifier: String?): NSCalendar = NSCalendar(calendarIdentifier = identifier!!)

private val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay

private fun gregorianNoon(date: IsoDate): NSDate {
    val p = parseIsoDate(date)
    val parts = NSDateComponents()
    parts.year = p.year.toLong()
    parts.month = p.month.toLong()
    parts.day = p.day.toLong()
    parts.hour = 12
    return calendar(NSCalendarIdentifierGregorian).dateFromComponents(parts)!!
}

actual fun hijriOf(date: IsoDate): HijriDate {
    val h = calendar(NSCalendarIdentifierIslamicUmmAlQura).components(units, fromDate = gregorianNoon(date))
    return HijriDate(h.year.toInt(), h.month.toInt(), h.day.toInt())
}

actual fun addHijriYears(date: IsoDate, years: Int): IsoDate {
    val next = calendar(NSCalendarIdentifierIslamicUmmAlQura).dateByAddingUnit(NSCalendarUnitYear, value = years.toLong(), toDate = gregorianNoon(date), options = 0u)!!
    val g = calendar(NSCalendarIdentifierGregorian).components(units, fromDate = next)
    return formatIsoDate(DateParts(g.year.toInt(), g.month.toInt(), g.day.toInt()))
}
