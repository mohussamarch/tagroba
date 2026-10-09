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

    /**
     * مراجعة S1: الساعة المكتوبة قدام ساعة الوصول **في نص النهار** ما معناهاش إن الرسالة اتأخرت يوم كامل — ساعة البنك أو الجوال غلط (أو فرق
     * ساعة في التوقيت الصيفي) ⇒ يوم الوصول والرسالة بتستنى. اليوم اللي قبله بس لو اتكتبت بالليل ووصلت الصبح بتأخير 8 ساعات بالكتير.
     */
    @Test fun aClockAheadOfTheArrivalInTheDayKeepsTheArrivalDayAndWaits() {
        // مكتوب 14:45 ووصل 14:25 بتوقيت الرياض (فرق 20 دقيقة، أكتر من الهامش) — كانت بتتسجل لوحدها يوم 7
        val ahead = ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n14:45\nمدى *3906", "2026-10-08T11:25:00Z"))
        assertEquals("2026-10-08", ahead.date)
        assertTrue(!ahead.shape.clear, "the two clocks disagree ⇒ the owner confirms once")
        // مكتوب 23:30 والجوال كان مقفول لحد 06:00 (6 ساعات ونص) ⇒ امبارح، ولوحدها
        val overnight = ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n23:30\nمدى *3906", "2026-10-09T03:00:00Z"))
        assertEquals("2026-10-08" to true, overnight.date to overnight.shape.clear)
        // مكتوب 22:00 ووصل 07:00 (9 ساعات — أكتر من ليلة معقولة) ⇒ يوم الوصول ومستنية
        val long = ok(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن TEST CAFE\n22:00\nمدى *3906", "2026-10-09T04:00:00Z"))
        assertEquals("2026-10-09" to false, long.date to long.shape.clear)
        // مصر: ساعة الرسالة قدام بساعة (فرق التوقيت الصيفي) ⇒ يوم الوصول، مش امبارح
        val vf = "15:10: Received EGP260.00 from 00201001112296 to Mobile Account Number 01009998877. Ref: 900000441127 Available Balance: 560.00"
        val dst = ok(eg(vf, "2026-10-08T11:10:00Z")) // 14:10 القاهرة (الصيفي +3)
        assertEquals("2026-10-08" to false, dst.date to dst.shape.clear)
        assertTrue(datelessClockConflict(vf, "2026-10-08T11:10:00Z", SmsClock.CAIRO))
        assertTrue(!datelessClockConflict(vf, "2026-10-08", SmsClock.CAIRO), "a date-only arrival has no clock to compare")
    }

    @Test fun aDateTheReaderCannotReadIsStillRejected() {
        val unclear = uiText(TextKey.SMS_DATE_UNCLEAR)
        assertEquals(unclear, assertIs<SmsParseResult.Rejected>(sa("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: 2026-13-45", "2026-10-08T09:00:00Z")).reason)
        assertEquals(
            unclear,
            assertIs<SmsParseResult.Rejected>(eg("تم خصم 500.00 جم من بطاقة الخصم المباشر رقم 6604 عند TEST STORE يوم 13-45 الساعة 09:10", "2026-10-08T09:00:00Z")).reason,
        )
    }

    /**
     * مراجعة S1 — §77-C «لكل الأشكال اللي مفيهاش تاريخ» (سؤال (د) في §75.1 سمّى كاش باك البطاقة بالاسم): الرسالة اللي مفيهاش تاريخ بتاخد يوم
     * الوصول لو شكلها معروف **أو فيها عبارة عملية خلصت** («تم …») — في **البلدين بنفس القاعدة** (السعودية كانت للشكل المعروف بس، ومصر كانت
     * بتقبل بالعبارة). الشكل المجهول بيستنى تأكيد المالك. اللي من غير أي دليل بيفضل «التاريخ مش واضح» (اختيار Claude — سؤال مفتوح للمالك):
     * إعلان زي «Use your debit card … save EGP 50» مفيش حارس بيمسكه، وكان هيبقى «جاهز» و«سجّل الكل» يسجله.
     */
    @Test fun aDatelessMessageWithDoneWordingTakesTheArrivalDayInBothCountries() {
        val at = "2026-10-08T09:00:00Z"
        val unclear = uiText(TextKey.SMS_DATE_UNCLEAR)
        // كاش باك البطاقة (البحث saudi#120 و#121 — كانوا «التاريخ مش واضح»)
        val cashback = ok(sa("استرداد نقدي إلى البطاقة:\nتم استرداد و إضافة 15.00 ريال مبلغ الاسترداد النقدي إلى رصيد بطاقتك 4476", at), "cashback")
        assertEquals(listOf("2026-10-08", 1_500L, Direction.IN), listOf(cashback.date, cashback.amountMinor, cashback.direction))
        assertEquals(SmsKind.REFUND to SmsShape.KeywordFallback, cashback.kind to cashback.shape, "a refund waits for the owner anyway (§75-6)")
        val wallet = ok(sa("بطاقة ائتمانية استرجاع نقدي :\nتم إضافة 15.00 ريال إلى محفظة الاسترجاع النقدي لبطاقة SAMPLE GOLD", at), "cashback wallet")
        assertEquals("2026-10-08" to 1_500L, wallet.date to wallet.amountMinor)
        // مصر: نفس القاعدة — بالعبارة يوم الوصول ومستنية، ومن غيرها «التاريخ مش واضح»
        val egypt = ok(eg("تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER", at))
        assertEquals("2026-10-08" to SmsShape.KeywordFallback, egypt.date to egypt.shape)
        assertEquals(unclear, assertIs<SmsParseResult.Rejected>(eg("Use your debit card at TEST GROCER and save EGP 50", at)).reason)
        // السعودية من غير دليل: عنوان «Purchase» لوحده (كلمات عامة) · عنوان مش معروف ⇒ زي الأول
        assertEquals(unclear, assertIs<SmsParseResult.Rejected>(sa("Purchase\nبـSR 30\nلدى:TEST SHOP", at)).reason)
        assertEquals(unclear, assertIs<SmsParseResult.Rejected>(sa("رسالة من البنك\nشراء\nبـ64.25 SAR\nمن TEST GROCER\nمدى *6604", at)).reason)
        assertTrue(cashback.learnKey == null && egypt.learnKey == null, "an unknown layout is never learned")
    }

    /** مراجعة S1: الشكل المعروف من غير تاريخ وفيه ساعتين مختلفتين **بيستنى** (كان بيترفض لو مش على شكل الأهلي). */
    @Test fun aKnownLayoutWithTwoClocksAndNoDateWaitsInsteadOfBeingRejected() {
        val row = ok(sa("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\n09:10 11:40", "2026-10-08T09:00:00Z"))
        assertEquals("2026-10-08" to false, row.date to row.shape.clear)
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
