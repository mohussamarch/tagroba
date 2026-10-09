package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * §77-C (قرار المالك 2026-10-09): «الرسالة من غير تاريخ ⇒ ياخد يوم وصول الرسالة بتوقيت البلد — لكل الأشكال اللي مفيهاش تاريخ».
 * السعودية +3 · مصر +2 (والصيفي +3). الساعة المكتوبة بعد ساعة الوصول ⇒ الرسالة عدّت نص الليل ⇒ اليوم اللي قبله (اختيار Claude).
 * الرسالة اللي فيها تاريخ القارئ مش قادر يقراه لسه بتترفض «التاريخ مش واضح». كل الرسايل مخترعة.
 */
class SmsDatelessTest {
    private fun sa(body: String, at: String) = parseBankSms(BankSmsMessage("TESTBANK", at, body), 1)

    private fun eg(body: String, at: String) = parseEgyptBankSms(BankSmsMessage("TESTBANK", at, body), 1)

    private fun ok(r: SmsParseResult, what: String = "") = assertIs<SmsParseResult.Ok>(r, "$what $r").row

    @Test fun aSaudiKnownLayoutWithoutADateTakesTheRiyadhArrivalDay() {
        // 21:30 جرينتش = 00:30 الرياض يوم 9
        val row = ok(sa("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE", "2026-10-08T21:30:00Z"))
        assertEquals("2026-10-09", row.date)
        assertTrue(row.shape.clear, "known layout ⇒ still records itself")
        // قالب بنك (مش عنوان موحّد): الراجحي «حوالة محلية واردة» من غير تاريخ
        assertEquals("2026-10-08", ok(sa("حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111", "2026-10-08T12:00:00Z")).date)
    }

    @Test fun anEgyptianKnownLayoutWithoutADateTakesTheCairoArrivalDayInSummerAndWinter() {
        val card = "Your Debit Card **1234 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40"
        // الصيفي (+3): 21:30 جرينتش = 00:30 القاهرة يوم 2 يوليو
        assertEquals("2026-07-02", ok(eg(card, "2026-07-01T21:30:00Z")).date)
        // الشتوي (+2): 21:30 جرينتش = 23:30 القاهرة يوم 1 ديسمبر · 22:30 = 00:30 يوم 2
        assertEquals("2026-12-01", ok(eg(card, "2026-12-01T21:30:00Z")).date)
        assertEquals("2026-12-02", ok(eg(card, "2026-12-01T22:30:00Z")).date)
        assertTrue(ok(eg(card, "2026-12-01T22:30:00Z")).shape.clear)
    }

    @Test fun aClockOnlyMessageThatCrossedMidnightTakesTheDayBefore() {
        // مكتوب 23:58 ووصل 21:03 جرينتش = 00:03 الرياض يوم 9 ⇒ يوم 8
        val late = ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n23:58\nمدى *3906", "2026-10-08T21:03:00Z"))
        assertEquals("2026-10-08", late.date)
        assertTrue(late.shape.clear, "§77-C removed the wait for clock-only messages")
        // مكتوب 14:22 ووصل 14:25 بتوقيت الرياض ⇒ يوم الوصول
        assertEquals("2026-10-08", ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n14:22\nمدى *3906", "2026-10-08T11:25:00Z")).date)
        // ساعة البنك قدام ساعة الجوال بدقايق (مكتوب 14:30 ووصل 14:25) ⇒ برضه يوم الوصول، مش امبارح
        assertEquals("2026-10-08", ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n14:30\nمدى *3906", "2026-10-08T11:25:00Z")).date)
        // مصر بنفس القاعدة: «11:58 PM» ووصلت 00:04 القاهرة
        val vf = "11:58 PM: Received EGP260.00 from 00201001112296 to Mobile Account Number 01009998877. Ref: 900000441127 Available Balance: 560.00"
        assertEquals("2026-10-08", ok(eg(vf, "2026-10-08T21:04:00Z")).date)
    }

    @Test fun twoDifferentClocksWithoutADateStillWait() {
        val row = ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n09:10 23:58\nمدى *3906", "2026-10-08T21:03:00Z"))
        assertTrue(!row.shape.clear, "which clock is the transaction's is not clear")
    }

    @Test fun aDateTheReaderCannotReadIsStillRejected() {
        val unclear = uiText(TextKey.SMS_DATE_UNCLEAR)
        assertEquals(unclear, assertIs<SmsParseResult.Rejected>(sa("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: 2026-13-45", "2026-10-08T09:00:00Z")).reason)
        assertEquals(
            unclear,
            assertIs<SmsParseResult.Rejected>(eg("تم خصم 500.00 جم من بطاقة الخصم المباشر رقم 6604 عند TEST STORE يوم 13-45 الساعة 09:10", "2026-10-08T09:00:00Z")).reason,
        )
        // رسالة من غير تاريخ ومش على شكل معروف ⇒ زي الأول (السعودية: «التاريخ مش واضح»)
        assertEquals(unclear, assertIs<SmsParseResult.Rejected>(sa("رسالة من البنك\nشراء\nبـ64.25 SAR\nمن TEST GROCER\nمدى *6604", "2026-10-08T09:00:00Z")).reason)
    }

    @Test fun writtenClocksAreReadWithTheirHalfOfTheDay() {
        assertEquals(listOf(23 * 60 + 58), writtenClockTimes("11:58 PM: Received"))
        assertEquals(listOf(5), writtenClockTimes("12:05 AM"))
        assertEquals(listOf(23 * 60 + 58), writtenClockTimes("الساعة 11:58 م"))
        assertEquals(listOf(14 * 60 + 22), writtenClockTimes("14:22:05 14:22"))
        assertEquals(emptyList(), writtenClockTimes("25:70"))
        assertEquals("2026-10-09", datelessDay("no clock", "2026-10-09", SmsClock.RIYADH), "a date-only arrival stays as it is")
    }
}
