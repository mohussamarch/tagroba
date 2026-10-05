package app.masroufy.core

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

// `HijrahChronology` الافتراضي في الجافا = «Hijrah-umalqura» (أم القرى). `plus(سنين)` بيرجّع آخر يوم صالح لو اليوم مش موجود.
actual fun hijriOf(date: IsoDate): HijriDate {
    val h = HijrahDate.from(LocalDate.parse(date))
    return HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
}

actual fun addHijriYears(date: IsoDate, years: Int): IsoDate =
    LocalDate.from(HijrahDate.from(LocalDate.parse(date)).plus(years.toLong(), ChronoUnit.YEARS)).toString()
