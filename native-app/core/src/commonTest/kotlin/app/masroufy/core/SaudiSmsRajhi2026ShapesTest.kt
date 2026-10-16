package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.BILL
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.TRANSFER_IN
import app.masroufy.core.SmsKind.TRANSFER_OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * r05 — أشكال الراجحي 2026 اللي كانت بتتفهم «من كلماتها بس» (بتستنى المالك كل مرة): «شراء PoS» · الكارت لازق في «عبر» («عبر6604;مدى») ·
 * المبلغ في العنوان («شراء انترنت بـSR 45.50» · «حوالة داخلية صادرة بـSR 900») · «مدفوعات وزارة الداخلية-المخالفات المرورية» ·
 * «استرجاع عملية». **كل الرسايل مخترعة** بنفس البنية (المحل TEST GROCER · الطرف «سامي التجريبي» · حسابك 1188 · الطرف 9021 · الكارت 6604).
 */
class SaudiSmsRajhi2026ShapesTest {
    private val ar = "سامي التجريبي"
    private val stamp = "26/3/5 09:10"

    private fun row(body: String): SmsRow = when (val r = parseBankSms(smsMessage(body, sender = "AlRajhiBank"), 1)) {
        is SmsParseResult.Ok -> r.row
        is SmsParseResult.Rejected -> fail("rejected «${r.reason}»: $body")
    }

    private fun known(body: String, template: String): SmsRow {
        val row = row(body)
        val shape = assertIs<SmsShape.KnownShape>(row.shape, "shape of: $body")
        assertEquals("alrajhi" to template, shape.bankFamily to shape.templateId, body)
        return row
    }

    private val cases = listOf(
        SmsCase("PoS, card glued to عبر, wallet", "شراء PoS\nعبر6604;مدى-سامسونج باي\nبـSR 45.50\nلـTEST GROCER\n$stamp", 4550, OUT, PURCHASE, merchant = "TEST GROCER"),
        SmsCase("PoS, card with colon, SAR", "شراء PoS\nعبر:6604;مدى-ابل باي\nبـSAR 45.50\nلـTEST GROCER\n$stamp", 4550, OUT, PURCHASE, merchant = "TEST GROCER"),
        SmsCase("online, amount in title", "شراء انترنت بـSR 45.50\nعبر6604;مدى\nمن1188\nلـTEST GROCER\n$stamp", 4550, OUT, PURCHASE, merchant = "TEST GROCER"),
        SmsCase("online BNPL charge (Tabby)", "شراء انترنت بـSR 250\nعبر6604;مدى\nمن1188\nلـTabby\n$stamp", 25000, OUT, PURCHASE, merchant = "Tabby"),
        SmsCase("internal out, amount in title", "حوالة داخلية صادرة بـSR 900\nمن1188\nلـ9021;$ar\n$stamp", 90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021"),
        SmsCase("local in, amount in title", "حوالة محلية واردة بـSR 900\nلـ1188\nمن9021;$ar\n$stamp", 90000, IN, TRANSFER_IN, party = ar, last4 = "9021"),
        SmsCase("local out + fee", "حوالة محلية صادرة بـSR 900\nمن1188\nلـ9021;$ar\nرسوم:SR 0.58\n$stamp", 90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021"),
        SmsCase("MOI traffic fine", "مدفوعات وزارة الداخلية-المخالفات المرورية\nمن:1188\nمبلغ:SR 300\nرقم الفاتورة:••••4455\n$stamp", 30000, OUT, BILL),
        SmsCase("refund «استرجاع عملية»", "استرجاع عملية\nبطاقة:6604;مدى\nمبلغ:45.50 SAR\nالتاجر: TEST GROCER\nفي:$stamp", 4550, IN, REFUND),
    )

    @Test
    fun readsEveryCaseRight() {
        for (c in cases) checkCase(c, { m, n -> parseBankSms(m.copy(sender = "AlRajhiBank"), n) }, Currency.SAR)
    }

    @Test
    fun theyAreKnownShapesNow() {
        known(cases[0].body, "purchase")
        known(cases[1].body, "purchase")
        known(cases[2].body, "purchase-online")
        known(cases[3].body, "purchase-online")
        known(cases[4].body, "transfer-out-internal")
        known(cases[5].body, "transfer-in-local")
        known(cases[6].body, "transfer-out-local").also { assertEquals(58L, it.fee?.amountMinor, "fee") }
        known(cases[7].body, "government-moi-service")
        known(cases[8].body, "refund-reversal")
    }

    @Test
    fun accountAndCardDigitsAreRead() {
        val online = row(cases[2].body)
        assertEquals("1188", online.accountLast4, "account")
        val pos = row(cases[0].body)
        assertEquals("6604", pos.ownLast4, "card")
    }

    @Test
    fun theSameShapeHasOneLearnKeyWhateverTheValues() {
        val a = row(cases[0].body).learnKey
        val b = row("شراء PoS\nعبر7319;مدى-سامسونج باي\nبـSR 1,210.00\nلـSAMPLE PHARMACY\n26/3/4 22:05").learnKey
        assertEquals(a, b)
    }

    /** الحماية زي ما هي: كلمة حالة أو سطر زيادة أو خدمة مش من القايمة ⇒ مش شكل معروف (بتستنى). */
    @Test
    fun guardsStillHold() {
        val waiting = listOf(
            "شراء PoS مؤجل\nعبر6604;مدى\nبـSR 45.50\nلـTEST GROCER\n$stamp",
            "شراء PoS\nعبر6604;مدى - تم الإلغاء\nبـSR 45.50\nلـTEST GROCER\n$stamp",
            "شراء PoS\nعبر6604;مدى\nبـSR 45.50\nلـTEST GROCER\nالمبلغ محجوز مؤقتًا\n$stamp",
            "شراء انترنت بـSR 45.50 بانتظار التأكيد\nعبر6604;مدى\nمن1188\nلـTEST GROCER\n$stamp",
            "مدفوعات وزارة الداخلية-خدمة تجريبية\nمن:1188\nمبلغ:SR 300\nرقم الفاتورة:••••4455\n$stamp",
            "حوالة داخلية صادرة بـSR 900 معلقة\nمن1188\nلـ9021;$ar\n$stamp",
        )
        for (body in waiting) {
            val r = parseBankSms(smsMessage(body, sender = "AlRajhiBank"), 1)
            if (r is SmsParseResult.Ok) assertFalse(r.row.shape.clear, "must wait: $body")
        }
    }
}
