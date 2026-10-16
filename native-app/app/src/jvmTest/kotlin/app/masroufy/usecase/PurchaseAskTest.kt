package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** «آخر اللي استخدمته» في «إيه الصنف؟» بعد «شراء» (2026-10-16). أسامي مخترعة. */
class PurchaseAskTest {
    private fun cat(id: String, active: Boolean = true) = Category(id, null, "تصنيف $id", "tag", "#ABCDEF", "#123456", active, 1)

    private fun buy(id: String, date: String, cat: String?, kind: EconomicKind = EconomicKind.PURCHASE) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = cat != null, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = cat,
    )

    @Test fun newestFirstDistinctActiveAndPurchasesOnly() {
        val rows = listOf(
            buy("1", "2026-10-01", "a"), buy("2", "2026-10-05", "b"), buy("3", "2026-10-03", "a"),
            buy("4", "2026-10-06", "off"), buy("5", "2026-10-07", "c", EconomicKind.FEE), buy("6", "2026-10-08", null),
        )
        val got = recentPurchaseCategories(rows, listOf(cat("a"), cat("b"), cat("c"), cat("off", active = false)), limit = 6)
        assertEquals(listOf("b", "a"), got.map { it.id })
        assertEquals(listOf("b"), recentPurchaseCategories(rows, listOf(cat("a"), cat("b")), limit = 1).map { it.id })
    }

    @Test fun readsOnlyTheLastNinetyDays() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository(listOf(buy("old", "2026-06-01", "a"), buy("new", "2026-10-01", "b")))
        val got = RecentCategories(txns, MemoryCategoryRepository(listOf(cat("a"), cat("b"))), FixedClock("2026-10-16T09:00:00.000Z")).forPurchase()
        assertEquals(listOf("b"), got.map { it.id })
    }
}
