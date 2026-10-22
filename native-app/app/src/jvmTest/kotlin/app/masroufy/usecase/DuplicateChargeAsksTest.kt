package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.closeAmounts
import app.masroufy.core.duplicateChargeIds
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** r06-darahim (§12-5): «مكرر؟» — أسامي ومبالغ مخترعة. بيعلّم الأحدث بس، وعمره ما بيمسح. */
class DuplicateChargeAsksTest {
    private fun tx(id: String, time: String?, amount: Long, merchant: String = "TEST DINER RIYADH SA", day: String = "2026-10-07", dir: Direction = Direction.OUT) = Transaction(
        id = id, occurredAt = day, datePrecision = if (time == null) "day" else "minute", sourceTime = time, sourceOrder = 0,
        economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = Currency.SAR,
        categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false,
        createdAt = "2026-10-07T10:00:00.000Z", updatedAt = "2026-10-07T10:00:00.000Z", rawMerchantName = merchant,
    )

    @Test fun amountsWithinOnePercentOrOneRiyal() {
        assertTrue(closeAmounts(2_000, 2_100), "ريال واحد")
        assertFalse(closeAmounts(2_000, 2_101))
        assertTrue(closeAmounts(50_000, 50_500), "1% من الأكبر")
        assertFalse(closeAmounts(50_000, 50_600))
    }

    @Test fun flagsTheNewerOfAClosePairOnly() {
        val rows = listOf(
            tx("a1", "20:32:10", 4_500), tx("a2", "20:33:05", 4_500), // 55 ثانية ⇒ a2
            tx("b1", "12:00:00", 4_500), tx("b2", "12:01:01", 4_500), // 61 ثانية ⇒ لأ
            tx("c1", "09:00", 3_000), tx("c2", "09:00", 3_000, merchant = "OTHER TEST SHOP"), // محل تاني ⇒ لأ
            tx("d1", "15:00:00", 3_000), tx("d2", "15:00:30", 9_000), // مبلغ بعيد ⇒ لأ
            tx("e1", null, 2_000), tx("e2", null, 2_000), // من غير ساعة ⇒ لأ (القاعدة 10)
            tx("f1", "18:00:00", 2_000), tx("f2", "18:00:20", 2_000, dir = Direction.IN), // داخل ⇒ لأ
        )
        assertEquals(setOf("a2"), duplicateChargeIds(rows))
    }

    @Test fun scanMarksAnsweredStaysAnsweredAndNothingIsDeleted() = runBlocking<Unit> {
        val repo = MemoryTransactionRepository(listOf(tx("a1", "20:32:10", 4_500), tx("a2", "20:32:40", 4_550)))
        val asks = DuplicateChargeAsks("sa", repo, FixedClock("2026-10-08T09:00:00.000Z"))
        assertEquals(1, asks.scanRecent())
        val pending = asks.pending("2026-10-01", "2026-10-08")
        assertEquals(listOf("a2"), pending.map { it.transactionId })
        assertEquals(AskKind.DUPLICATE_CHARGE, pending.single().kind)
        assertEquals(2, repo.listByDateRange("2026-10-01", "2026-10-08").size, "ما بيمسحش")

        asks.keepBoth("a2")
        assertTrue(asks.pending("2026-10-01", "2026-10-08").isEmpty())
        assertEquals(0, asks.scanRecent(), "«الاتنين صح» ما بيرجعش يتسأل")
    }
}
