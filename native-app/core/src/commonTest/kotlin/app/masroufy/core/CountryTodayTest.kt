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

    /** cty-9: المصدر الوحيد لـ«البلدين شغالين» — المؤرشفة ما بتتحسبش. cty-12: اسم البلد من الكود مش المتخزن. */
    @Test fun bothCountriesActiveIgnoresArchivedAndTheLabelComesFromTheCode() {
        val egypt = newSpaceFor("EG", emptyList(), "2026-01-01T00:00:00Z")
        assertEquals(false, bothCountriesActive(listOf(defaultSpace())))
        assertEquals(true, bothCountriesActive(listOf(defaultSpace(), egypt)))
        assertEquals(false, bothCountriesActive(listOf(defaultSpace(), egypt.copy(archived = true))))
        assertEquals(countryLabel("EG"), egypt.copy(name = "Egypt (old)").label)
    }
}
