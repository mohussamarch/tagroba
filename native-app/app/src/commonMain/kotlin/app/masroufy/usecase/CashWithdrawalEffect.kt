package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.Wallet
import app.masroufy.port.WalletRepository

/**
 * السحب من الصرّاف (أو من وكيل المحفظة في مصر) = **نقل لمحفظة الكاش لوحده** (§75-4 — قرار المالك 2026-10-08 ✓): رسالة
 * [SmsKind.CASH_WITHDRAWAL] بتتسجل تحويل داخلي مؤكد من محفظة الرسالة (البنك) لمحفظة الكاش **الوحيدة** في البلد — الرصيدين بيتظبطوا
 * (البنك ينزل والكاش يزيد بنفس المبلغ)، ومش مصروف ولا «حركة فلوس» (§58).
 *
 * - البلد مالهاش محفظة كاش **واحدة** (مفيش، أو أكتر من واحدة) ⇒ الرسالة بتستنى في الخلفية (`SMS_WAIT_NO_CASH_WALLET` — `SmsWaitReasons.kt`)،
 *   ولو المالك سجّلها بنفسه بتتسجل زي ما هي (صرف من البنك لسه ما اتحددش نوعه) — ما بنختارش محفظة من عندنا.
 * - نفس قاعدة [singleCashWallet] بتتحسب لما الهدف بيتبني (`AutoRecordSms.walletFor`) ووقت التسجيل هنا — فاللي اتسجل لوحده اتنقل فعلًا.
 * - ترتيبه قبل `SmsFeeEffect`: لو السحب عليه رسوم، الرسوم بتتسجل لوحدها والنقل بالمبلغ نفسه.
 */
class CashWithdrawalEffect(private val wallets: WalletRepository) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        val lines = ctx.lines.filter { it.sms?.kind == SmsKind.CASH_WITHDRAWAL && it.transaction.observedDirection == Direction.OUT }
        if (lines.isEmpty()) return
        val cash = singleCashWallet(wallets.listAll()) ?: return
        for (line in lines) {
            val t = line.transaction
            if (t.walletId == null || t.walletId == cash.id || t.currency != cash.currency) continue
            line.transaction = t.copy(
                economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, transferToWalletId = cash.id,
                reviewState = ReviewState.CONFIRMED, updatedAt = ctx.nowIso,
            )
        }
    }
}

/** محفظة الكاش **الوحيدة** في البلد، أو null لو مفيش أو أكتر من واحدة (ما بنختارش — §75-4). */
internal fun singleCashWallet(wallets: List<Wallet>): Wallet? = wallets.filter { it.kind == "cash" }.singleOrNull()
