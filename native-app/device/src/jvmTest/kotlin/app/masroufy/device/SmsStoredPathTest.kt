package app.masroufy.device

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsForeignAmount
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة التالتة من مراجعة قارئ الرسايل — **نفس طريق التسجيل التلقائي** (§72): `SmsSafety.sanitize` قبل الحفظ ⇒ قارئ كل بلد على
 * النص المحفوظ (المراجع لقى رسايل «اتسجلت لحد الآخر» من الطريق ده). الرسايل مخترعة (TEST STORE · 6604 · 1188 · مبالغ مخترعة).
 * الجداول الكبيرة على النص الأصلي في `core` (`SmsAdversarialCases`)؛ هنا عينة من كل نوع **بعد الحفظ** (الحجب ممكن يغيّر النص).
 */
class SmsStoredPathTest {
    private val received = "2026-03-05T06:10:30Z"

    private fun read(country: String, stored: String): SmsParseResult =
        (if (country == "SA") ::parseBankSms else ::parseEgyptBankSms)(BankSmsMessage("TESTBANK", received, stored), 1)

    private fun stored(body: String): String = assertNotNull(SmsSafety.sanitize(body), "dropped before storage: $body")

    /** §75-12: الأجنبي بأي كتابة بيتحفظ، وبيستنى في بلده بس ومعاه المبلغ الأجنبي والاقتراح المحلي — حتى الرقم الأجنبي الطويل ما بيتحجبش. */
    @Test fun foreignPurchasesWaitAfterStorage() {
        val cases = listOf(
            Triple("SA", "Online Purchase\nAmount: \$23.40 (SAR 87.75)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("USD", 2340) to 8775L),
            Triple("SA", "Purchase\nJPY 45000 (SAR 1,125.00)\nAt TEST STORE\n2026-03-05 09:10", SmsForeignAmount("JPY", 45000) to 112500L),
            Triple("SA", "Purchase\nAmount: \$12,500.00 (SAR 46,875.00)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("USD", 1250000) to 4687500L),
            Triple("SA", "شراء دولي\nبطاقة: *6604\nمبلغ: 300 يوان\n(156.20 ريال)\nلدى: TEST STORE\nفي: 2026-03-05 09:10", SmsForeignAmount("CNY", 30000) to 15620L),
            Triple("SA", "International Online Purchase\nAmount: EGP 500.00 (SAR 37.50)\nCard: *6604 - VISA (Ecommerce)\nAt: TEST STORE\nOn: 05/03/2026 09:10", SmsForeignAmount("EGP", 50000) to 3750L),
            Triple("EG", "Your credit card 6604 was charged \$15.00 (EGP 720.00) at TEST STORE on 05/03/2026", SmsForeignAmount("USD", 1500) to 72000L),
            Triple("EG", "تم خصم 4,500 ين من بطاقتك 6604 عند TEST STORE يوم 05/03/2026 بما يعادل 1,450 جم", SmsForeignAmount("JPY", 4500) to 145000L),
        )
        for ((country, body, expected) in cases) {
            val text = stored(body)
            val own = read(country, text)
            val pending = (own as? SmsParseResult.Rejected)?.foreign ?: throw AssertionError("[$country] not waiting: $own — $text")
            assertEquals(expected.first, pending.foreign, text)
            assertEquals(expected.second, pending.localSuggestion, text)
            val other = read(if (country == "SA") "EG" else "SA", text)
            assertTrue(other is SmsParseResult.Rejected && other.foreign == null, "other lane booked or waits too: $other — $text")
        }
    }

    /** رمز · حجز · ما اكتملتش · خصم جاي · رصيد بس · عرض (المراجع: «اتسجلت لحد الآخر») ⇒ ما بتتحفظش أصلًا. */
    @Test fun nonTransactionsNeverReachStorage() {
        val dropped = listOf(
            "الرمز المؤقت 482913\nشراء عبر الإنترنت\nمبلغ: 64.25 ر.س\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
            "Use 482913 to authenticate your purchase of EGP 500.00 at TEST STORE on 05/03/2026",
            "شراء\nمبلغ محجوز: 500.00 ر.س\nلدى: TEST HOTEL\nفي: 2026-03-05 09:10",
            "لم تكتمل عملية الشراء\nمبلغ: SAR 64.25\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
            "سوف يتم خصم مبلغ 230.00 ريال من حسابك 1188 لسداد فاتورة TEST POWER بتاريخ 2026-03-05",
            "سوف يتم خصم 500.00 جم من بطاقتك 6604 لدى TEST STORE يوم 05/03/2026",
            "الرصيد المتاح بعد عملية الشراء\n4,100.00 ر.س\nحساب: *1188\n2026-03-05 09:10",
            "Win EGP 1,000 with every purchase using your credit card from 01/03/2026",
        )
        for (body in dropped) assertNull(SmsSafety.sanitize(body), body)
    }

    /** ملتبسة بتتحفظ وبتستنى (أي رفض) — بس ما تتسجلش في أي بلد. */
    @Test fun ambiguousMessagesAreStoredButNotBooked() {
        val ambiguous = listOf(
            "ATM Withdrawal Reversal\nAmount: SAR 500.00\nATM: TEST ATM 01\n2026-03-05 09:10",
            "Salary Reversal\nAmount: SAR 12,000.00\nAccount: *1188\n2026-03-05",
            "Purchase Cancelled\nAmount: SAR 64.25\nAt: TEST GROCER\nRefund\n2026-03-05 09:10",
            "إيداع\nحساب المواطن\nمبلغ: 1,200.00 ر.س\nبتاريخ: 1447/09/16هـ",
            "Purchase\nAmount 64.25\nRef SR4821\nAt TEST STORE\n2026-03-05 09:10",
            "Cheque no. 4417 for EGP 5,000.00 deposited on 05/03/2026 was returned unpaid",
            "تم تحويل مبلغ 500 جم من حسابك رقم 1188 إلى حسابك رقم 2277 يوم 05/03/2026",
            "شراء\nمبلغ: 1'234.50 ر.س\nلدى: TEST STORE\n2026-03-05",
        )
        for (body in ambiguous) {
            val text = stored(body)
            for (country in listOf("SA", "EG")) assertFalse(read(country, text) is SmsParseResult.Ok, "[$country] booked: $text")
        }
    }

    /** عملية حقيقية فيها سطر تحذير أو إعلان بتتحفظ وبتتسجل بمبلغها. */
    @Test fun realTransactionsWithFootersAreStoredAndBooked() {
        val real = listOf(
            "SA" to "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nللاعتراض على العملية اتصل 8001110000",
            "SA" to "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nIf you have not authorized this transaction call 8001110000",
            "SA" to "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nاستمتع بخدماتنا",
            "EG" to "Your debit card 6604 was charged EGP 500.00 at TEST GROCER on 05/03/2026. Not authorised by you? Call 19000",
        )
        for ((country, body) in real) {
            val r = read(country, stored(body))
            assertTrue(r is SmsParseResult.Ok, "[$country] not booked: $r — $body")
        }
    }

    /** «Ref SR 48213» رقم مرجع: بيتحجب زي أي رقم طويل، والمبلغ الحقيقي جنب عملته ما بيتحجبش. */
    @Test fun aReferenceAfterRefIsRedactedNotKeptAsMoney() {
        val text = stored("Purchase\nAmount: SAR 64.25\nRef SR 48213\nAt: TEST STORE\n2026-03-05 09:10")
        assertTrue("SAR 64.25" in text, text)
        assertFalse("48213" in text, text)
        assertTrue("••••8213" in text, text)
    }
}
