package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.BILL
import app.masroufy.core.SmsKind.CARD_PAYMENT
import app.masroufy.core.SmsKind.CASH_DEPOSIT
import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.FEE
import app.masroufy.core.SmsKind.OTHER
import app.masroufy.core.SmsKind.OWN_TRANSFER
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.PURCHASE_WITH_CASH
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.SALARY
import app.masroufy.core.SmsKind.TRANSFER_IN
import app.masroufy.core.SmsKind.TRANSFER_OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * عناوين العمليات الموحّدة من البنك المركزي السعودي (تعميم 42023876 — نص عام منشور): كل عنوان بالعربي والإنجليزي كأول سطر في
 * رسالة عامة لبنك **مالوش عينة** ⇒ الاتجاه والنوع من العنوان لوحده. الحجز وفكّه **مش** عملية.
 */
class SamaSmsTitlesTest {
    private class Title(val ar: String, val en: String, val direction: Direction, val kind: SmsKind)

    private val titles = listOf(
        Title("سداد فاتورة", "Bill Payment", OUT, BILL),
        Title("سداد فاتورة لمرة واحدة", "Bill Payment one time", OUT, BILL),
        Title("إصدار شيك مصدّق", "Certified Cheque Issued", OUT, OTHER),
        Title("بطاقة ائتمانية استرجاع نقدي", "Credit Card Cashback", IN, REFUND),
        Title("بطاقة ائتمانية تأكيد سداد", "Credit Card Credited", IN, CARD_PAYMENT),
        Title("بطاقة ائتمانية تسديد", "Credit Card Payment", OUT, CARD_PAYMENT),
        Title("بطاقة ائتمانية استرداد مبلغ", "Credit Card Refund", IN, REFUND),
        Title("إيداع رسوم", "Credit Transaction Fees", IN, OTHER),
        Title("حوالة واردة من بطاقة", "Credit transfer from card", IN, TRANSFER_IN),
        Title("حوالة واردة بين حساباتك", "Credit transfer Between Your Accounts", IN, OWN_TRANSFER),
        Title("حوالة واردة حساب مواطن", "Credit transfer Citizen Account", IN, TRANSFER_IN),
        Title("سحب نقدي طارئ", "Credit transfer Emergency Cash Withdrawal", OUT, CASH_WITHDRAWAL),
        Title("حوالة واردة من حسابك الجاري", "Credit transfer From your Current Account", IN, OWN_TRANSFER),
        Title("حوالة واردة من حسابك الاستثماري", "Credit transfer From Your Investment Account", IN, OWN_TRANSFER),
        Title("حوالة واردة حافز", "Credit transfer Hafiz", IN, TRANSFER_IN),
        Title("حوالة واردة داخلية", "Credit transfer Internal", IN, TRANSFER_IN),
        Title("حوالة واردة دولية", "Credit transfer International", IN, TRANSFER_IN),
        Title("حوالة واردة تمويل", "Credit transfer Loan", IN, TRANSFER_IN),
        Title("حوالة واردة محلية", "Credit transfer Local", IN, TRANSFER_IN),
        Title("حوالة واردة كفيل", "Credit transfer Sponsor", IN, TRANSFER_IN),
        Title("حوالة واردة مكافأة طلاب", "Credit transfer Student Reward", IN, TRANSFER_IN),
        Title("خصم رسوم", "Debit Transaction Fees", OUT, FEE),
        Title("حوالة صادرة الى بطاقة", "Debit Transfer to card", OUT, TRANSFER_OUT),
        Title("حوالة صادرة بين حساباتك", "Debit Transfer Between Your Account", OUT, OWN_TRANSFER),
        Title("حوالة صادرة داخلية", "Debit Transfer Internal", OUT, TRANSFER_OUT),
        Title("حوالة صادرة دولية", "Debit Transfer International", OUT, TRANSFER_OUT),
        Title("حوالة صادرة محلية", "Debit Transfer Local", OUT, TRANSFER_OUT),
        Title("حوالة صادرة راتب", "Debit Transfer Salary", OUT, TRANSFER_OUT),
        Title("حوالة صادرة مكفول", "Debit Transfer Sponsored", OUT, TRANSFER_OUT),
        Title("حوالة صادرة الى حسابك الجاري", "Debit Transfer To Your Current Account", OUT, OWN_TRANSFER),
        Title("حوالة صادرة الى حسابك الاستثمار", "Debit Transfer To Your Investment account", OUT, OWN_TRANSFER),
        Title("إيداع صراف آلي", "Deposit ATM", IN, CASH_DEPOSIT),
        Title("إيداع فرع", "Deposit Branch", IN, CASH_DEPOSIT),
        Title("إيداع شيك ورقي", "Deposit Paper Cheque", IN, OTHER),
        Title("شراء عملة أجنبية", "Foreign Currency Purchase", OUT, PURCHASE),
        Title("سحب صراف آلي دولي", "International ATM Withdrawal", OUT, CASH_WITHDRAWAL),
        Title("مدفوعات وزارة الداخلية", "MOI Payments", OUT, BILL),
        Title("شراء إنترنت", "Online Purchase", OUT, PURCHASE),
        Title("امر مستديم سداد فواتير", "Permanent transfer Bill Payment", OUT, BILL),
        Title("امر مستديم حوالة صادرة داخلية", "Permanent transfer Debit transfer Bank internal", OUT, TRANSFER_OUT),
        Title("امر مستديم حوالة صادرة بين حساباتك", "Permanent transfer Debit transfer Between Your Accounts", OUT, OWN_TRANSFER),
        Title("امر مستديم مدفوعات وزارة الداخلية", "Permanent transfer MOI Payments", OUT, BILL),
        Title("شراء عبر نقاط البيع دولية", "PoS International Purchase", OUT, PURCHASE),
        Title("شراء ونقد عبر نقاط البيع", "PoS Purchase & Cashback", OUT, PURCHASE_WITH_CASH), // الجولة التامنة: جزء منه كاش (§75-4)
        Title("تسوية نقطة البيع", "PoS settlement", IN, OTHER),
        Title("حوالة واردة", "Received transfer", IN, TRANSFER_IN),
        Title("استرجاع مدفوعات وزارة الداخلية", "Refunding MOI Payments", IN, REFUND),
        Title("حوالة عكسية", "Reverse Transaction", IN, SmsKind.RETURNED),
        Title("سحب صراف آلي", "ATM Withdrawal", OUT, CASH_WITHDRAWAL),
        Title("سحب فرع", "Branch Withdrawal", OUT, CASH_WITHDRAWAL),
        Title("حوالة واردة راتب", "Credit transfer Salary", IN, SALARY),
        Title("خصم قسط تمويل", "Debit Transfer Loan Instalment", OUT, TRANSFER_OUT),
    )

    private fun read(body: String): SmsRow = assertIs<SmsParseResult.Ok>(parseBankSms(smsMessage(body), 1), body).row

    @Test fun everyStandardTitleGivesDirectionAndKindInBothLanguages() {
        for (t in titles) {
            val ar = read("${t.ar}\nمبلغ: SAR 245.75\nفي: 2026-03-05 09:10")
            assertEquals(t.direction, ar.direction, t.ar)
            assertEquals(24575, ar.amountMinor, t.ar)
            assertEquals(SMS_TX_DAY, ar.date, t.ar)
            val en = read("${t.en}\nAmount: SAR 245.75\nOn: 2026-03-05 09:10")
            assertEquals(t.direction, en.direction, t.en)
            // «خصم قسط تمويل» عربي = قسط (نوع تاني) والإنجليزي «Debit Transfer …» = حوالة — الاتجاه هو المهم
            if (t.ar != "خصم قسط تمويل") assertEquals(t.kind, ar.kind, t.ar)
            assertEquals(t.kind, en.kind, t.en)
        }
    }

    @Test fun holdAndReleaseAreNotTransactions() {
        for (title in listOf("بطاقة ائتمانية حجز مبلغ", "بطاقة ائتمانية الغاء حجز مبلغ", "Credit Card Cash Reserve", "Credit Card Cash Release")) {
            val r = assertIs<SmsParseResult.Rejected>(parseBankSms(smsMessage("$title\nمبلغ: SAR 245.75\nفي: 2026-03-05 09:10"), 1), title)
            assertEquals(uiText(TextKey.SMS_NOT_TRANSACTION), r.reason, title)
        }
    }

    /** العنوان بيغلب كلام الرسالة: «حوالة صادرة راتب» طالعة رغم «راتب»، و«استرجاع مدفوعات» داخلة رغم «مدفوعات». */
    @Test fun titleBeatsTheWordsAfterIt() {
        assertEquals(OUT, read("Debit Transfer Salary\nAmount: SAR 245.75\nOn: 2026-03-05 09:10").direction)
        assertEquals(IN, read("استرجاع مدفوعات وزارة الداخلية\nمبلغ: SAR 245.75\nفي: 2026-03-05 09:10").direction)
        // «شراء واسترداد» مش عنوان — نفس التطبيق الحالي (ملف المرجع): الاتجاه مش واضح
        val both = assertIs<SmsParseResult.Rejected>(parseBankSms(smsMessage("شراء واسترداد 25 SAR"), 1))
        assertEquals(uiText(TextKey.SMS_DIRECTION_UNCLEAR), both.reason)
    }
}
