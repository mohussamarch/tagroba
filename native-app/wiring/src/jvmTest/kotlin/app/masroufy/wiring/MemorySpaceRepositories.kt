package app.masroufy.wiring

import app.masroufy.core.Category
import app.masroufy.core.Transaction
import app.masroufy.core.UserProfile
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryFeedCache
import app.masroufy.memory.MemoryGoalContributionRepository
import app.masroufy.memory.MemoryHttpText
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryInheritanceScenarioRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryMerchantCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryNotificationReceiptRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryOccasionRepository
import app.masroufy.memory.MemoryPersonProfileRepository
import app.masroufy.memory.MemoryPersonRelationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryPrepItemRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryProjectRepository
import app.masroufy.memory.MemoryProjectRuleRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryReservationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySavingsGoalRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemorySpaceRegistry
import app.masroufy.memory.MemorySpaceTransferRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatFactRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.MemoryZakatYearRepository
import app.masroufy.memory.SequentialIdGenerator

/** بلد كاملة بمستودعات الذاكرة — نفس شكل `FirestoreContainer` (الاختبار بيمشي في نفس التجميع اللي الجوال بيمشي فيه). */
fun memorySpaceRepositories(
    wallets: List<Wallet> = emptyList(),
    categories: List<Category> = emptyList(),
    transactions: List<Transaction> = emptyList(),
    profile: UserProfile? = null,
): SpaceRepositories {
    val txns = MemoryTransactionRepository(transactions)
    val sources = MemorySourceRecordRepository()
    val batches = MemoryImportBatchRepository()
    val parties = MemoryTransferPartyRepository()
    val obligations = MemoryObligationRepository()
    val settlements = MemorySettlementRepository()
    return SpaceRepositories(
        uow = MemoryUnitOfWork(listOf(txns, sources, batches, parties)),
        transactions = txns, sourceRecords = sources, importBatches = batches, wallets = MemoryWalletRepository(wallets),
        categories = MemoryCategoryRepository(categories), rules = MemoryRuleRepository(), merchants = MemoryMerchantRepository(),
        merchantCategories = MemoryMerchantCategoryRepository(), people = MemoryPersonRepository(), obligations = obligations,
        settlements = settlements, settlementWriter = MemorySettlementWriter(obligations, settlements), allocations = MemoryAllocationRepository(),
        tags = MemoryTagRepository(), transactionTags = MemoryTransactionTagRepository(), budgets = MemoryBudgetRepository(),
        recurring = MemoryRecurringRepository(), notificationReceipts = MemoryNotificationReceiptRepository(), assets = MemoryAssetRepository(),
        assetLots = MemoryAssetLotRepository(), assetSales = MemoryAssetSaleRepository(), assetPrices = MemoryAssetPriceRepository(),
        projects = MemoryProjectRepository(), projectRules = MemoryProjectRuleRepository(), projectLinks = MemoryProjectLinkRepository(),
        roscas = MemoryRoscaRepository(), roscaEntries = MemoryRoscaEntryRepository(), installmentPlans = MemoryInstallmentPlanRepository(),
        installmentPayments = MemoryInstallmentPaymentRepository(), debtTerms = MemoryDebtTermsRepository(), transferParties = parties,
        zakatFacts = MemoryZakatFactRepository(), zakatYears = MemoryZakatYearRepository(), zakatPayments = MemoryZakatPaymentRepository(),
        lifeEvents = MemoryLifeEventRepository(), eventLinks = MemoryEventLinkRepository(), occasions = MemoryOccasionRepository(),
        personProfiles = MemoryPersonProfileRepository(), personRelations = MemoryPersonRelationRepository(), alertSettings = MemoryAlertSettings(),
        alertInbox = MemoryAlertInbox(), alertReceipts = MemoryAlertReceipts(), savingsGoals = MemorySavingsGoalRepository(),
        goalContributions = MemoryGoalContributionRepository(), inheritanceScenarios = MemoryInheritanceScenarioRepository(),
        incomeSources = MemoryIncomeSourceRepository(), reservations = MemoryReservationRepository(), eventPrep = MemoryPrepItemRepository(),
        profile = MemoryProfileRepository(profile), spaces = MemorySpaceRegistry(), spaceTransfers = MemorySpaceTransferRepository(),
    )
}

/** الجهاز في الاختبار: نفس اليوم والساعة دايمًا، ومعرّفات متسلسلة، ونت وهمي. */
fun memoryEnv(today: String = "2026-10-09", http: MemoryHttpText = MemoryHttpText()) = DeviceEnv(
    clock = FixedClock("${today}T07:00:00.000Z"), ids = SequentialIdGenerator(), today = { today }, hourNow = { 9 }, nowMillis = { 1_000L },
    interactions = MemoryAlertInteractions(), usualHours = MemoryUsualHours(), seenAlerts = MemorySeenAlerts(), http = http, feedCache = MemoryFeedCache(),
)
