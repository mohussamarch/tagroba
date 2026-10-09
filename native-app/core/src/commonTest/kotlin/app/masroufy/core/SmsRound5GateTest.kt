package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * الجولة الخامسة — كل حماية من حمايات «الشكل الواضح» **لوحدها** (رسايل مخترعة مفيهاش كلمة يمسكها حارس تاني): لو حماية منهم اتشالت،
 * الاختبار ده بيفشل (التحوير `scratchpad/r5mutate.js` بيتأكد). القراية نفسها بتفضل زي ما هي (الرسالة بتتقري وبتستنى، مش بتترفض).
 */
class SmsRound5GateTest {
    private fun waitsReady(body: String, at: String = SMS_RECEIVED_AT, egypt: Boolean = false) {
        val r = if (egypt) parseEgyptBankSms(smsMessage(body, receivedAt = at), 1) else parseBankSms(smsMessage(body, receivedAt = at), 1)
        val row = assertIs<SmsParseResult.Ok>(r, body).row
        assertEquals(SmsShape.KeywordFallback, row.shape, body)
    }

    /** القايمة البيضا: سطر مش خانة معروفة ومفيهوش كلمة حارس ⇒ يستنى (القايمة السودا ما كانتش هتمسكه). */
    @Test fun anUnknownLineAfterAKnownTitleWaits() {
        waitsReady("Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: Under verification\n2026-03-05 10:42")
        waitsReady("سداد فاتورة\nالمبلغ: 230.40 ر.س\nالحالة: قيد التدقيق\n2026-03-05 10:42")
        waitsReady("Debit Transfer Local\nAmount: SAR 1,320.00\nTo: FAHAD SAMPLE\nTransfer will settle tomorrow\n2026-03-05 10:42")
        waitsReady("شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nنتيجة العملية: جزئية\n2026-03-05 11:20")
        // ونفس الرسالة من غير السطر ده ⇒ واضحة
        assertTrue((parseBankSms(smsMessage("شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20"), 1) as SmsParseResult.Ok).row.shape.clear)
    }

    /** تاريخين مختلفين في خانات تاريخ معروفة ⇒ يستنى (القارئ لسه بياخد الأول — ملف المرجع). */
    @Test fun twoDifferentDatesWait() {
        waitsReady("شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nفي: 2026-02-25\nفي: 2026-03-05")
        assertEquals(1, distinctDateCount("في: 2026-03-05\nفي: 2026-03-05"), "نفس التاريخ مرتين = واحد")
        assertEquals(2, distinctDateCount("تم خصم … يوم 03-05 الساعة 09:40 - تاريخ آخر كشف 28/02/2026"))
    }

    /** تاريخ بعد يوم الوصول (القارئ بيقبل لحد يوم بعده — ملف المرجع) ⇒ يستنى. */
    @Test fun aDateAfterTheArrivalDayWaits() {
        waitsReady("شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nفي: 2026-03-06 09:00")
        waitsReady("Your credit card ending with#7788 was charged for EGP 640.00 at TEST TOYS on 06/03/26 at 19:30.", egypt = true)
        assertEquals(SmsShape.SamaTitle, gateShape(SmsShape.SamaTitle, "x 2026-03-05", "2026-03-05", "2026-03-05", "x 2026-03-05"))
        assertEquals(SmsShape.KeywordFallback, gateShape(SmsShape.SamaTitle, "x 2026-03-06", "2026-03-06", "2026-03-05", "x 2026-03-06"))
    }

    /** كلمة شك جوه خانة حرة في قالب مصري (مفيش حارس بيمسكها) ⇒ يستنى. */
    @Test fun aDoubtWordInsideAnEgyptianFreeSlotWaits() {
        waitsReady("Your credit card ending with#7788 was charged for EGP 640.00 at TEST SHOP SUSPENDED on 05/03/26 at 19:30.", egypt = true)
        waitsReady("تم خصم 185.00 جم من بطاقة الخصم المباشر رقم 4455 عند المطعم القادم يوم 03-05 الساعة 14:30", egypt = true)
    }

    /** لاحقة العنوان: اسم محفظة بس (الأهلي) — «شراء نقاط بيع <أي كلام>» مش شكل معروف. */
    @Test fun titleSuffixesAreOnlyTheResearchedOnes() {
        waitsReady("شراء نقاط بيع TEST\nبـ87.40 SAR\nمن TEST BAKERY\nفي 11:20 05/03/26")
        assertTrue((parseBankSms(smsMessage("شراء نقاط بيع ApplePay\nبـ87.40 SAR\nمن TEST BAKERY\nفي 11:20 05/03/26"), 1) as SmsParseResult.Ok).row.shape.clear)
    }
}
