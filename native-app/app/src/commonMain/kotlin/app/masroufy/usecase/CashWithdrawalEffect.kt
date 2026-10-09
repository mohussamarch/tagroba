package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.movesToCash
import app.masroufy.port.WalletRepository

/**
 * السحب من الصرّاف (أو من وكيل المحفظة في مصر) = **نقل لمحفظة الكاش لوحده** (§75-4 — قرار المالك 2026-10-08 ✓): رسالة
 * [SmsKind.CASH_WITHDRAWAL] بتتسجل تحويل داخلي مؤكد من محفظة الرسالة (البنك) لمحفظة الكاش — الرصيدين بيتظبطوا (البنك ينزل والكاش
 * يزيد بنفس المبلغ)، ومش مصروف ولا «حركة فلوس» (§58).
 *
 * - **قاعدة واحدة** ([cashWalletFor] + `SmsRow.movesToCash` في `SmsCashWithdrawals.kt`) للتسجيل التلقائي (`SmsReviewTarget.cashWalletId`
 *   ⇒ سبب الانتظار في [cashWithdrawalWait]) وللأثر هنا: اللي اتسجل لوحده في الخلفية اتنقل فعلًا، واللي مش هيتنقل بيستنى تأكيد المالك.
 * - مش بيتنقل (بيستنى، ولو المالك سجّله بنفسه بيتسجل زي ما هو — صرف من المحفظة لسه ما اتحددش نوعه؛ ما بنختارش محفظة من عندنا):
 *   البلد مالهاش محفظة كاش **واحدة** بعملة المحفظة نفسها · المحفظة نفسها هي الكاش · سلفة نقدية من **كارت ائتمان** · سحب من صرّاف
 *   **برّه البلد**.
 * - ترتيبه **قبل** `SmsFeeEffect` (عقد الترتيب): لو السحب عليه رسوم، الرسوم بتتسجل لوحدها والنقل بالمبلغ نفسه.
 */
class CashWithdrawalEffect(private val wallets: WalletRepository) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        val lines = ctx.lines.filter { it.sms?.movesToCash() == true && it.transaction.observedDirection == Direction.OUT }
        if (lines.isEmpty()) return
        val all = wallets.listAll()
        for (line in lines) {
            val t = line.transaction
            val from = all.firstOrNull { it.id == t.walletId } ?: continue
            val cash = cashWalletFor(from, all)?.takeIf { it.currency == t.currency } ?: continue
            line.transaction = t.copy(
                economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, transferToWalletId = cash.id,
                reviewState = ReviewState.CONFIRMED, updatedAt = ctx.nowIso,
            )
        }
    }
}

/**
 * محفظة الكاش اللي السحب من [wallet] بيتنقل ليها (§75-4): محفظة الكاش **الوحيدة** في البلد ([all]) — بنفس عملة [wallet] ومش هي نفسها.
 * غير كده null (مفيش · أكتر من واحدة · عملة تانية · المحفظة نفسها كاش) ⇒ ما بنختارش.
 */
internal fun cashWalletFor(wallet: Wallet, all: List<Wallet>): Wallet? {
    val cash = all.filter { it.kind == "cash" }.singleOrNull() ?: return null
    return cash.takeIf { it.id != wallet.id && it.currency == wallet.currency }
}

/**
 * سبب انتظار السحب (الشكل معروف): مش بيتنقل لوحده (كارت ائتمان · صرّاف برّه) ⇒ `SMS_WAIT_CASH_ADVANCE` · مفيش محفظة كاش يتنقل ليها
 * (الهدف — نفس [cashWalletFor]، أو استيراد البلد من غير الأثر) ⇒ `SMS_WAIT_CASH_WITHDRAWAL` (نصه صحيح في الحالتين: «بيتنقل لو فيه
 * محفظة كاش واحدة بنفس العملة»). بيتنقل ⇒ null.
 */
internal fun cashWithdrawalWait(row: SmsRow, target: SmsReviewTarget): TextKey? = when {
    !row.movesToCash() -> TextKey.SMS_WAIT_CASH_ADVANCE
    target.cashWalletId == null || target.cashWalletId == target.walletId -> TextKey.SMS_WAIT_CASH_WITHDRAWAL
    else -> null
}
