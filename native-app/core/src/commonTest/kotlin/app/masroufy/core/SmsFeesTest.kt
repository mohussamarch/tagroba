package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * رسوم الحوالة والمحفظة في رسالة البنك (§77-B — `SmsFees.kt`): الرسوم + الضريبة المكتوبة عليها بعملة البلد، وهل مبلغ الصف فيه الرسوم.
 * **ما بنخترعش رقم** (قاعدة 10): عملة تانية · رقم مش مقروء · رسوم ورصيد مع بعض · «شاملة» · ضريبة لوحدها ⇒ مفيش رسوم.
 * القراية نفسها ما اتغيرتش (ملفات المرجع بتتأكد في `SmsGoldenTest`). كل الرسايل والأسامي والأرقام مخترعة.
 */
class SmsFeesTest {
    private val march = "2026-03-05T09:00:00Z"

    private fun sa(body: String, at: String = march) = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", at, body), 1), body).row

    private fun eg(body: String, at: String = march) = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("TESTBANK", at, body), 1), body).row

    @Test fun saudiTransferWithTotalDueGivesFeePlusVatInsideTheAmount() {
        val row = sa(SA_TOTAL_DUE)
        assertEquals(100_661L, row.amountMinor, "المبلغ = الإجمالي المستحق زي ما هو (قراية القارئ ما اتغيرتش)")
        assertEquals(OUT, row.direction)
        assertEquals(SmsFee(661, includedInAmount = true), row.fee, "5.75 رسوم + 0.86 ضريبة")
    }

    @Test fun saudiTransferWithAFeeLineOnTopOfTheAmount() {
        val row = sa(SA_FEE_ON_TOP)
        assertEquals(100_000L, row.amountMinor)
        assertEquals(SmsFee(575, includedInAmount = false), row.fee)
        // سطر «رسوم:» لوحده والمبلغ في السطر اللي بعده
        assertEquals(SmsFee(575, false), saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم:\nSAR 5.75", SmsKind.TRANSFER_OUT, 100_000))
        // بعد المبلغ على طول («SAR 5.75 fee») ونفس السطر (شكل ملف المرجع)
        assertEquals(SmsFee(575, false), saudiFeeOf("Transfer\nAmount: SAR 1,000.00\nSAR 5.75 fee", SmsKind.TRANSFER_OUT, 100_000))
        assertEquals(SmsFee(200, false), sa("شراء 25 SAR fees 2 SAR 2026-09-10", "2026-09-18T10:00:00Z").fee)
    }

    @Test fun vodafoneCashServiceFeeIsAFeeOutsideTheAmount() {
        val row = eg(VF_SEND)
        assertEquals(30_000L, row.amountMinor)
        assertEquals(SmsFee(100, includedInAmount = false), row.fee)
        // «مصاريف الخدمة وخصم 1.50 جنيه من محفظتك» = رقم واحد (ما بيتحسبش مرتين) · «تكلفة الخدمة»
        val written = "تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة وخصم 1.50 جنيه من محفظتك. رصيد حسابك في فودافون كاش الحالي 98.50"
        assertEquals(SmsFee(150, false), eg(written, "2026-10-08T12:00:00Z").fee)
        assertEquals(SmsFee(150, false), egyptFeeOf("تم تحويل 500 جنيه لرقم 01200009876 رصيدك الحالي 1,200 جنيه تكلفة الخدمة 1.50 جنيه", SmsKind.TRANSFER_OUT, 50_000))
    }

    @Test fun anExplicitWalletDebitOnAReceiveIsAFeeToo() {
        // «وخصم 7.50 من محفظتك» صريح إنه اتخصم من المحفظة (العملة = عملة المحفظة)
        val row = eg("تم استلام مبلغ 750.00 جنيه من رقم 01000000431 المسجل باسم TEST PERSON وخصم 7.50 من محفظتك")
        assertEquals(IN, row.direction)
        assertEquals(SmsFee(750, false), row.fee)
        // بس «رسوم» جنب مبلغ داخل ممكن تكون اتخصمت من المبلغ نفسه ⇒ مش واضح
        assertNull(egyptFeeOf("تم استلام 500 جنيه من رقم 01000000431 رسوم استلام 5 جنيه", SmsKind.TRANSFER_IN, 50_000))
    }

    @Test fun aFeeInAnotherCurrencyIsNeverAFee() {
        val usd = "حوالة صادرة\nمبلغ: SAR 100.00\nFee: USD 2.00\nإلى: TEST PERSON\nفي: 2026-03-05 09:10"
        assertIs<SmsParseResult.Rejected>(parseBankSms(BankSmsMessage("TESTBANK", march, usd), 1), "الرسالة نفسها أجنبية (§75-12) ⇒ مرفوضة ومستنية")
        assertNull(saudiFeeOf(usd, SmsKind.TRANSFER_OUT, 10_000))
        assertNull(egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 Fees: USD 2.00", SmsKind.TRANSFER_OUT, 30_000))
    }

    @Test fun noFeeLineNoFee() {
        assertNull(sa("شراء\nبـSR 25\nلدى:TEST CAFE\n26/10/07", "2026-10-07T10:00:00Z").fee)
        assertNull(eg("Your credit card ending with #4417 was charged for EGP 640.00 at PROBE MART on 07/10/2026", "2026-10-07T10:00:00Z").fee)
    }

    @Test fun ambiguousFeesAreNotInvented() {
        val t = SmsKind.TRANSFER_OUT
        // ضريبة لوحدها (ممكن تبقى ضريبة الشراء نفسه)
        assertNull(saudiFeeOf("شراء\nمبلغ: SAR 100.00\nVAT: SAR 15.00\nلدى: TEST SHOP", SmsKind.PURCHASE, 10_000))
        // الإجمالي = المبلغ (الرسوم برّه ولا جوه؟) · الإجمالي ≠ مبلغ الصف
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nإجمالي المبلغ المستحق: SAR 1,000.00", t, 100_000))
        assertNull(saudiFeeOf(SA_TOTAL_DUE, t, 100_000))
        // فلوس داخلة (مش واضح الرسوم اتخصمت من المبلغ ولا لوحدها) · «شاملة الرسوم» · رسوم ورصيد في نفس الكلام · رسوم من غير عملة
        assertNull(saudiFeeOf("حوالة واردة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.00", SmsKind.TRANSFER_IN, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,005.75 شاملة الرسوم\nرسوم: SAR 5.75", t, 100_575))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nالرصيد بعد خصم الرسوم 4,100.00 SAR", t, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nFees: 5.75", t, 100_000))
        assertNull(egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 رصيد حسابك الحالي 699", t, 30_000))
        // رسوم مقروءة وجنبها جزء مش مقروء ⇒ ولا جزء (رقم ناقص أسوأ من مفيش): ضريبة من غير عملة · رسوم بعملة تانية · رسوم ورصيد مع بعض
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nضريبة: 0.86", t, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nFee: USD 2.00", t, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nالرصيد بعد خصم الرسوم 4,100.00 SAR", t, 100_000))
        assertNull(egyptFeeOf("تم تحويل 500 جنيه لرقم 01200009876 تكلفة الخدمة 1.50 جنيه ورسوم تحويل 2", t, 50_000))
        // شحن الرصيد: المخصوم هو المبلغ، والضريبة ضريبة الشحن
        assertNull(eg("تم شحن رصيد موبايلك ب 50 بنجاح وخصم 57 من محفظتك شاملة الضريبة يوم 05/03/2026").fee)
        // صفر رسوم ⇒ مفيش عملية رسوم
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 0.00", t, 100_000))
    }

    @Test fun aFeeMessageItselfHasNoExtraFee() {
        val row = sa("Debit fees\nReason: Card replacement fee\nAmount: SAR 15.00\nFrom: STC Bank wallet\nDate: 2026-03-05 09:10:44")
        assertEquals(SmsKind.FEE, row.kind)
        assertEquals(1_500L, row.amountMinor)
        assertNull(row.fee, "المبلغ كله رسوم — `SmsFeeEffect` بيصنّفه")
    }

    companion object {
        /** حوالة بالإجمالي المستحق = المبلغ + الرسوم + الضريبة (شكل إس تي سي). */
        const val SA_TOTAL_DUE = "حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nضريبة القيمة المضافة: SAR 0.86\nإجمالي المبلغ المستحق: SAR 1,006.61\nإلى: TEST PERSON\nفي: 2026-03-05 09:10"

        /** حوالة محلية على شكل الراجحي: الرسوم سطر لوحدها برّه المبلغ. */
        const val SA_FEE_ON_TOP = "حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 1000\nالى:TEST PERSON\nالرسوم:SAR 5.75\n26/03/05 09:10"

        /** فودافون كاش: «مصاريف الخدمة 1 جنيه» برّه الـ300. */
        const val VF_SEND = "تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه رصيد حسابك فى فودافون كاش الحالي 699.\nتاريخ العملية: 09:10 26-03-05\nرقم العملية: 900000001"
    }
}
