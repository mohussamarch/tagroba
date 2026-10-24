package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** r06-links2 فجوة ٣: روابط العملية والعكس (الدين والخطة). كل الأسامي والأرقام مخترعة. */
class OperationLinksTest {
    private fun txn(id: String, day: String, wallet: String, amount: Long, dir: Direction = Direction.OUT, name: String = "محل وهمي", to: String? = null) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = wallet, rawMerchantName = name, transferToWalletId = to,
    )

    private val lend = txn("t-lend", "2026-10-01", "w-a", 30_000)
    private val back = txn("t-back", "2026-10-05", "w-a", 10_000, Direction.IN)
    private val pay = txn("t-pay", "2026-10-10", "w-a", 5_000, name = "خدمة وهمية")
    private val plan = InstallmentPlan("ip-1", "جهاز وهمي", "جهة وهمية", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 50_000, 50_000, 5_000, 1, "2026-10-10")
    private val sub = RecurringItem("rec-1", "خدمة وهمية", app.masroufy.core.recurringKey(pay), "subscription", 1, 5_000, Currency.SAR, "2026-10-10", true, true)

    private fun links() = OperationLinks(
        OperationLinksDeps(
            MemoryTransactionRepository(listOf(lend, back, pay)),
            MemoryWalletRepository(listOf(Wallet("w-a", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01"))),
            MemoryPersonRepository(listOf(Person("p-1", "صديق وهمي"))),
            MemoryAllocationRepository(listOf(PersonAllocation("al-1", "t-lend", "p-1", AllocationKind.GIFT, 1_000, Currency.SAR))),
            MemoryPersonLinkRepository(),
            MemoryObligationRepository(listOf(Obligation("obl-1", "p-1", "t-lend", ObligationKind.RECEIVABLE, 30_000, Currency.SAR))),
            MemorySettlementRepository(listOf(Settlement(id = "stl-1", transactionId = "t-back", obligationId = "obl-1", amountMinor = 10_000))),
            MemoryInstallmentPaymentRepository(listOf(InstallmentPayment("pay-1", "ip-1", "t-pay", 5_000))),
            MemoryInstallmentPlanRepository(listOf(plan)),
            MemoryRecurringRepository(listOf(sub)),
            MemoryAssetRepository(), MemoryAssetSaleRepository(), MemoryIncomeSourceRepository(),
        ),
    )

    @Test fun theLendingOperationLinksToItsWalletPersonAndDebt() = runBlocking<Unit> {
        val got = links().of("t-lend").map { Triple(it.kind, it.id, it.name) }
        assertEquals(
            listOf(
                Triple(OpLinkKind.WALLET, "w-a", "بنك وهمي"), Triple(OpLinkKind.PERSON, "p-1", "صديق وهمي"),
                Triple(OpLinkKind.DEBT, "obl-1", "صديق وهمي"),
            ),
            got,
        )
    }

    @Test fun aSettlementLinksToTheDebtAndAPaymentToItsPlanAndSubscription() = runBlocking<Unit> {
        assertEquals(listOf(OpLinkKind.WALLET, OpLinkKind.DEBT), links().of("t-back").map { it.kind })
        val pay = links().of("t-pay")
        assertEquals(listOf(OpLinkKind.WALLET, OpLinkKind.PLAN, OpLinkKind.SUBSCRIPTION), pay.map { it.kind })
        assertEquals(listOf("ip-1", "rec-1"), pay.drop(1).map { it.id })
    }

    @Test fun theDebtAndPlanPagesListTheirOperations() = runBlocking<Unit> {
        assertEquals(listOf("t-lend", "t-back"), links().opsOfDebt("obl-1", "p-1").map { it.transactionId })
        assertEquals(listOf("t-pay"), links().opsOfPlan("ip-1").map { it.transactionId })
        assertEquals(emptyList(), links().of("missing"))
    }
}
