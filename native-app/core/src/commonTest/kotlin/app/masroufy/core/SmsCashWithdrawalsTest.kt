package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * مراجعة الشريحة S2 (§75-4 — `SmsCashWithdrawals.kt`): السحب العادي من الحساب جوه البلد بس هو اللي بيتنقل للكاش لوحده؛ سلفة كارت
 * الائتمان والصرّاف اللي برّه بيستنوا. كل الرسايل والأسامي والأرقام مخترعة.
 */
class SmsCashWithdrawalsTest {
    private fun row(raw: String, kind: SmsKind = SmsKind.CASH_WITHDRAWAL, direction: Direction = Direction.OUT) =
        SmsRow(1, "2026-10-07", 50_000, direction, "", "SMS:x", "TESTBANK", raw, raw, kind)

    @Test fun onlyAPlainWithdrawalFromTheAccountMovesToCash() {
        assertTrue(row("ATM Withdrawal\nCard: *7739\nAmount: SAR 500.00\nAt: TEST ATM RIYADH\nOn: 2026-10-07 10:00").movesToCash())
        assertTrue(row("تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه.").movesToCash())
        // «دولي» جوه اسم بنك مش صرّاف برّه
        assertTrue(row("تم سحب 500 جنيه من ماكينة البنك التجاري الدولي").movesToCash())
        assertTrue(row("ATM Withdrawal at Commercial International Bank ATM").movesToCash())
        val waits = listOf(
            "Your credit card ending with #4417 was charged for EGP 2,000.00 at CASH WITHDRAWAL on 07/10/2026",
            "سحب صراف آلي دولي\nمبلغ: SAR 500.00\nبطاقة: *7739",
            "International ATM Withdrawal\nAmount: SAR 500.00",
            "Cash advance from your card SAR 500",
            "سلفة نقدية من البطاقة الائتمانية",
            "سحب نقدي خارج المملكة",
        )
        for (raw in waits) assertFalse(row(raw).movesToCash(), raw)
        assertFalse(row("ATM Withdrawal", kind = SmsKind.PURCHASE).movesToCash())
        assertFalse(row("ATM Withdrawal", direction = Direction.IN).movesToCash())
    }
}
