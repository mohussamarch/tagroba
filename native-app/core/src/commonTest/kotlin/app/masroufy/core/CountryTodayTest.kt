package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** cty-8: «النهارده» واحد للبلد بتوقيت حزمتها — في الشتا القاهرة ورا الرياض بساعة، فبعد نص الليل بشوية اليومين بيختلفوا. */
class CountryTodayTest {
    @Test fun todayFollowsTheCountryPackTimeZoneNotUtcOrThePhone() {
        val now = "2026-01-15T21:30:00.000Z"
        assertEquals("2026-01-16", todayInCountry("SA", now), "الرياض +3 ⇒ بعد نص الليل")
        assertEquals("2026-01-15", todayInCountry("EG", now), "القاهرة +2 ⇒ لسه قبل نص الليل")
        assertEquals("2026-01-16", todayInCountry(null, now), "من غير بلد ⇒ الأصلية (السعودية)")
        assertEquals("2026-10-23", todayInCountry("EG", "2026-10-23"), "تاريخ بس ⇒ زي ما هو")
    }
}
