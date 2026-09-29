package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.assertHalalas
import app.masroufy.core.formatMoney
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * ربط عملية من الكشف بجمعية أو بخطة أقساط — الفحص المشترك قبل الكتابة.
 * العملية الواحدة **ما تتربطش بحاجتين**: لو اتربطت بجمعية وبقسط، نفس الفلوس هتتعد مرتين في «المستحقات».
 */
class DueLinkError(message: String) : IllegalArgumentException(message)

internal class DueLinks(
    private val txns: TransactionRepository,
    private val roscaEntries: RoscaEntryRepository,
    private val payments: InstallmentPaymentRepository,
    private val clock: Clock,
) {
    /** العملية بعد الفحص + المبلغ اللي هيتربط (المبلغ كله لو ما اتحددش). */
    suspend fun check(transactionId: Id, direction: Direction, currency: Currency, ownerName: String, amountMinor: Halalas?): Pair<Transaction, Halalas> {
        val txn = txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw DueLinkError(uiText(TextKey.DUE_TXN_NOT_FOUND))
        if (txn.observedDirection != direction) {
            throw DueLinkError(uiText(if (direction == Direction.OUT) TextKey.DUE_TXN_NEEDS_OUT else TextKey.DUE_TXN_NEEDS_IN))
        }
        if (txn.currency != currency) throw DueLinkError(uiText(TextKey.DUE_TXN_CURRENCY, ownerName))
        val ids = listOf(transactionId)
        if (roscaEntries.listByTransactionIds(ids).isNotEmpty() || payments.listByTransactionIds(ids).isNotEmpty()) {
            throw DueLinkError(uiText(TextKey.DUE_TXN_ALREADY_LINKED))
        }
        val amount = amountMinor ?: txn.amountMinor
        assertHalalas(amount)
        // جزء من العملية مسموح (تحويل فيه القسط وحاجة تانية)، أكتر منها لأ
        if (amount <= 0) throw DueLinkError(uiText(TextKey.DUE_AMOUNT_POSITIVE))
        if (amount > txn.amountMinor) throw DueLinkError(uiText(TextKey.DUE_AMOUNT_OVER_TXN, formatMoney(txn.amountMinor, txn.currency)))
        return txn to amount
    }

    /** النوع بيتأكد عشان محدش يرجع يسأل عليه، والعملية بتخرج من «محتاجة مراجعة». */
    suspend fun markKind(transactionId: Id, kind: EconomicKind) {
        txns.update(
            transactionId,
            TransactionPatch(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = clock.nowIso()),
        )
    }

    /** فك الربط: النوع بيرجع «لسه ما اتحددش» ويتسأل تاني — ما بنخمّنش النوع القديم. */
    suspend fun clearKind(transactionId: Id) {
        txns.update(
            transactionId,
            TransactionPatch(economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW, updatedAt = clock.nowIso()),
        )
    }
}
