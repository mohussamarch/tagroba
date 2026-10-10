package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.EGYPT_TIME_ZONE
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.port.QueuedSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **§88 (رد المالك 2026-10-11): «اقرأ اللي فات؟» بتوقيت كل بلد** — «أقدم رسالة يوم كذا» و«اقرأ من يوم كذا» بيوم القاهرة (بالصيفي) لبنوك مصر،
 * وبيوم الرياض للسعودية (زي ما كان). كانت +3 ثابتة: رسالة مصرية 23:30 بالليل في الشتا كانت بتبان على اليوم اللي بعده، و«من يوم 16» كان بيقرا
 * ساعة من يوم 15. كل الرسايل مخترعة.
 */
class SmsHistoryZoneTest {
    private val card = "Your Debit Card **1234 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40"

    /** رسالة من غير تاريخ بشكل معروف في البلد (اليوم من وقت الوصول). */
    private fun bodyIn(zone: String) = if (zone == EGYPT_TIME_ZONE) card else "PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE"

    private fun world(zone: String) = if (zone == EGYPT_TIME_ZONE) {
        HistoryWorld(space = SmsSpace("eg", wallets = listOf(CASH, HISTORY_BANK), parse = ::parseEgyptBankSms, window = CROSS_SOURCE_WINDOW_DAYS), zone = zone)
    } else {
        HistoryWorld(zone = zone)
    }

    private fun HistoryWorld.space() = space.spaceId

    /** «أقدم رسالة يوم كذا» للرسالة دي لوحدها على الجوال. */
    private suspend fun oldestDay(zone: String, at: String): String {
        val w = world(zone)
        w.memory.deliver(QueuedSms("m1", "TESTBANK", at, bodyIn(zone)))
        w.memory.enable(listOf("TESTBANK"))
        return assertNotNull(w.history().question(w.space())).oldestDay
    }

    @Test fun theOldestDayIsTheCountryDay() = runBlocking<Unit> {
        // 23:30 القاهرة: الشتا 21:30 جرينتش · الصيف 20:30
        assertEquals("2026-01-15", oldestDay(EGYPT_TIME_ZONE, "2026-01-15T21:30:00Z"), "winter — fixed +3 said the 16th")
        assertEquals("2026-06-15", oldestDay(EGYPT_TIME_ZONE, "2026-06-15T20:30:00Z"), "summer")
        assertEquals("2026-01-16", oldestDay(EGYPT_TIME_ZONE, "2026-01-15T22:30:00Z"), "00:30 Cairo")
        // السعودية زي ما هي: 21:30 جرينتش = 00:30 الرياض
        assertEquals("2026-01-16", oldestDay(SAUDI_TIME_ZONE, "2026-01-15T21:30:00Z"))
        assertEquals("2026-01-15", oldestDay(SAUDI_TIME_ZONE, "2026-01-15T20:30:00Z"))
    }

    /** «اقرأ من يوم [from]»: الرسايل اللي دخلت الصندوق — بتبدأ من 00:00 بتوقيت البلد بالظبط. */
    private suspend fun readFromDay(zone: String, from: String, vararg at: Pair<String, String>): Pair<List<String>, String> {
        val w = world(zone)
        w.memory.deliver(*at.map { (id, time) -> QueuedSms(id, "TESTBANK", time, bodyIn(zone)) }.toTypedArray())
        w.memory.enable(listOf("TESTBANK"))
        val history = w.history()
        val q = assertNotNull(history.question(w.space()))
        history.start(w.space(), q.senders, from)
        return w.memory.sync().messages.map { it.id }.sorted() to assertNotNull(w.memory.readMarks()).floor
    }

    @Test fun readingFromADayStartsAtTheCountryMidnight() = runBlocking<Unit> {
        // الشتا: 23:30 القاهرة يوم 15 (21:30 جرينتش) برا · 00:30 يوم 16 (22:30) جوه
        assertEquals(
            listOf("in") to "2026-01-15T22:00:00Z",
            readFromDay(EGYPT_TIME_ZONE, "2026-01-16", "out" to "2026-01-15T21:30:00Z", "in" to "2026-01-15T22:30:00Z"),
        )
        // الصيف: 23:30 القاهرة يوم 15 (20:30) برا · 00:30 يوم 16 (21:30) جوه
        assertEquals(
            listOf("in") to "2026-06-15T21:00:00Z",
            readFromDay(EGYPT_TIME_ZONE, "2026-06-16", "out" to "2026-06-15T20:30:00Z", "in" to "2026-06-15T21:30:00Z"),
        )
        // السعودية زي ما هي: من 21:00 جرينتش (00:00 الرياض) ⇒ 21:30 جوه و20:30 برا
        assertEquals(
            listOf("in") to "2026-01-15T21:00:00Z",
            readFromDay(SAUDI_TIME_ZONE, "2026-01-16", "out" to "2026-01-15T20:30:00Z", "in" to "2026-01-15T21:30:00Z"),
        )
    }
}
