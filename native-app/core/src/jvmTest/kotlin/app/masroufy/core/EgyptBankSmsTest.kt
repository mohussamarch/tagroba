package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * قارئ رسايل بنوك مصر (OVERRIDES §40.3).
 * ⚠️ **كل الرسايل هنا مخترعة** بنفس شكل QNB — مفيش أي رقم ولا اسم ولا مرجع من حساب المالك.
 * مفيش ملف مرجع (golden) للقارئ ده: التطبيق الحالي ما بيقراش رسايل مصرية أصلًا.
 */
class EgyptBankSmsTest {
    private fun sms(body: String, receivedAt: String = "2026-08-01T05:50:00.000Z") =
        BankSmsMessage(sender = "QNB EGYPT", receivedAt = receivedAt, body = body)

    private fun ok(body: String, receivedAt: String = "2026-08-01T05:50:00.000Z"): SmsRow =
        when (val r = parseEgyptBankSms(sms(body, receivedAt), 1)) {
            is SmsParseResult.Ok -> r.row
            is SmsParseResult.Rejected -> fail("المفروض تتقرا، بس اترفضت: ${r.reason}")
        }

    private fun rejected(body: String, receivedAt: String = "2026-08-01T05:50:00.000Z"): String =
        when (val r = parseEgyptBankSms(sms(body, receivedAt), 1)) {
            is SmsParseResult.Rejected -> r.reason
            is SmsParseResult.Ok -> fail("المفروض تترفض، بس اتقرت: ${r.row}")
        }

    @Test
    fun `تحويل صادر بالجنيه`() {
        val row = ok("IPN transfer sent with amount of EGP 300.00 from 1234 on 30/07 at 05:48 AM. Ref# aaaa1111. For more details call 19700.")
        assertEquals(30_000, row.amountMinor)
        assertEquals(Direction.OUT, row.direction)
        assertEquals("2026-07-30", row.date)
        // التحويل مفيهوش اسم الطرف التاني في الرسالة — بييجي من الكشف (زون التحويلات، §39)
        assertEquals("", row.merchantName)
        assertEquals("QNB EGYPT", row.sourceName)
    }

    @Test
    fun `تحويل وارد بالجنيه`() {
        val row = ok("IPN transfer received with amount of EGP 2680.22 on 1234 on 29/07 at 03:14 PM. Ref# bbbb2222.")
        assertEquals(268_022, row.amountMinor)
        assertEquals(Direction.IN, row.direction)
        assertEquals("2026-07-29", row.date)
    }

    @Test
    fun `شراء بالبطاقة - التاجر من علامة آت والرصيد مش مبلغ العملية`() {
        val row = ok(
            "Your Debit Card **1234 had a Successful transaction of EGP 41.25 @sample-store.com,your available bal.EGP174.40 for lost/stolen card call 19700",
            receivedAt = "2026-01-18T02:24:00.000+02:00",
        )
        assertEquals(4_125, row.amountMinor)
        assertEquals(Direction.OUT, row.direction)
        assertEquals("sample-store.com", row.merchantName)
        // رسالة البطاقة مفيهاش تاريخ ⇒ تاريخ وصولها زي ما هو مكتوب
        assertEquals("2026-01-18", row.date)
    }

    @Test
    fun `الكسور بتتحسب صح لغاية القرش`() {
        val row = ok("IPN transfer received with amount of EGP 43780.58 on 1234 on 29/07 at 03:01 PM. Ref# cccc3333.")
        assertEquals(4_378_058, row.amountMinor)
    }

    @Test
    fun `رمز التحقق والعرض والعملية المرفوضة بيترفضوا بسبب مكتوب`() {
        assertEquals(uiText(TextKey.SMS_SENSITIVE), rejected("Your OTP is 123456. Do not share it with anyone."))
        assertEquals(uiText(TextKey.SMS_OFFER), rejected("Special offer: transaction of EGP 100.00 will be free this month."))
        assertEquals(
            uiText(TextKey.SMS_DECLINED),
            rejected("Your Debit Card **1234 transaction of EGP 55.00 @sample-store.com was declined"),
        )
    }

    @Test
    fun `العملة اللي مش جنيه بتترفض`() {
        assertEquals(
            uiText(TextKey.SMS_NOT_EGP),
            rejected("Your Debit Card **1234 had a Successful transaction of USD 20.00 @sample-store.com"),
        )
    }

    @Test
    fun `الاتجاه الغامض والمبلغ الغامض بيترفضوا`() {
        assertEquals(uiText(TextKey.SMS_DIRECTION_UNCLEAR), rejected("Your account had activity of EGP 10.00 today."))
        assertEquals(
            uiText(TextKey.SMS_AMOUNT_UNCLEAR),
            rejected("IPN transfer sent from 1234 on 30/07 at 05:48 AM. Ref# dddd4444."),
        )
    }

    @Test
    fun `أكتر من مبلغ عملية في الرسالة بيترفض`() {
        assertEquals(
            uiText(TextKey.SMS_MULTIPLE_AMOUNTS),
            rejected("IPN transfer sent with amount of EGP 300.00 and amount of EGP 400.00 from 1234 on 30/07."),
        )
    }

    @Test
    fun `التاريخ الغامض بيترفض بدل ما يتخمن`() {
        // 30/07 بعيد أكتر من 60 يوم عن وصول الرسالة ⇒ مفيش سنة مقبولة
        assertEquals(
            uiText(TextKey.SMS_DATE_UNCLEAR),
            rejected(
                "IPN transfer sent with amount of EGP 300.00 from 1234 on 30/07 at 05:48 AM.",
                receivedAt = "2026-12-01T05:50:00.000Z",
            ),
        )
    }

    @Test
    fun `حزمة مصر بتقول ناقصها إيه`() {
        val egypt = countryPack("EG")
        assertEquals(Currency.EGP, egypt.currency)
        assertEquals(EgyptBankSmsReader, egypt.smsReader)
        assertTrue(!egypt.ready, "حزمة مصر لسه ناقصة، المفروض تقول كده")
        assertEquals(listOf("categoryTree", "statementReader"), egypt.gaps)
        assertTrue(SAUDI_PACK.ready, "حزمة السعودية المفروض جاهزة")
    }
}
