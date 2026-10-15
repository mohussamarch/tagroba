package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * إجابة المالك 2026-10-15 (OVERRIDES §91-9): رسالة بنك كاتبة المبلغ بعلامة الريال الجديدة (U+20C1) — قبل الرقم أو بعده، بمسافة أو من
 * غيرها — بتتقري ريال سعودي **بالظبط** زي «ر.س»/SAR (نفس المبلغ ونفس وضوح الشكل). كل المبالغ والمحلات مخترعة.
 */
class SmsRiyalSignTest {
    private val sign = "⃁"

    private fun parse(body: String) = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", "2026-10-07T09:40:00Z", body), 1), body).row

    private fun purchase(amount: String) = "شراء\nبـ$amount\nلدى:TEST SHOP\n26/10/07 09:35"

    @Test fun theNewSignReadsLikeSarOnEitherSideWithOrWithoutASpace() {
        val sar = parse(purchase("SAR 35.62"))
        val written = listOf("$sign 35.62", "${sign}35.62", "35.62 $sign", "35.62$sign")
        for (w in written) {
            val row = parse(purchase(w))
            assertEquals(3_562L, row.amountMinor, w)
            assertEquals(sar.direction to sar.shape.clear, row.direction to row.shape.clear, w)
            assertEquals(sar.merchantName, row.merchantName, w)
        }
    }

    @Test fun theSignWithThousandsAndATransferReadsTheSameToo() {
        val sar = parse("حوالة محلية واردة\nمن:TEST PAYER\nبـSAR 1,250.00\n26/10/07")
        val row = parse("حوالة محلية واردة\nمن:TEST PAYER\nبـ$sign 1,250.00\n26/10/07")
        assertEquals(125_000L to sar.direction, row.amountMinor to row.direction)
        assertEquals(sar.shape.clear, row.shape.clear)
    }
}
