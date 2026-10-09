package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.OWN_TRANSFER
import app.masroufy.core.SmsKind.TRANSFER_IN
import app.masroufy.core.SmsKind.TRANSFER_OUT
import kotlin.test.Test

/**
 * حوالات البنوك السعودية: الاتجاه (حتى من غير كلمة اتجاه في العنوان) والطرف التاني = **الاسم + آخر 4 أرقام** لو الرسالة فيها
 * الاتنين (§39/§60/§72) — من غير رقم كامل أبدًا. «بين حساباتك» ⇒ الطرف = آخر 4 من حسابك التاني (§75-11). كل الرسايل مخترعة.
 */
class SaudiSmsTransferFormatsTest {
    private val ar = "سامي التجريبي"
    private val en = "SAMI TESTER"
    private val d = "في:26-03-05 09:10"
    private val on = "On: 05/03/2026 09:10"
    private val at = "2026-03-05 09:10:44"

    private val cases = listOf(
        // ── الراجحي القديم «حوالة داخلية/محلية» من غير اتجاه ⇒ من مكان الاسم ──
        SmsCase("rajhi old internal in", "حوالة داخلية\nمبلغ:SAR 900.00\nالى:1188\nمن:$ar\n$d", 90000, IN, null, party = ar),
        SmsCase("rajhi old internal out", "حوالة داخلية\nمن:1188\nمبلغ:SAR 900.00\nالى:$ar\n$d", 90000, OUT, null, party = ar),
        SmsCase("rajhi old local in (sending bank in عبر:)", "حوالة محلية\nعبر:بنك الرياض\nمبلغ:SAR 900.00\nالى:1188\nمن:$ar\n$d", 90000, IN, null, party = ar),
        SmsCase(
            "rajhi old local out (fee is not the amount)",
            "حوالة محلية\nمصرف:RIBL\nمن:1188\nمبلغ:SAR 900.00\nالى:$ar\nالرسوم:SAR 0.58\nمرجع:12345678\n$d", 90000, OUT, null, party = ar,
        ),
        // ── الراجحي بكلمة اتجاه ──
        SmsCase(
            "rajhi internal in + sender digits", "حوالة داخلية واردة\nمبلغ:SAR 900.00\nالى:1188\nمن:$ar\nمن:9021\n$d",
            90000, IN, TRANSFER_IN, party = ar, last4 = "9021",
        ),
        SmsCase(
            "rajhi 2026 internal in «من9021;name»", "حوالة داخلية واردة بـSR 900\nلـ1188\nمن9021;$ar\n26/3/5 09:10",
            90000, IN, TRANSFER_IN, party = ar, last4 = "9021",
        ),
        SmsCase(
            "rajhi local in", "حوالة محلية واردة\nعبر:بنك الرياض\nمبلغ:SAR 900.00\nالى:1188\nمن:$ar\nمن:9021\n$d",
            90000, IN, TRANSFER_IN, party = ar, last4 = "9021",
        ),
        SmsCase(
            "rajhi internal out + beneficiary digits", "حوالة داخلية صادرة\nمن:1188\nمبلغ:SAR 900.00\nالى:$ar\nالى:9021\n$d",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase(
            "rajhi 2026 internal out «لـ9021; name»", "حوالة داخلية صادرة\nمن1188\nبـSAR 900.00\nلـ9021; $ar\n26/3/5 09:10",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase(
            "rajhi local out", "حوالة محلية صادرة\nمصرف:RIBL\nمن:1188\nمبلغ:SAR 900.00\nالى:$ar\nالى:9021\nالرسوم:SAR 0.58\n26-03-05 09:10",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase("rajhi between your accounts", "حوالة بين حساباتك\nمبلغ: SAR 900.00\nالى: 2277\n26-03-05", 90000, OUT, OWN_TRANSFER, party = "••••2277", last4 = "2277"),
        SmsCase(
            "unlabelled internal in", "حوالة واردة:داخلية\nمبلغ:SAR 900.00\nالى:*1188\nمن: $ar\nمن حساب:*9021\n$d",
            90000, IN, TRANSFER_IN, party = ar, last4 = "9021",
        ),
        // ── الأهلي السعودي ──
        SmsCase("snb out", "حوالة صادرة\nبـ900.00 SAR\nالى: $ar\nالرصيد المتاح: SAR 4,100.00\nفي 09:10 05/03/26", 90000, OUT, TRANSFER_OUT, party = ar),
        SmsCase("snb between your accounts", "حوالة بين حساباتك\nبـ900.00 SAR\nالى $ar\nالرصيد: SAR 4,100.00\nفي 09:10 05/03/26", 90000, OUT, OWN_TRANSFER, party = ar),
        // ── ساب: اسم + آيبان في الاتجاهين ──
        SmsCase(
            "sab out", "حوالة صادرة مقبولة\nمن: **1188\nإلى: $ar\nآيبان: **9021\nبنك الرياض\nمبلغ: SAR 900.00\nرسوم: SAR 0.58\nفي: $at",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase(
            "sab in", "إيداع حوالة واردة\nمن: $ar\nإلى: **1188\nآيبان: **9021\nبنك الرياض\nمبلغ: SAR 900.00\nفي: $at",
            90000, IN, TRANSFER_IN, party = ar, last4 = "9021",
        ),
        // ── الإنماء ──
        SmsCase("alinma internal in", "حوالة واردة داخلية\nالمبلغ: 900.00 SAR\nلحساب: **1188\nمن: $ar\nفي: 05/03/2026 09:10", 90000, IN, TRANSFER_IN, party = ar),
        SmsCase(
            "alinma instant in", "حوالة واردة محلية سريع\nلحساب: **1188\nالمبلغ: 900.00 SAR\nمن: $ar\nمن حساب: **9021\nفي: 05/03/2026 09:10",
            90000, IN, TRANSFER_IN, party = ar, last4 = "9021",
        ),
        SmsCase("alinma local in «من name»", "حوالة واردة محلية\nمبلغ 900.00 SAR\nمن $ar\nحساب *1188\nفي 09:10 05/03/2026", 90000, IN, TRANSFER_IN, party = ar),
        SmsCase("alinma prose in (no party)", "عميلنا العزيز،\nتم استلام حوالة واردة من حساب جاري مبلغ SAR 900.00 إلى **1188 05/03/2026 09:10", 90000, IN),
        SmsCase(
            "alinma out «المستفيد»", "حوالة صادرة داخلية\nمبلغ: 900.00 ريال\nالمستفيد: $ar\nإلى حساب: *9021\nمن حساب: **1188\nفي: 05/03/2026 09:10",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase(
            "alinma out «لـ name» + «لحساب»", "حوالة صادرة داخلية\nمبلغ 900.00 ريال\nلـ $ar\nلحساب *9021\nفي 05/03/2026 09:10",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase(
            "alinma to your investment account", "حوالة صادرة لحسابك الاستثماري في بنك الانماء\nمبلغ: 900.00 ريال\nمن: **1188\nمن بنك: بنك الانماء\nإلى حساب: **2277\nفي: 05/03/2026 09:10",
            90000, OUT, OWN_TRANSFER, party = "••••2277", last4 = "2277",
        ),
        // ── الفرنسي: من غير نقطتين، والقيمة «القيمة» ──
        SmsCase(
            "bsf out", "عملية حوالة مالية صادرة مقبولة\nخصمت من حساب ****1188\nإلى $ar\nبنك الرياض\nآيبان ****9021\nالقيمة SAR 900.00\nالرسوم SAR 0.58\nفي05-03-2026 09:10:44",
            90000, OUT, TRANSFER_OUT, party = ar, last4 = "9021",
        ),
        SmsCase(
            "bsf in (own masked IBAN is not a name)", "حوالة واردة\nمبلغ: 900.00 SAR\nلـ:SA**********1*******8\nمن: $ar\nآيبان: ****9021\nفي: 05-03-2026 09:10:44",
            90000, IN, TRANSFER_IN, merchant = ar, party = ar, last4 = "9021",
        ),
        // ── دي 360: «IBAN:» في الوارد = حسابك إنت ──
        SmsCase(
            "d360 in digits only", "Incoming Transfer: Riyad Bank\nAmount: SAR 900.00\nFrom: *9021\nIBAN: *1188\nat: 05/03/2026 09:10",
            90000, IN, TRANSFER_IN, merchant = "", party = "••••9021", last4 = "9021",
        ),
        SmsCase("d360 internal in", "Incoming Internal transfer: D360\nAmount: SAR 900.00\nFrom: $en\nIBAN: ****1188\n$on", 90000, IN, TRANSFER_IN, party = en),
        SmsCase(
            "d360 internal out", "Outgoing Internal transfer: D360\nfrom: *1188\nAmount: SAR 900.00\nTo: $en\nIBAN: *9021\n$on",
            90000, OUT, TRANSFER_OUT, party = en, last4 = "9021",
        ),
        SmsCase(
            "d360 local out", "Outgoing Local Transfer\nFrom : ****1188\nAmount: SAR 900.00\nTo: $en\nIBAN: ****9021 - Riyad Bank\n$on",
            90000, OUT, TRANSFER_OUT, party = en, last4 = "9021",
        ),
        // ── بنك إس تي سي ──
        SmsCase("stc between my accounts", "Transfer between my accounts\nAmount: 900.00 SAR\nfrom: ***1188\nTo: ***2277\nAt: $at", 90000, OUT, OWN_TRANSFER, party = "••••2277", last4 = "2277"),
        SmsCase("stc between my accounts ar", "تحويل بين حساباتي\nبـ:900.00 ر.س\nمن: ***1188\nإلى: ***2277\nفي:$at", 90000, OUT, OWN_TRANSFER, party = "••••2277", last4 = "2277"),
        SmsCase(
            "stc sarie in (first From = name)", "Inward local transfer (SARIE)\n900.00 SAR\nFrom $en\nFrom Riyad Bank\nAccount *1188\n$at\nRef. No. *123456789",
            90000, IN, TRANSFER_IN, party = en,
        ),
        SmsCase("stc local in", "Credit Local transfer\n900.00SR\nFrom $en\nFrom Riyad Bank\nFrom 902\n$at", 90000, IN, TRANSFER_IN, party = en),
        SmsCase("stc internal in", "Internal incoming transfer\nAmount:900.00SAR\nFrom:$en\nAcc:30*\nAt:$at", 90000, IN, TRANSFER_IN, party = en),
        SmsCase("stc outward", "Outward transfer\nAmount: 900.00 SAR\nAcc: ***1188\nTo: $en\nAt: $at", 90000, OUT, TRANSFER_OUT, party = en),
        SmsCase("stc local out (fees line)", "Debit Local transfer\n900.00SR\nFees 0.58SR\nTo $en\nTo Riyad Bank\nTo 902\n$at", 90000, OUT, TRANSFER_OUT, party = en),
        SmsCase(
            "stc western union", "Transfer via WU\nAmount:900.00 SAR\nFees:25.00 SAR\nMTCN:1234567890\nReceiver:$en\nAccount:*1188\nCountry:EG\nAt:$at",
            90000, OUT, TRANSFER_OUT, party = en,
        ),
    )

    @Test fun everyTransferTemplateIsReadWithItsCounterparty() {
        for (c in cases) checkCase(c, ::parseBankSms, Currency.SAR)
    }
}
