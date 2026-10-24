package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.computePeriodTotals
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRepairBackup
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.PassthroughUnitOfWork

/** r06-delete — كل المستودعات في الذاكرة للوحة المسح الواحدة. كل الأسامي والمبالغ مخترعة. */
internal class RemovalKit {
    val txns = MemoryTransactionRepository()
    val sources = MemorySourceRecordRepository()
    val tags = MemoryTransactionTagRepository()
    val projects = MemoryProjectLinkRepository()
    val events = MemoryEventLinkRepository()
    val entries = MemoryRoscaEntryRepository()
    val payments = MemoryInstallmentPaymentRepository()
    val plans = MemoryInstallmentPlanRepository()
    val zakat = MemoryZakatPaymentRepository()
    val lots = MemoryAssetLotRepository()
    val sales = MemoryAssetSaleRepository()
    val personLinks = MemoryPersonLinkRepository()
    val allocations = MemoryAllocationRepository()
    val obligations = MemoryObligationRepository()
    val settlements = MemorySettlementRepository()
    val wallets = MemoryWalletRepository()
    val assets = MemoryAssetRepository()
    val roscas = MemoryRoscaRepository()
    val recurring = MemoryRecurringRepository()
    val people = MemoryPersonRepository()
    val merchants = MemoryMerchantRepository()
    val incomeSources = MemoryIncomeSourceRepository()
    val categories = MemoryCategoryRepository()
    val rules = MemoryRuleRepository()
    val terms = MemoryDebtTermsRepository()
    val inbox = MemorySmsInbox()
    val backup = MemoryRepairBackup()
    val goals = app.masroufy.memory.MemorySavingsGoalRepository()
    val goalContributions = app.masroufy.memory.MemoryGoalContributionRepository()
    val removals = Removals(
        RemovalDeps(
            txns, sources, PassthroughUnitOfWork(), FixedClock("2026-10-24T10:00:00Z"),
            RevertLinkDeps(projects, events, tags, entries, payments, plans, zakat, lots, sales, personLinks = personLinks),
            allocations, obligations, settlements, wallets, assets, roscas, recurring, people, merchants, incomeSources, categories,
            rules, terms, inbox, "sa", backup, goals = goals, goalContributions = goalContributions,
        ),
    )

    suspend fun all(): List<Transaction> = txns.listByDateRange("0000-01-01", "9999-12-31T23:59:59Z")

    /** المجاميع (الدخل والمصروف الحقيقي) زي الشاشات — لازم ما تتغيرش بعد «امسحه وسيب العمليات». */
    suspend fun totals() = all().let { rows -> computePeriodTotals(rows, allocations.listByTransactionIds(rows.map { it.id })) }

    /** صورة العمليات بكل حقولها — «تراجع» لازم يرجّعها بالظبط. */
    suspend fun picture(): List<Transaction> = all().sortedBy { it.id }
}

internal fun op(
    id: String,
    amount: Long,
    kind: EconomicKind = EconomicKind.PURCHASE,
    dir: Direction = Direction.OUT,
    walletId: String? = "w-1",
    day: String = "2026-10-10",
    merchantId: String? = null,
    categoryId: String? = null,
    rawMerchantName: String? = null,
    transferToWalletId: String? = null,
) = Transaction(
    id = id, occurredAt = day, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
    observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = categoryId != null, excludedFromBudget = false,
    reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "2026-10-10T08:00:00Z", updatedAt = "2026-10-10T08:00:00Z",
    merchantId = merchantId, categoryId = categoryId, walletId = walletId, rawMerchantName = rawMerchantName, transferToWalletId = transferToWalletId,
)
