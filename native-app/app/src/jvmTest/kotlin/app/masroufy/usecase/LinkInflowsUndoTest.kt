package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LinkRefusal
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * r041 A11 — «فك الربط» بتأكيد و«تراجع» 8 ثواني: الفك بيرجّع اللي يلزم للتراجع، والتراجع بيرجّع الحقلين بالظبط، وآمن التكرار،
 * ولو المصروف اتمسح ⇒ سبب رفض ومفيش كتابة. بيانات مخترعة.
 */
class LinkInflowsUndoTest {
    private var seq = 0

    private fun txn(id: String, date: String, amount: Long, dir: Direction, kind: EconomicKind, category: String? = null) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = seq++, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = category != null, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", categoryId = category, walletId = "w-1",
    )

    private val meal = txn("meal", "2026-09-10", 12_000, Direction.OUT, EconomicKind.PURCHASE, category = "food")
    private val share = txn("share", "2026-09-11", 6_000, Direction.IN, EconomicKind.UNCLASSIFIED)
    private val other = txn("other", "2026-09-12", 2_000, Direction.IN, EconomicKind.REFUND_RECEIVED)

    private val txns = MemoryTransactionRepository(listOf(meal, share, other))
    private val links = LinkInflows(
        LinkInflowsDeps(
            txns, MemoryAllocationRepository(emptyList()), MemorySettlementRepository(emptyList()), MemoryObligationRepository(),
            MemoryUnitOfWork(listOf(txns)), FixedClock("2026-09-20T10:00:00Z"),
        ),
    )

    private fun one(id: String) = txns.all().single { it.id == id }

    @Test fun unlinkThenRelinkRestoresBothFieldsExactly() = runBlocking<Unit> {
        assertNull(links.link("meal", listOf("share", "other")))
        val before = one("meal").linkedInflowMinor to one("share").linkedExpenseId
        assertEquals(8_000L to "meal", before)
        val undo = links.unlinkWithUndo("share")
        assertEquals(UnlinkUndo("share", "meal"), undo)
        assertNull(one("share").linkedExpenseId)
        assertEquals(2_000L, one("meal").linkedInflowMinor, "the expense keeps only the other inflow")
        assertNull(links.relink(undo!!))
        assertEquals(before, one("meal").linkedInflowMinor to one("share").linkedExpenseId)
    }

    @Test fun relinkIsSafeToRepeat() = runBlocking<Unit> {
        links.link("meal", listOf("share"))
        val undo = links.unlinkWithUndo("share")!!
        assertNull(links.relink(undo))
        val once = txns.all()
        assertNull(links.relink(undo), "already linked again ⇒ nothing to do")
        assertEquals(once, txns.all(), "no second write")
        assertEquals(6_000L, one("meal").linkedInflowMinor)
    }

    @Test fun relinkAfterTheExpenseWasDeletedIsRefusedAndWritesNothing() = runBlocking<Unit> {
        links.link("meal", listOf("share"))
        val undo = links.unlinkWithUndo("share")!!
        txns.deleteMany(listOf("meal"))
        val before = txns.all()
        assertEquals(LinkRefusal.NOT_EXPENSE, links.relink(undo))
        assertEquals(before, txns.all())
        assertNull(one("share").linkedExpenseId)
    }

    @Test fun unlinkingSomethingNotLinkedGivesNoUndo() = runBlocking<Unit> {
        assertNull(links.unlinkWithUndo("other"))
        assertEquals(false, links.unlink("other"))
    }
}
