package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** «ممكن تكون مربوطة بـ» (2026-10-16): مرشحين من «عمليات تابعة» بس، لحد 3، وسريع على بيانات كبيرة. كل المبالغ مخترعة. */
class QuickLinksTest {
    private fun txn(
        id: String, date: String, minor: Long, dir: Direction, kind: EconomicKind = EconomicKind.PURCHASE, merchant: String? = null,
        currency: Currency = Currency.SAR,
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-1", rawMerchantName = merchant,
    )

    private fun world(extra: Int): Pair<MemoryTransactionRepository, QuickLinks> {
        val start = toDayNumber(parseIsoDate("2024-01-01"))
        val noise = (0 until extra).map { i ->
            txn("n$i", dayNumberToIso(start + i % 900), 1_000L + i, if (i % 3 == 0) Direction.IN else Direction.OUT, if (i % 3 == 0) EconomicKind.SALARY else EconomicKind.PURCHASE)
        }
        val txns = MemoryTransactionRepository(
            noise + listOf(
                txn("buy", "2026-10-01", 40_000, Direction.OUT, merchant = "QA CAFE"),
                // نفس التاجر (أي مبلغ) · نفس المبلغ في حدود 30 يوم
                txn("ref1", "2026-10-03", 10_000, Direction.IN, EconomicKind.REFUND_RECEIVED, merchant = "qa  cafe"),
                txn("ref2", "2026-10-05", 40_000, Direction.IN, EconomicKind.REFUND_RECEIVED),
                // فحص 0.5.3: ملهاش علاقة — مبلغ تاني من غير تاجر · نفس المبلغ بعد 45 يوم · نفس التاجر بعملة تانية (بلد تانية)
                txn("other", "2026-10-02", 44_400, Direction.IN, EconomicKind.REFUND_RECEIVED),
                txn("late", "2026-11-15", 40_000, Direction.IN, EconomicKind.REFUND_RECEIVED),
                txn("egp", "2026-10-04", 40_000, Direction.IN, EconomicKind.REFUND_RECEIVED, merchant = "QA CAFE", currency = Currency.EGP),
            ),
        )
        val inflows = LinkInflows(
            LinkInflowsDeps(txns, MemoryAllocationRepository(), MemorySettlementRepository(), MemoryObligationRepository(), MemoryUnitOfWork(listOf(txns)), FixedClock("2026-10-16T09:00:00Z")),
        )
        return txns to QuickLinks(QuickLinksDeps(txns, inflows, null, null))
    }

    @Test fun refundsNearThePurchaseAreOfferedAndLinkInOneTap() = runBlocking<Unit> {
        val (txns, quick) = world(0)
        val got = quick.candidates("buy")
        assertEquals(setOf("ref1", "ref2"), got.filterIsInstance<QuickLink.Refund>().map { it.inflow.id }.toSet())
        assertEquals(null, quick.link("buy", got.first()))
        assertEquals(1, txns.findByIds(listOf("ref1", "ref2")).count { it.linkedExpenseId == "buy" })
        assertTrue(quick.candidates("ref1").isEmpty(), "الداخلة نفسها مالهاش مصدر مرشحين")
    }

    @Test fun atMostThreeAndFastOnTwoThousandFiveHundredOperations() = runBlocking<Unit> {
        val (_, quick) = world(2_500)
        quick.candidates("buy") // تسخين
        var got: List<QuickLink> = emptyList()
        val took = measureTime { got = quick.candidates("buy") }
        assertTrue(got.size <= QuickLinks.LIMIT)
        assertTrue(took.inWholeMilliseconds < 700, "فتح التفاصيل أخد ${took.inWholeMilliseconds}ms")
    }
}
