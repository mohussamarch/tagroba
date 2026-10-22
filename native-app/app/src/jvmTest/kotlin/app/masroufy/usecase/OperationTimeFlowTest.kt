package app.masroufy.usecase

import app.masroufy.core.EGYPT_TIME_ZONE
import app.masroufy.core.Meal
import app.masroufy.core.RESTAURANT_SUB_ID
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.SaudiBankSmsReader
import app.masroufy.core.localTimeOn
import app.masroufy.core.parseTimed
import app.masroufy.core.qnbCardTime
import app.masroufy.core.suggestedMealOf
import app.masroufy.memory.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-1e (OVERRIDES §110-1): **ساعة العملية المخفية** — رسالة البنك بتتسجل بساعة وصولها المحلية (توقيت حزمة البلد · §88) ⇒ اقتراح الوجبة
 * و«مكرر؟» (§109) بيشتغلوا. من غير ساعة ⇒ لا وجبة ولا سؤال (القاعدة 10). كل الرسايل مخترعة (`SmsAutoFixture.kt`).
 */
class OperationTimeFlowTest {
    private val timed = SmsSpace(parse = { m, n -> SaudiBankSmsReader.parseTimed(m, n, SAUDI_TIME_ZONE) })

    private fun twinBody(amount: String, day: String = "26/10/07") = "شراء\nبـSR $amount\nلدى:TEST CAFE\n$day"

    @Test fun localTimeFollowsThePackZoneAndTheDay() {
        assertEquals("13:00:30", localTimeOn(SAUDI_TIME_ZONE, "2026-10-07T10:00:30Z", "2026-10-07"))
        assertEquals("13:00:30", localTimeOn(EGYPT_TIME_ZONE, "2026-10-07T10:00:30Z", "2026-10-07"), "القاهرة صيفي +3 في أكتوبر قبل آخر خميس")
        assertEquals("12:00:30", localTimeOn(EGYPT_TIME_ZONE, "2026-11-07T10:00:30Z", "2026-11-07"), "القاهرة شتوي +2")
        assertNull(localTimeOn(SAUDI_TIME_ZONE, "2026-10-07T22:30:00Z", "2026-10-07"), "بعد نص الليل المحلي = يوم تاني ⇒ مش ساعة العملية")
        assertNull(localTimeOn(SAUDI_TIME_ZONE, "2026-10-07", "2026-10-07"), "تاريخ من غير ساعة")
        assertEquals("14:05", qnbCardTime("CARD PURCHASE 07/10/26 14:05-TEST SHOP CAIRO", "2026-10-07"))
        assertNull(qnbCardTime("CARD PURCHASE 06/10/26 14:05-TEST SHOP CAIRO", "2026-10-07"), "يوم الكارت غير يوم السطر")
    }

    @Test fun smsRecordedRestaurantOpGetsAMealFromItsHiddenTime() = runBlocking<Unit> {
        val world = SmsWorld(listOf(timed)).enable()
        world.receive(sms("m1", CAFE, at = "2026-10-07T10:00:00Z"))
        assertEquals(1, world.auto().run().recorded)
        val t = timed.all().single()
        assertEquals("minute" to "13:00:00", t.datePrecision to t.sourceTime, "ساعة الرياض المحلية اتحفظت")
        assertEquals(Meal.LUNCH, suggestedMealOf(t.copy(categoryId = RESTAURANT_SUB_ID), emptyList()), "13:00 = غدا")
        assertEquals(Meal.BREAKFAST, suggestedMealOf(t.copy(categoryId = RESTAURANT_SUB_ID, sourceTime = "08:15:00"), emptyList()))
    }

    @Test fun twoChargesThirtySecondsApartRaiseTheDuplicateAsk() = runBlocking<Unit> {
        val world = SmsWorld(listOf(timed)).enable()
        world.receive(sms("m1", twinBody("25"), at = "2026-10-07T10:00:00Z"), sms("m2", twinBody("25.50"), at = "2026-10-07T10:00:30Z"))
        world.auto().run()
        val rows = timed.all()
        assertEquals(2, rows.size, "الاتنين اتسجلوا")
        val asks = DuplicateChargeAsks("sa", timed.txnStore, FixedClock("2026-10-07T11:00:00.000Z"))
        assertEquals(1, asks.scan("2026-10-07", "2026-10-07"))
        val flagged = timed.all().filter { it.duplicateAskPending == true }
        assertEquals(listOf("13:00:30"), flagged.map { it.sourceTime }, "الأحدث بس عليه «مكرر؟»")
    }

    /** فحص 0.5.2 (00:10 الرياض): بعد نص الليل المحلي اليوم بالعالمي لسه امبارح — `scanRecent` لازم يلاقي خصمين النهارده المحلي. */
    @Test fun scanRecentFindsTodaysChargesJustAfterLocalMidnight() = runBlocking<Unit> {
        val world = SmsWorld(listOf(timed)).enable()
        world.receive(sms("m1", twinBody("25", "26/10/08"), at = "2026-10-07T21:05:00Z"), sms("m2", twinBody("25", "26/10/08"), at = "2026-10-07T21:05:30Z"))
        world.auto().run()
        assertEquals(setOf("2026-10-08"), timed.all().map { it.occurredAt.take(10) }.toSet(), "تاريخ العمليات بتوقيت الرياض")
        assertEquals(1, DuplicateChargeAsks("sa", timed.txnStore, FixedClock("2026-10-07T21:10:00.000Z")).scanRecent())
    }

    /** 0.5.2: نفس المحل ونفس المبلغ بعد 30 ث في تشغيلة تانية — فحص الرسايل القديم كان بيمسكها «بانتظار تأكيدك». */
    @Test fun anIdenticalSecondChargeWithinAMinuteIsRecordedAndAsked() = runBlocking<Unit> {
        val world = SmsWorld(listOf(timed)).enable()
        world.receive(sms("m1", twinBody("41"), at = "2026-10-07T10:00:00Z"))
        assertEquals(1, world.auto().run().recorded)
        world.receive(sms("m2", twinBody("41"), at = "2026-10-07T10:00:30Z"))
        val second = world.auto().run()
        assertEquals(1, second.recorded, "التانية اتسجلت")
        assertTrue(second.waiting.isEmpty(), "مش بانتظار التأكيد")
        assertEquals(1, DuplicateChargeAsks("sa", timed.txnStore, FixedClock("2026-10-07T11:00:00.000Z")).scan("2026-10-07", "2026-10-07"))
    }

    /** نفس الرسالة وصلت مرتين (نفس النص والوقت) ⇒ النسخة بتتشال من غير تسجيل ولا سؤال. */
    @Test fun theSameMessageDeliveredTwiceIsDroppedSilently() = runBlocking<Unit> {
        val world = SmsWorld(listOf(timed)).enable()
        world.receive(sms("m1", twinBody("41"), at = "2026-10-07T10:00:00Z"))
        world.auto().run()
        world.receive(sms("m1-copy", twinBody("41"), at = "2026-10-07T10:00:00Z"))
        val again = world.auto().run()
        assertEquals(0, again.recorded)
        assertTrue(again.waiting.isEmpty(), "ما بتستناش تأكيد")
        assertEquals(1, timed.all().size)
        assertEquals(0, DuplicateChargeAsks("sa", timed.txnStore, FixedClock("2026-10-07T11:00:00.000Z")).scan("2026-10-07", "2026-10-07"))
    }

    @Test fun withoutATimeThereIsNoMealAndNoAsk() = runBlocking<Unit> {
        val plain = SmsSpace() // القارئ من غير ساعة (زي الكشف اللي سطره مفيهوش ساعة)
        val world = SmsWorld(listOf(plain)).enable()
        world.receive(sms("m1", twinBody("25"), at = "2026-10-07T10:00:00Z"), sms("m2", twinBody("25.50"), at = "2026-10-07T10:00:30Z"))
        world.auto().run()
        val rows = plain.all()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.datePrecision == "day" && it.sourceTime == null })
        assertTrue(rows.all { suggestedMealOf(it.copy(categoryId = RESTAURANT_SUB_ID), emptyList()) == null }, "القاعدة 10: لا وجبة")
        assertEquals(0, DuplicateChargeAsks("sa", plain.txnStore, FixedClock("2026-10-07T11:00:00.000Z")).scan("2026-10-07", "2026-10-07"))
    }
}
