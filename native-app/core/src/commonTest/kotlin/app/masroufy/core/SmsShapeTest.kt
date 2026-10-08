package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * الجولة الرابعة — **الشكل** (OVERRIDES §72: «المفهومة» بتتسجل لوحدها = شكل معروف): كل صف بيقول القارئ فهمه إزاي. القراية نفسها ما
 * اتغيرتش (ملف المرجع `golden/sms.json` بيمسكها)، والشكل علامة جنبها. القياس على كل أشكال البحث في `SmsTemplateCoverageTest` (device).
 * الرسايل مخترعة (TEST STORE · الكارت 6604 · الحساب 1188).
 */
class SmsShapeTest {
    private fun saudi(body: String) = assertIs<SmsParseResult.Ok>(parseBankSms(smsMessage(body), 1), body).row
    private fun egypt(body: String) = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(smsMessage(body), 1), body).row

    @Test fun keyIgnoresHamzaYaaTatweelDiacriticsColonsCaseAndSpaces() {
        assertEquals("سحب صراف الي", shapeKey("سحب:صراف آلي"))
        assertEquals("حوالة صادرة الي حسابك الاستثماري", shapeKey("  حوالة  صادرة إلى حسابك الاستثماري :"))
        assertEquals("اصدار شيك مصدق", shapeKey("إصدار شيك مصدّق"))
        assertEquals("تم ايداع الراتب", shapeKey("تم إيـداع الراتب،"))
        assertEquals("pos purchase", shapeKey("PoS Purchase"))
        assertEquals("2026-03-05 09:10: received", shapeKey("2026-03-05 09:10: Received", dropColons = false))
    }

    @Test fun catalogsHaveTheResearchedSize() {
        assertEquals(59, SAMA_TITLE_COUNT, "61 عنوان في التعميم − عنوانين الحجز")
        assertTrue(SAUDI_BANK_TITLE_COUNT >= 60, "قوالب البنوك السعودية: $SAUDI_BANK_TITLE_COUNT")
        assertTrue(EGYPT_SHAPE_COUNT >= 37, "قوالب مصر: $EGYPT_SHAPE_COUNT")
    }

    @Test fun samaTitleOnTheWholeFirstLineIsClear() {
        for (body in listOf(
            "شراء عبر نقاط البيع\nبطاقة: ***6604;mada(Apple Pay);\nمبلغ: SAR 64.25\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
            "PoS Purchase\nBy:6604;mada(Apple Pay)\nAmount:SR 64.25\nAt:TEST STORE\n26-03-05 09:10",
            "سحب:صراف آلي\nبطاقة:6604;مدى\nمبلغ:SAR 64.25\nمكان السحب:TEST ATM\nفي:26-03-05 09:10",
            "حوالة واردة\nمبلغ: 64.25 SAR\nمن: TEST PERSON\nفي: 2026-03-05 09:10",
        )) {
            val row = saudi(body)
            assertEquals(SmsShape.SamaTitle, row.shape, body)
            assertTrue(row.shape.clear)
        }
    }

    @Test fun bankTemplatesAreKnownShapes() {
        assertEquals(SmsShape.KnownShape("alrajhi", "purchase"), saudi("شراء\nبـSR 25\nلدى:TEST CAFE\n26/03/05").shape)
        assertEquals(SmsShape.KnownShape("alrajhi", "transfer-out-internal"), saudi("حوالة داخلية صادرة\nمن:1188\nمبلغ:SAR 500\nالى:TEST PERSON\nفي:26-03-05 09:35").shape)
        // حوالة الراجحي القديمة من غير كلمة اتجاه: الاتجاه من مكان الاسم = شكل معروف
        assertEquals(SmsShape.KnownShape("alrajhi", "transfer-undirected"), saudi("حوالة داخلية\nمبلغ:SAR 500\nالى:1188\nمن:TEST PERSON\nفي:26-03-05 09:35").shape)
        // الإنماء: سطر التحية قبل العنوان
        assertEquals(SmsShape.KnownShape("alinma", "salary"), saudi("هلا TEST NAME\nتم إيداع الراتب\nبمبلغ: 9000.00 SAR\nفي حساب: **1188\nفي: 2026-03-05 09:10\nمن خلال الإنماء").shape)
        assertEquals(SmsShape.KnownShape("stc", "purchase-card"), saudi("**6604 Purchase\nVia:6604\nAmount: 64.25 SAR\nFrom: TEST STORE\nAt: 2026-03-05 09:10").shape)
        // مصر: أول الجملة بهيكلها
        assertEquals(SmsShape.KnownShape("qnb", "debit-card"), egypt("Your Debit Card **6604 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40").shape)
        assertEquals(SmsShape.KnownShape("nbe", "purchase-debit-card"), egypt("تم خصم 500.00 EGP من بطاقة الخصم المباشر رقم6604 عندTEST STORE يوم03-05 الساعة09:10 المتاح1500.00EGP").shape)
        // رقم طويل اتقص في فلتر الجهاز ولزق في الكلمة اللي بعده («••••4567to») — لسه نفس الشكل
        assertEquals(
            SmsShape.KnownShape("vodafone-cash", "receive-en"),
            egypt("2026-03-05 09:10: Received EGP1,250.00 from ••••4567to Mobile Account Number 3307. Ref: ••••0427").shape,
        )
    }

    /** الاتجاه من كلمات عامة بس (القاعدة القديمة) ⇒ القراية زي ما هي بس **ما بتتسجلش لوحدها**. */
    @Test fun keywordOnlyMessagesAreReadButNotClear() {
        val saudiFallback = listOf(
            "Purchase\nAmount: SAR 64.25\nAt: TEST STORE\n2026-03-05 09:10", // «Purchase» لوحده مش عنوان بنك معروف
            "SNB: Purchase of SAR 64.25 at TEST STORE approved.\n2026-03-05", // جملة مش عنوان
            "مدفوعات\nبـSR 64.25\nلدى:TEST CAFE\n26/03/05",
            "سداد قسط السيارة\nمبلغ: SAR 64.25\nفي: 2026-03-05", // أول كلمة معروفة، بس السطر كله مش قالب
            "Spend alert\nPurchase\nAmount: SAR 64.25\nAt: TEST STORE\n2026-03-05 09:10",
            "حوالة داخلية\nمبلغ:SAR 64.25\nشراء\n26-03-05", // «حوالة داخلية» من غير اسم ⇒ الاتجاه من كلمة «شراء»
        )
        for (body in saudiFallback) {
            val row = saudi(body)
            assertEquals(SmsShape.KeywordFallback, row.shape, body)
            assertFalse(row.shape.clear, body)
        }
        val egyptFallback = listOf(
            "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST STORE يوم 05/03/2026",
            "Your debit card 6604 was charged EGP 500.00 at TEST STORE on 05/03/2026",
            "تم شحن محفظتك بمبلغ 200.00 جنيه يوم 05/03/2026",
        )
        for (body in egyptFallback) assertEquals(SmsShape.KeywordFallback, egypt(body).shape, body)
    }

    /** دفاع تاني: الشكل المعروف لازم اتجاهه = اتجاه القارئ — لو عكسه، الرسالة تستنى. */
    @Test fun aKnownShapeWithTheOtherDirectionIsNotClear() {
        assertEquals(SmsShape.SamaTitle, saudiShape("حوالة واردة\nمبلغ: 64.25 SAR", IN))
        assertEquals(SmsShape.KeywordFallback, saudiShape("حوالة واردة\nمبلغ: 64.25 SAR", OUT))
        assertEquals(SmsShape.KeywordFallback, saudiShape("شراء\nبـSR 25", IN))
        assertEquals(SmsShape.KeywordFallback, saudiShape("حوالة داخلية\nالى:1188\nمن:TEST PERSON", OUT), "الاسم في «من:» = وارد")
        assertEquals(SmsShape.KeywordFallback, egyptShape("IPN transfer received with amount of EGP 300.00 on 1234 on 30/07", OUT))
        // الملتبس في المصدر نفسه (سداد البطاقة) بيقبل أي اتجاه
        assertEquals(SmsShape.SamaTitle, saudiShape("بطاقة ائتمانية تسديد\nمبلغ: 64.25 SAR", IN))
        assertEquals(SmsShape.SamaTitle, saudiShape("بطاقة ائتمانية تسديد\nمبلغ: 64.25 SAR", OUT))
    }

    /** عناوين الحجز مش في القايمة عمدًا: لو الحارس فوّت واحدة، ما تتسجلش لوحدها. */
    @Test fun holdTitlesAreNeverClear() {
        assertEquals(SmsShape.KeywordFallback, saudiShape("بطاقة ائتمانية حجز مبلغ\nمبلغ: 64.25 SAR", OUT))
        assertEquals(SmsShape.KeywordFallback, saudiShape("Credit Card Cash Release\nAmount: SAR 64.25", IN))
    }

    @Test fun aRowMadeOutsideTheReadersIsNotClearByDefault() {
        val row = SmsRow(1, "2026-03-05", 100, OUT, "", null, "x", "", "")
        assertEquals(SmsShape.KeywordFallback, row.shape)
        assertFalse(row.shape.clear)
    }

    @Test fun shapeIsNotPartOfTheFileFingerprint() {
        val row = saudi("شراء\nبـSR 25\nلدى:TEST CAFE\n26/03/05")
        assertEquals(smsRowsJson(listOf(row)), smsRowsJson(listOf(row.copy(shape = SmsShape.KeywordFallback))))
    }
}
