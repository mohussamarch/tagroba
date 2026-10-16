package app.masroufy.usecase

import app.masroufy.core.BnplCycle
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * r051 — السرعة: الهيكل بيشغّل `BnplCharges.settle` بعد كل تسجيل، و«الأقساط» بتقرا `LoadBnplPlans` كل فتحة. 3,600 عملية مخترعة في 180 يوم
 * منها 40 خطة تابي/تمارا وخصوماتهم ⇒ الاتنين مع بعض (والمعاينة) أقل من 700 مللي ثانية.
 */
class BnplSpeedTest {
    private fun txn(i: Int, day: String, minor: Long, merchant: String) = Transaction(
        id = "t$i", occurredAt = day, datePrecision = "day", sourceOrder = i, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "x", rawMerchantName = merchant,
    )

    @Test fun settleLoadAndPreviewStayUnder700ms() = runBlocking<Unit> {
        val start = toDayNumber(parseIsoDate("2026-04-20"))
        val seed = (0 until 3_600).map { i -> txn(i, dayNumberToIso(start + i % 179), 1_000L + i * 7 % 50_000, "متجر وهمي ${i % 300}") }
        val firsts = (0 until 40).map { k -> txn(10_000 + k, dayNumberToIso(start + k), 20_000L + k * 100, if (k % 2 == 0) "Tabby" else "Tamara") }
        val seconds = (0 until 40).map { k -> txn(20_000 + k, dayNumberToIso(start + 30 + k), 20_000L + k * 100, if (k % 2 == 0) "Tabby" else "Tamara") }
        val txns = MemoryTransactionRepository(seed + firsts + seconds)
        val payments = MemoryInstallmentPaymentRepository()
        val plans = MemoryInstallmentPlanRepository()
        val clock = FixedClock("2026-10-16T09:00:00.000Z")
        val cats = MemoryCategoryRepository()
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                plans, payments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns)), SequentialIdGenerator(), clock, cats,
            ),
        )
        val bnpl = BnplCharges(BnplChargesDeps(txns, plans, payments, installments, clock, { "2026-10-16" }, categories = cats))
        for (f in firsts) bnpl.newPurchase(f.id, BnplPurchase("مشترى وهمي ${f.id}", null, 4, BnplCycle.MONTHLY))
        val load = LoadBnplPlans(plans, payments, cats)
        bnpl.settle() // تسخين (أول ربط)
        val took = measureTime {
            val again = bnpl.settle()
            assertTrue(again.asks.isEmpty())
            assertEquals(40, load.load("2026-10-16").size)
            repeat(20) { bnpl.preview(25_000, Currency.SAR, "2026-10-16", 6, BnplCycle.TWO_WEEKS) }
        }
        assertEquals(80, payments.listByTransactionIds((firsts + seconds).map { it.id }).size, "every second charge linked by itself")
        assertTrue(took.inWholeMilliseconds < 700, "settle + load took $took")
    }
}
