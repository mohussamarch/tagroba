package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * مراجعة الشريحة S2: **كل رقم رسوم بيتحسب مرة** (`SmsFees.kt`). كانت الرسوم بتتجمع من كل رقم جنبه كلمة رسوم: نفس الرسوم بالعربي
 * وبالإنجليزي اتحسبت مرتين (1,000 + 11.50 بدل 5.75)، و«إجمالي الرسوم» اتجمع فوق أجزائه، ومصر «وخصم 2005 من محفظتك» على تحويل
 * 2000 اتحسبت رسوم 2005. كل الرسايل والأسامي والأرقام مخترعة.
 */
class SmsFeesOnceTest {
    private val october = "2026-10-07T10:00:00Z"
    private val out = SmsKind.TRANSFER_OUT

    private fun sa(body: String) = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", october, body), 1), body).row

    private fun eg(body: String) = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("TESTBANK", october, body), 1), body).row

    @Test fun theSameFeeInTwoLanguagesCountsOnce() {
        // شكل الراجحي المعروف ⇒ كان بيتسجل لوحده برسوم 11.50 والبنك −1,011.50
        val rajhi = sa("حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 1000\nالى:TEST PERSON\nالرسوم:SAR 5.75\nFees: SAR 5.75\n26/10/07 09:10")
        assertEquals(SmsShape.KnownShape("alrajhi", "transfer-out-local"), rajhi.shape)
        assertEquals(SmsFee(575, includedInAmount = false), rajhi.fee)
        // رسالة بلغتين بعنوان موحّد: القارئ بيقرا المبلغ مرة (§72.5-10)، والرسوم كمان مرة — ومعاها الضريبة
        val bilingual = "حوالة صادرة محلية\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nإلى: TEST PERSON\nDebit Transfer Local\nAmount: SAR 1,000.00\nFee: SAR 5.75\nTo: TEST PERSON\n2026-10-07 09:10"
        assertEquals(100_000L to SmsFee(575, false), sa(bilingual).let { it.amountMinor to it.fee })
        val withVat = "حوالة صادرة محلية\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nضريبة القيمة المضافة: SAR 0.86\nإلى: TEST PERSON\n" +
            "Debit Transfer Local\nAmount: SAR 1,000.00\nFee: SAR 5.75\nVAT: SAR 0.86\nTo: TEST PERSON\n2026-10-07 09:10"
        assertEquals(SmsFee(661, false), sa(withVat).fee)
        // لغتين من غير رقم مشترك = بندين مختلفين (رسوم بالعربي وضريبتها بالإنجليزي)
        assertEquals(SmsFee(661, false), saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nVAT: SAR 0.86", out, 100_000))
    }

    @Test fun aTotalFeesLineIsTheSumNotAnExtraFee() {
        val parts = "حوالة صادرة\nمبلغ: SAR 1,000.00\nالرسوم: SAR 5.00\nضريبة القيمة المضافة: SAR 0.75\nإجمالي الرسوم: SAR 5.75\nإلى: TEST PERSON\nفي: 2026-10-07 09:10"
        assertEquals(SmsFee(575, false), sa(parts).fee)
        assertEquals(SmsFee(575, false), saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nTotal fees: SAR 5.75", out, 100_000), "الإجمالي لوحده")
        assertEquals(SmsFee(575, false), saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nإجمالي الرسوم:\nSAR 5.75", out, 100_000), "اللابل في سطر لوحده")
        // الأجزاء مش = الإجمالي · ضريبة من غير رسوم جنب الإجمالي · إجماليين مختلفين · إجمالي من غير عملة ⇒ مش عارفين
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nالرسوم: SAR 5.00\nإجمالي الرسوم: SAR 5.75", out, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nVAT: SAR 0.75\nTotal fees: SAR 5.75", out, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nإجمالي الرسوم: SAR 5.75\nTotal fees: SAR 6.00", out, 100_000))
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nالرسوم: SAR 5.00\nإجمالي الرسوم: 5.75", out, 100_000))
        // نفس الإجمالي باللغتين = مرة
        assertEquals(SmsFee(575, false), saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nإجمالي الرسوم: SAR 5.75\nTotal fees: SAR 5.75", out, 100_000))
    }

    @Test fun aFeeRepeatedInOneLanguageIsNotInvented() {
        // رقمين رسوم بنفس القيمة بنفس اللغة: رسوم واحدة متكررة ولا رسمين؟ مش عارفين ⇒ مفيش رسوم
        assertNull(sa("حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nرسوم التحويل: SAR 5.75\nإلى: TEST PERSON\nفي: 2026-10-07 09:10").fee)
        // لغتين فيهم رقم مشترك وبند زيادة في واحدة بس ⇒ مش متطابقين
        assertNull(saudiFeeOf("حوالة صادرة\nمبلغ: SAR 1,000.00\nالرسوم: SAR 5.75\nFees: SAR 5.75\nVAT: SAR 0.86", out, 100_000))
    }

    @Test fun egyptWalletDebitAtLeastTheAmountIsTheTotal() {
        // «وخصم 2005 من محفظتك» على تحويل 2000 = المخصوم كله ⇒ المصاريف 5 (كانت 2005 والمحفظة −4,005)
        assertEquals(200_000L to SmsFee(500, false), eg("تم تحويل 2000 جنيه لرقم 01000001212 وخصم 2005 من محفظتك").let { it.amountMinor to it.fee })
        assertEquals(SmsFee(100, false), eg("تم تحويل 300 جنيه لرقم 01000001212 وخصم 301 من محفظتك. رصيد حسابك في فودافون كاش الحالي 98.50").fee)
        // المخصوم = المبلغ ⇒ مفيش مصاريف
        assertNull(eg("تم تحويل 300 جنيه لرقم 01000001212 وخصم 300 من محفظتك").fee)
        // المصاريف المكتوبة والإجمالي متفقين ⇒ مرة · مش متفقين ⇒ مش عارفين
        assertEquals(SmsFee(100, false), eg("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه وخصم 301 من محفظتك").fee)
        assertNull(egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه وخصم 305 من محفظتك", out, 30_000))
        assertNull(egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه وخصم 300 من محفظتك", out, 30_000))
        // نفس المصاريف مكتوبة مرتين (اسمها ورقم المخصوم) ⇒ مرة · مختلفين ⇒ مش عارفين
        assertEquals(SmsFee(100, false), egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه وخصم 1 جنيه من محفظتك", out, 30_000))
        assertNull(egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه وخصم 2 جنيه من محفظتك", out, 30_000))
        // «وخصم» مرتين بقيمتين · استلام اتخصم من المحفظة قده ⇒ مش مفهوم
        assertNull(egyptFeeOf("تم تحويل 300 جنيه لرقم 01000001212 وخصم 1 من محفظتك وخصم 2 من محفظتك", out, 30_000))
        assertNull(egyptFeeOf("تم استلام مبلغ 750.00 جنيه من رقم 01000000431 وخصم 750 من محفظتك", SmsKind.TRANSFER_IN, 75_000))
    }
}
