package app.masroufy.core

import kotlin.test.Test

/** smoke 0.5.4 ملاحظة 1 — «حوالة محلية صادرة» باسم لاتيني (مخترع): الطرف = الاسم + آخر 4. */
class SmokeOutgoingPartyTest {
    @Test fun latinBeneficiaryOnALocalOutgoingTransferIsTheParty() {
        val body = "حوالة محلية صادرة\nمصرف:ANB\nمن:0000\nمبلغ:SAR 300\nالى:QA PERSON Q\nالى:7788\nالرسوم:SAR 0.58\n26-03-05 09:10"
        checkCase(SmsCase("smoke local out latin", body, 30000, Direction.OUT, SmsKind.TRANSFER_OUT, party = "QA PERSON Q", last4 = "7788"), ::parseBankSms, Currency.SAR)
    }
}
