package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «استخدم المتوسط» في لوحة السقف من `LoadBudgetScreen` (رد المالك OVERRIDES §82-17): متوسط **آخر 3 شهور** للشهر كله ولكل تصنيف —
 * مش متوسط الست فترات اللي بيتعرض كمعلومة. الشاشة بتنسخ الرقم بس. أسامي ومبالغ مخترعة.
 */
class LimitAverageFlowTest {
    private val period = buildPeriod(2026, 9, 28)
    private val fun_ = "cat-fun"
    private val grocery = "cat-grocery"
    private val books = "cat-books"
    private val categories = listOf(
        Category(fun_, null, "ترفيه", "i", "#000", "#fff", true, 1),
        Category(grocery, null, "بقالة", "i", "#000", "#fff", true, 2),
        Category(books, null, "كتب", "i", "#000", "#fff", true, 3),
    )
    private var seq = 0

    private fun txn(date: String, amount: Long, category: String) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category,
    )

    private fun load(rows: List<Transaction>) = runBlocking {
        LoadBudgetScreen(LoadBudgetScreenDeps(MemoryTransactionRepository(rows), MemoryCategoryRepository(categories), MemoryAllocationRepository(), MemoryBudgetRepository()))
            .load(LoadBudgetScreenRequest(period, "2026-10-01", 28))
    }

    @Test fun lastThreeMonthsForTheMonthAndForEachCategory() {
        val data = load(
            listOf(
                txn("2026-05-29", 300_000, fun_), // الشهر الرابع: في متوسط المعلومة، مش في «استخدم المتوسط»
                txn("2026-06-29", 30_000, fun_), txn("2026-06-30", 90_000, grocery),
                txn("2026-07-29", 30_000, fun_),
                txn("2026-08-29", 60_000, fun_), txn("2026-08-30", 90_000, grocery),
                txn("2026-09-29", 10_000, fun_), txn("2026-09-29", 5_000, books),
            ),
        )
        assertEquals(100_000, data.limitAverageMinor, "(120,000 + 30,000 + 150,000) ÷ 3")
        assertEquals(40_000, data.limitAverageByCategory[fun_])
        assertEquals(60_000, data.limitAverageByCategory[grocery], "شهر يوليو من غير بقالة = صفر")
        // تصنيف ما صرفش فيه في التلات شهور (بس الشهر الجاري) ⇒ صفر معروف مش «غير متاح»
        assertEquals(0L, data.limitAverageByCategory[books])
        assertEquals(150_000, data.average.averageMinor, "متوسط المعلومة (الأربع شهور) لوحده — الاتنين ما يختلطوش")
    }

    @Test fun notAvailableWithoutThreeCompleteMonths() {
        val data = load(listOf(txn("2026-07-29", 30_000, fun_), txn("2026-08-29", 60_000, fun_), txn("2026-09-29", 10_000, fun_)))
        assertNull(data.limitAverageMinor, "يونيو فاضي ⇒ مش معروف")
        assertNull(data.limitAverageByCategory[fun_])
    }
}
