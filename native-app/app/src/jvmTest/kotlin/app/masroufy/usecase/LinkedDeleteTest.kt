package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.PassthroughUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * مراجعة r04: «امسح» على طرف في «عمليات تابعة» (مصروف 120 · داخلة 60 مربوطة بيه) — كان بيسيب المصروف منقوص بداخلة ممسوحة، أو الداخلة
 * مستخبية من الدخل ورا مصروف ممسوح. دلوقتي الطرف التاني بيتفك في نفس وحدة العمل، و«تراجع» بيرجّع الاتنين زي ما كانوا. مبالغ مخترعة.
 */
class LinkedDeleteTest {
    private fun txn(id: String, amount: Long, dir: Direction, kind: EconomicKind) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = app.masroufy.core.Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "w-1",
    )

    private val meal = txn("meal", 12_000, Direction.OUT, EconomicKind.PURCHASE).copy(linkedInflowMinor = 6_000)
    private val share = txn("share", 6_000, Direction.IN, EconomicKind.UNCLASSIFIED).copy(linkedExpenseId = "meal")
    private val txns = MemoryTransactionRepository(listOf(meal, share))
    private val erase = DeleteTransaction(
        DeleteTransactionDeps(
            txns, MemorySourceRecordRepository(), MemorySettlementRepository(), MemoryAllocationRepository(), MemoryObligationRepository(),
            RevertLinkDeps(
                MemoryProjectLinkRepository(), MemoryEventLinkRepository(), MemoryTransactionTagRepository(), MemoryRoscaEntryRepository(),
                MemoryInstallmentPaymentRepository(), MemoryInstallmentPlanRepository(), MemoryZakatPaymentRepository(), MemoryAssetLotRepository(),
                MemoryAssetSaleRepository(),
            ),
            PassthroughUnitOfWork(), FixedClock("2026-10-15T20:00:00Z"),
        ),
    )

    private suspend fun one(id: String) = txns.findByIds(listOf(id)).single()

    @Test fun deletingTheInflowGivesTheExpenseItsFullAmountBackAndUndoRestoresTheLink() = runBlocking<Unit> {
        val removal = erase.delete("share")
        assertNull(one("meal").linkedInflowMinor, "المصروف بيرجع 120 كاملة — مفيش داخلة تنقّصه")
        erase.restore(removal)
        assertEquals(6_000L, one("meal").linkedInflowMinor)
        assertEquals("meal", one("share").linkedExpenseId)
    }

    @Test fun deletingTheExpenseReturnsTheInflowToIncomeAndUndoRestoresTheLink() = runBlocking<Unit> {
        val removal = erase.delete("meal")
        assertNull(one("share").linkedExpenseId, "الداخلة بترجع تتحسب بنوعها")
        erase.restore(removal)
        assertEquals(meal, one("meal"))
        assertEquals(share, one("share"))
    }
}
