package app.masroufy.device

import app.masroufy.core.LocalMoment
import app.masroufy.core.parseIsoDate
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «النهارده» من غير لغة الجوال ولا تقويمه (عطل شاومي في 0.5/0.5.1): نفس اللحظة لازم تطلع نفس التاريخ بأرقام 0-9 وتقويم جريجوري
 * مهما كانت اللغة — عربي مصر/السعودية · فارسي · تايلاندي بوذي · ياباني إمبراطوري · هجري.
 */
class LocalClockTest {
    private val savedLocale = Locale.getDefault()
    private val savedZone = TimeZone.getDefault()

    @AfterTest
    fun restore() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedZone)
    }

    // 2026-10-19 21:30 بتوقيت السعودية (+3) = 18:30 عالمي
    private val moment = java.time.Instant.parse("2026-10-19T18:30:00Z").toEpochMilli()

    @Test
    fun sameDateInEveryLanguageAndCalendar() {
        val riyadh = TimeZone.getTimeZone("Asia/Riyadh")
        for (tag in listOf("en-US", "ar-EG", "ar-SA", "ar", "fa-IR", "th-TH-u-ca-buddhist", "ja-JP-u-ca-japanese", "ar-SA-u-ca-islamic-umalqura", "hi-IN-u-nu-deva")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val m = LocalClock.at(moment, riyadh)
            assertEquals(LocalMoment("2026-10-19", 21), m, tag)
            parseIsoDate(m.date) // ما بيرميش
        }
    }

    @Test
    fun followsPhoneTimeZoneAcrossMidnight() {
        val riyadh = TimeZone.getTimeZone("Asia/Riyadh")
        assertEquals(LocalMoment("2026-10-19", 23), LocalClock.at(moment + 2 * 3_600_000L, riyadh))
        assertEquals(LocalMoment("2026-10-20", 0), LocalClock.at(moment + 3 * 3_600_000L - 1_800_000L, riyadh))
        assertEquals(LocalMoment("2026-10-19", 18), LocalClock.at(moment, TimeZone.getTimeZone("UTC")))
        assertEquals(LocalMoment("2026-10-20", 8), LocalClock.at(moment, TimeZone.getTimeZone("Pacific/Kiritimati")))
        assertEquals(LocalMoment("2026-10-19", 14), LocalClock.at(moment, TimeZone.getTimeZone("America/New_York")))
    }

    @Test
    fun beforeEpochStillCorrect() {
        assertEquals(LocalMoment("1969-12-31", 23), LocalClock.at(-3_600_000L, TimeZone.getTimeZone("UTC")))
    }
}
