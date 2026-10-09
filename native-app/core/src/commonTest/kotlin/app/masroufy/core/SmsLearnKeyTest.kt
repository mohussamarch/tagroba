package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §77-A «وضع التعلّم» — بصمة **شكل** الرسالة (`SmsLearnKeys.kt`): نفس القالب بمحل ومبلغ وتاريخ واسم وكارت ومرجع تانيين ⇒ نفس البصمة؛
 * كلمة ثابتة اتغيّرت (العنوان · اللابل · جملة التحذير · الكلمة البديلة في قالب مصر) ⇒ بصمة تانية؛ الكلمات العامة ⇒ مفيش بصمة؛ والبصمة
 * ما فيهاش ولا حرف من نص الرسالة. كل الرسايل مخترعة.
 */
class SmsLearnKeyTest {
    private val sa = "2026-10-08T09:00:00Z"
    private val eg = "2026-10-08T09:00:00Z"

    private fun saKey(body: String, at: String = sa): String {
        val row = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", at, body), 1), body).row
        assertTrue(row.shape.clear, "${row.shape.wire}: $body")
        return row.learnKey ?: error("no key: $body")
    }

    private fun egKey(body: String, at: String = eg): String {
        val row = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("TESTBANK", at, body), 1), body).row
        assertTrue(row.shape.clear, "${row.shape.wire}: $body")
        return row.learnKey ?: error("no key: $body")
    }

    // ── السعودية ──

    @Test fun samaTitleKeepsItsKeyAcrossValuesAndChangesWithTheTitleWording() {
        val a = saKey("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: 2026-10-07 09:10")
        val b = saKey("PoS Purchase\nAmount: SAR 1,250.40\nAt: OTHER SAMPLE MART\nOn: 2026-10-05 21:44")
        assertEquals(a, b, "merchant, amount, date and time are values")
        assertNotEquals(a, saKey("Online Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: 2026-10-07 09:10"), "title wording")
        assertNotEquals(a, saKey("PoS Purchase\nAmount: SAR 25.00\nMerchant: TEST CAFE\nOn: 2026-10-07 09:10"), "label")
    }

    @Test fun rajhiPurchaseIgnoresCardMerchantAndAmount() {
        val a = saKey("شراء\nعبر:6604;مدى\nبـSAR 64.25\nلدى:TEST CAFE\n26/10/7 09:10")
        val b = saKey("شراء\nعبر:1188;مدى\nبـSAR 7\nلدى:SAMPLE BAKERY 2\n26/10/5 23:01")
        assertEquals(a, b, "card, amount, merchant and date are values")
        assertNotEquals(a, saKey("شراء\nعبر:6604;مدى\nبـSAR 64.25\nعند:TEST CAFE\n26/10/7 09:10"), "merchant label «لدى» ⇒ «عند»")
    }

    @Test fun rajhiTransferIgnoresTheNameAndAccountDigits() {
        val a = saKey("حوالة داخلية صادرة\nمن:1111\nإلى:TEST PERSON\nبـSR 500\n26/10/07 09:35")
        val b = saKey("حوالة داخلية صادرة\nمن:2222\nإلى:OTHER SAMPLE\nبـSR 75.50\n26/10/06 18:02")
        assertEquals(a, b)
        assertNotEquals(a, saKey("حوالة داخلية واردة\nمن:TEST PERSON\nإلى:1111\nبـSR 500\n26/10/07 09:35"), "another template")
    }

    @Test fun snbLayoutWithAndWithoutTheClockLineAreDifferentLayouts() {
        val a = saKey("شراء نقاط بيع\nبـ48.60 SAR\nمن QUOLL BAKERY\nمدى *3906")
        assertEquals(a, saKey("شراء نقاط بيع\nبـ1,200.00 SAR\nمن TEST MART\nمدى *1234"), "SNB template — card and merchant are values")
        assertNotEquals(a, saKey("شراء نقاط بيع\nبـ48.60 SAR\nمن QUOLL BAKERY\n09:10\nمدى *3906"), "an extra line is another layout")
    }

    @Test fun aDifferentFooterSentenceIsADifferentLayoutButTheNumberInItIsNot() {
        val base = "PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: 2026-10-07 09:10\n"
        val a = saKey(base + "للاعتراض على العملية اتصل 8000000001")
        assertEquals(a, saKey(base + "للاعتراض على العملية اتصل 9200000002"), "the phone number is a value")
        assertNotEquals(a, saKey(base + "للاستفسار اتصل 8000000001"), "footer sentence")
        assertNotEquals(a, saKey(base.trimEnd()), "no footer")
    }

    @Test fun d360BankNameAfterTheColonIsAValue() {
        val a = saKey("Incoming Transfer: Riyad Bank\nAmount: SAR 900.00\nFrom: TAREQ PROBE\nOn: 2026-10-07")
        assertEquals(a, saKey("Incoming Transfer: Alinma Bank\nAmount: SAR 15.00\nFrom: OTHER SAMPLE\nOn: 2026-10-05"))
    }

    // ── مصر ──

    @Test fun qnbDebitCardIgnoresCardAmountMerchantAndBalance() {
        val a = egKey("Your Debit Card **1234 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40")
        assertEquals(a, egKey("Your Debit Card **9876 had a Successful transaction of EGP 1,250.00 @OTHER SAMPLE SHOP,your available bal.EGP 3,000.00"))
    }

    @Test fun nbeCardKeepsItsKeyAndAnAlternativeCurrencyWordIsADifferentLayout() {
        val a = egKey("تم خصم 500.00 جم من بطاقة الخصم المباشر رقم 6604 عند TEST STORE يوم 10-05 الساعة 09:10")
        assertEquals(a, egKey("تم خصم 75.25 جم من بطاقة الخصم المباشر رقم 1188 عند OTHER SAMPLE MART يوم 10-07 الساعة 23:41"))
        assertNotEquals(a, egKey("تم خصم 500.00 جنيه من بطاقة الخصم المباشر رقم 6604 عند TEST STORE يوم 10-05 الساعة 09:10"), "«جم» ⇒ «جنيه»")
    }

    @Test fun vodafoneCashIgnoresValuesAndChangesWithThePromoLine() {
        val a = egKey("تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 2026-10-07 10:00 رقم العملية: 900000613")
        assertEquals(a, egKey("تم سحب 75 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 1,240 جنيه. تاريخ العملية: 2026-10-05 21:12 رقم العملية: 900000999"))
        assertNotEquals(
            a,
            egKey("تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 2026-10-07 10:00 رقم العملية: 900000613 تابع مصروفاتك"),
            "promo line",
        )
    }

    @Test fun vodafoneCashClockOnlyMessagesShareALayout() {
        val a = egKey("23:58: Received EGP320.00 from 00201001112244 to Mobile Account Number 01009998877. Ref: 900000441123 Available Balance: 820.00", "2026-10-08T21:03:00Z")
        val b = egKey("14:22: Received EGP150.00 from 00201001112297 to Mobile Account Number 01009998877. Ref: 900000441128 Available Balance: 650.00", "2026-10-08T11:23:00Z")
        assertEquals(a, b, "the clock, the amount, the phone and the reference are values")
    }

    @Test fun nbeTransferIgnoresTheSenderName() {
        val a = egKey("تم اضافة تحويل لحظي لحسابكم رقم 2277 بمبلغ 60 جم من سامر التجريبي رقم مرجعي 900000563 يوم 10-07")
        assertEquals(a, egKey("تم اضافة تحويل لحظي لحسابكم رقم 9911 بمبلغ 1,500 جم من هالة مثال رقم مرجعي 900000777 يوم 10-05"))
    }

    // ── بدون بصمة ──

    @Test fun keywordFallbackHasNoKeyInEitherCountry() {
        val sa = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", sa, "Purchase\nبـSR 30\nلدى:TEST SHOP\n26/10/07"), 1)).row
        assertEquals(SmsShape.KeywordFallback, sa.shape)
        assertNull(sa.learnKey)
        val egRow = assertIs<SmsParseResult.Ok>(
            parseEgyptBankSms(BankSmsMessage("TESTBANK", eg, "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 07/10/2026"), 1),
        ).row
        assertEquals(SmsShape.KeywordFallback, egRow.shape)
        assertNull(egRow.learnKey)
        assertNull(saudiLearnKey("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE", SmsShape.KeywordFallback), "not clear ⇒ never a key")
    }

    @Test fun theKeyIsAHashNotMessageText() {
        val key = saKey("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: 2026-10-07 09:10")
        assertTrue(Regex("^[0-9A-F]{16}$").matches(key), key)
        val egyptKey = egKey("Your Debit Card **1234 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40")
        assertTrue(Regex("^[0-9A-F]{16}$").matches(egyptKey), egyptKey)
        assertNotEquals(key, egyptKey)
    }

    @Test fun maskingHidesEveryValueKind() {
        assertEquals("on <date> at <time>", maskLayoutValues("on sep 14, 2026 at 2:22:05 pm"))
        assertEquals("#بتاريخ#<time>", maskLayoutValues("••••0427 بتاريخ 07/10/2026 23:58"))
        assertEquals("كارت#رصيد#", maskLayoutValues("كارت xx6618 رصيد 1,250.00"))
        assertEquals(maskLayoutValues("bal.egp174.40"), maskLayoutValues("bal.egp 3,000.00"), "a space next to a number is not a layout")
    }
}
