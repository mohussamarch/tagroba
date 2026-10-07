package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الطرف التاني في **رسايل البنك** (OVERRIDES §72): نفس الرسايل المخترعة اللي في `golden/sms.json` و`EgyptBankSms.kt` — بتعدّي على
 * المحلل الحقيقي الأول (القص والتنضيف) وبعدين بتتعمل عملية زي الاستيراد بالظبط (الوصف = نص الرسالة، من غير نوع عملية).
 */
class SmsTransferPartiesTest {
    private var seq = 0

    private fun recorded(body: String, egypt: Boolean = false): Transaction {
        val message = BankSmsMessage("TESTBANK", "2026-09-18T10:00:00Z", body)
        val row = (if (egypt) parseEgyptBankSms(message, 1) else parseBankSms(message, 1)) as SmsParseResult.Ok
        return Transaction(
            id = "t-${seq++}", occurredAt = row.row.date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false, observedDirection = row.row.direction, amountMinor = row.row.amountMinor, currency = Currency.SAR,
            categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x",
            updatedAt = "x", rawDescription = row.row.description, rawMerchantName = row.row.merchantName,
        )
    }

    @Test fun outgoingTransferPartyIsTheToLineNotYourOwnAccount() {
        val party = transferPartyOf(recorded("حوالة داخلية صادرة\nمن:1111\nإلى:TEST PERSON\nبـSR 500\n26/9/18 09:35"))!!
        assertEquals("TEST PERSON", party.label)
        assertNull(party.last4, "«من:1111» حسابك إنت — مش الطرف")
    }

    @Test fun incomingTransferPartyIsTheFromLine() {
        val party = transferPartyOf(recorded("حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111\n26/9/18"))!!
        assertEquals("TEST PERSON", party.label)
        assertEquals(
            transferPartyOf(recorded("حوالة داخلية صادرة\nمن:1111\nإلى:TEST PERSON\nبـSR 500\n26/9/18 09:35"))!!.key, party.key,
            "نفس الشخص رايح وجاي = نفس الطرف",
        )
    }

    @Test fun accountNumbersBecomeLastFourOnly() {
        val toAccount = transferPartyOf(recorded("حوالة داخلية صادرة\nالرصيد: 1500 SAR\nمبلغ:SAR 35.62\nإلى: 9999\n26/9/18 09:35"))!!
        assertEquals(TransferPartyRef("#9999", "••••9999", "9999"), toAccount)
        // الآيبان بيتقص قبل الحفظ (`redactSms`) ⇒ آخر 4 بس — ولا رقم كامل في المفتاح ولا الاسم
        val fromIban = transferPartyOf(recorded("حوالة واردة\nمن حساب SA0380000000608010167519\nبـSR 100\n26/9/18 09:35"))!!
        assertEquals("7519", fromIban.last4)
        assertTrue("0380000000608010167519" !in fromIban.key + fromIban.label)
    }

    @Test fun messagesWithoutACounterpartyHaveNoParty() {
        // QNB مصر: «from 1234» = حسابك، والرسالة مفيهاش الطرف أصلًا — كانت بتطلع «طرف» من أول الرسالة لحد التاريخ
        assertNull(transferPartyOf(recorded("IPN transfer sent with amount of EGP 300.00 from 1234 on 29/07 at 12:04 PM. Ref# ab12cd34.", egypt = true).copy(occurredAt = "2026-07-29")))
        assertNull(transferPartyOf(recorded("IPN transfer received with amount of EGP 250.00 on 1234 on 29/07 at 03:01 PM. Ref# ab12cd34.", egypt = true)))
        // صادرة من غير سطر «إلى» ⇒ ما بنخمّنش (ولا «في:26/09/16» تتقري اسم)
        assertNull(transferPartyOf(recorded("حوالة داخلية صادرة\nعبر1111;مدى\nSR 7\nلـTEST STORE\nفي:26/09/16\nالرصيد: 1500 SAR")))
        assertNull(transferPartyOf(recorded("حوالة صادرة 26/09/16\nبـSR 7")), "التاريخ مش «اسم/أرقام» بتاع الكشف")
        // شراء مش تحويل
        assertNull(transferPartyOf(recorded("شراء\nبـSR 25\nلدى:TEST CAFE\n26/9/18")))
    }

    @Test fun statementDescriptionsAreUntouched() {
        // كشف الراجحي (سطر واحد) لسه بيتقري بأنماط الكشف
        val statement = recorded("حوالة داخلية صادرة\nمن:1111\nإلى:TEST PERSON\nبـSR 500\n26/9/18 09:35")
            .copy(rawDescription = "W-/TOACCT/99998888777766665TOSAMI:ملاحظة", sourceOperationType = "عملية تحويل داخلية")
        assertEquals("SAMI", transferPartyOf(statement)!!.label)
    }
}
