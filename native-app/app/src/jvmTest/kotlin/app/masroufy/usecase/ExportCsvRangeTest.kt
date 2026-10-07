package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * تصدير CSV **بالظبط من «من» لـ«لحد»** (قرار المالك، OVERRIDES §49). مكتوب بالإيد لأن التطبيق الحالي
 * بيطلّع الفترات المالية كاملة، فملف المرجع مينفعش يكون الحكم لمدى بيعدّي حدود فترة.
 */
class ExportCsvRangeTest {
    private fun txn(id: String, date: String) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1,
        economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true, observedDirection = Direction.OUT,
        amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false,
        createdAt = "2026-09-01T00:00:00.000Z", updatedAt = "2026-09-01T00:00:00.000Z", rawMerchantName = "TEST $id",
    )

    private val all = listOf(
        txn("before", "2025-12-30"), txn("first", "2026-01-01"), txn("mid", "2026-02-15"),
        txn("last", "2026-02-28"), txn("after", "2026-03-10"),
    )

    private fun ids(csv: String) = csv.trimEnd('\n').split('\n').drop(1).map { it.substringAfterLast(',') }

    @Test
    fun keepsOnlyTheChosenDatesEvenInsideAPeriod() {
        val csv = runBlocking { ExportCsv(MemoryTransactionRepository(all)).export("2026-01-01", "2026-02-28", 28) }
        // يوم راتب 28: فترة يناير بتبدأ 2025-12-28 وآخر فترة بتخلص 2026-03-27 — والتطبيق الحالي كان بيطلّع «before» و«after»
        assertEquals(listOf("first", "mid", "last"), ids(csv))
    }

    @Test
    fun anEmptyRangeGivesTheHeaderOnly() {
        val csv = runBlocking { ExportCsv(MemoryTransactionRepository(all)).export("2026-03-11", "2026-03-20", 28) }
        assertEquals(Char(0xFEFF) + "date,name,amount,type,source,reference\n", csv)
    }
}
