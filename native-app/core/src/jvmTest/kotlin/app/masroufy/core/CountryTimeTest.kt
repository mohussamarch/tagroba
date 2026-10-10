package app.masroufy.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **§88 (رد المالك 2026-10-11): حدود اليوم بتوقيت كل بلد** — القاهرة بالتوقيت الصيفي والرياض، مش +3 مثبّتة. الحساب اللي من غير مكتبة
 * (`CountryTime.kt`) متقارن بـ`java.time` كل نص ساعة من 2015 لآخر 2027 وكل يوم (بداية اليوم)، ومنها يوم بداية الصيفي في مصر اللي مفيهوش
 * 00:00. وحالة المالك بالحرف: رسالة مصرية الساعة 23:30 بتوقيت القاهرة بتقع على نفس يوم القاهرة صيف وشتا، والسعودية زي ما هي.
 */
class CountryTimeTest {
    private val from = LocalDate.of(2015, 1, 1)
    private val until = LocalDate.of(2028, 1, 1)

    @Test fun everyHalfHourLandsOnTheSameDayAsJavaTimeInBothCountries() {
        for (zone in KNOWN_TIME_ZONES) {
            val id = ZoneId.of(zone)
            var t = from.atStartOfDay(id).toInstant().toEpochMilli() - 3_600_000L
            val end = until.atStartOfDay(id).toInstant().toEpochMilli()
            while (t < end) {
                val expected = Instant.ofEpochMilli(t).atZone(id).toLocalDate().toString()
                val actual = dayAt(zone, t)
                if (actual != expected) fail("$zone ${Instant.ofEpochMilli(t)}: $actual ≠ $expected")
                t += 30 * 60_000L
            }
        }
    }

    @Test fun everyDayStartsAtLocalMidnightLikeJavaTime() {
        for (zone in KNOWN_TIME_ZONES) {
            val id = ZoneId.of(zone)
            var d = from
            while (d < until) {
                val expected = d.atStartOfDay(id).toInstant()
                val actual = dayStartMillisIn(zone, d.toString())
                if (actual != expected.toEpochMilli()) fail("$zone $d: ${Instant.ofEpochMilli(actual)} ≠ $expected")
                assertEquals(expected.toString(), dayStartIn(zone, d.toString()).let { Instant.parse(it).toString() })
                d = d.plusDays(1)
            }
        }
    }

    @Test fun dayStartsAsTheInboxReadsThem() {
        assertEquals("2026-01-14T22:00:00Z", dayStartIn(EGYPT_TIME_ZONE, "2026-01-15"), "Cairo winter +2")
        assertEquals("2026-07-14T21:00:00Z", dayStartIn(EGYPT_TIME_ZONE, "2026-07-15"), "Cairo summer +3")
        assertEquals("2026-04-23T22:00:00Z", dayStartIn(EGYPT_TIME_ZONE, "2026-04-24"), "summer starts: no 00:00 that day ⇒ its first moment (01:00)")
        assertEquals("2026-01-14T21:00:00Z", dayStartIn(SAUDI_TIME_ZONE, "2026-01-15"), "Riyadh +3 — unchanged")
        assertEquals("2026-07-14T21:00:00Z", dayStartIn(SAUDI_TIME_ZONE, "2026-07-15"))
        assertEquals("2026-10-09", dayIn(EGYPT_TIME_ZONE, "2026-10-09"), "a date-only value stays as it is")
        assertEquals(null, dayIn(EGYPT_TIME_ZONE, "not a time"))
    }

    /** حالة المالك: 23:30 القاهرة = نفس يوم القاهرة (الصيفي 20:30 جرينتش · الشتوي 21:30)؛ الرياض زي ما هي (+3 طول السنة). */
    @Test fun anEgyptianMessageAt2330CairoStaysOnTheCairoDaySummerAndWinter() {
        val card = "Your Debit Card **1234 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40"
        fun eg(at: String) = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("TESTBANK", at, card), 1)).row.date
        assertEquals("2026-07-15", eg("2026-07-15T20:30:00Z"), "summer 23:30 Cairo")
        assertEquals("2026-01-15", eg("2026-01-15T21:30:00Z"), "winter 23:30 Cairo — fixed +3 said the 16th")
        assertEquals("2026-01-16", eg("2026-01-15T22:30:00Z"), "winter 00:30 Cairo")
        assertEquals("2026-07-15", dayIn(EGYPT_TIME_ZONE, "2026-07-15T20:30:00Z"))
        assertEquals("2026-01-15", dayIn(EGYPT_TIME_ZONE, "2026-01-15T21:30:00Z"))

        val saudi = "PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE"
        fun sa(at: String) = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", at, saudi), 1)).row.date
        assertEquals("2026-01-15", sa("2026-01-15T20:30:00Z"), "23:30 Riyadh")
        assertEquals("2026-01-16", sa("2026-01-15T21:30:00Z"), "00:30 Riyadh — unchanged")
        assertEquals("2026-01-16", dayIn(SAUDI_TIME_ZONE, "2026-01-15T21:30:00Z"))
    }

    @Test fun everyCountryPackHasAKnownZoneAndItsParserUsesTheSameOne() {
        assertTrue(COUNTRY_PACKS.values.all { it.timeZone in KNOWN_TIME_ZONES }, COUNTRY_PACKS.values.map { it.timeZone }.toString())
        assertEquals(SAUDI_TIME_ZONE, SAUDI_PACK.timeZone, "parseBankSms reads dateless days in SAUDI_TIME_ZONE")
        assertEquals(EGYPT_TIME_ZONE, EGYPT_PACK.timeZone, "parseEgyptBankSms reads dateless days in EGYPT_TIME_ZONE")
        assertFailsWith<IllegalArgumentException> { dayAt("Europe/London", 0) }
    }
}
