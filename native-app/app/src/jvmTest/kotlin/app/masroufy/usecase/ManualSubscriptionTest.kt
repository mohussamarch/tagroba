package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.RecurringError
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.recurringKey
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** r06-links2 فجوة ٢: «ضيف اشتراك» بإيدك. أسامي ومبالغ مخترعة · المبالغ بالوحدة الصغرى. */
class ManualSubscriptionTest {
    private fun manage(items: MemoryRecurringRepository = MemoryRecurringRepository()) =
        ManageRecurring(ManageRecurringDeps(items, MemoryTransactionRepository(), MemoryCategoryRepository(), SequentialIdGenerator()))

    @Test fun addsAConfirmedSubscriptionThatShowsInTheList() = runBlocking<Unit> {
        val items = MemoryRecurringRepository()
        val m = manage(items)
        val item = m.addManual("  خدمة وهمية  ", "subscription", 12, 29_900, Currency.SAR, "2026-11-05")
        assertEquals("خدمة وهمية", item.name)
        assertTrue(item.confirmed && item.active)
        assertEquals(Triple(12, 29_900L, "2026-11-05"), Triple(item.cycleMonths, item.expectedMinor, item.nextDueAt))
        assertEquals(listOf(item.id), m.load("2026-10-24").items.map { it.item.id })
    }

    @Test fun aChargeWithTheSameNameBelongsToIt() = runBlocking<Unit> {
        val item = manage().addManual("خدمة وهمية", "bill", 1, 10_000, Currency.SAR, "2026-11-01")
        val charge = Transaction(
            id = "t1", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
            observedDirection = Direction.OUT, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w", rawMerchantName = "خدمة وهمية",
        )
        assertEquals(item.merchantKey, recurringKey(charge))
    }

    @Test fun refusesAnEmptyNameOrAZeroAmountOrAnUnknownCycle() = runBlocking<Unit> {
        val m = manage()
        assertFailsWith<RecurringError> { m.addManual(" ", "subscription", 1, 1_000, Currency.SAR, "2026-11-01") }
        assertFailsWith<RecurringError> { m.addManual("خدمة", "subscription", 1, 0, Currency.SAR, "2026-11-01") }
        assertFailsWith<RecurringError> { m.addManual("خدمة", "subscription", 2, 1_000, Currency.SAR, "2026-11-01") }
    }
}
