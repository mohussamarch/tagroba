package app.masroufy.firestore

import app.masroufy.port.UnitOfWork

/**
 * وحدة العمل على فايربيز — نفس `FirestoreUnitOfWork` في التطبيق الحالي: **بتمرّر الشغل زي ما هو**.
 * ⚠️ معاملة فايربيز ما تقبلش قراية بعد كتابة وحدها 500 عملية، والاستيراد بيكتب مئات المستندات، فالذرّية
 * **لكل دفعة 500 مش للعملية كلها**. «صفر أو كامل الدفعة» (spec/06) بيتحقق ببروتوكول `staged` ⇐ `committed`
 * جوه `ImportStatement` + `ResumeStagedBatch` اللي بيكمّل الدفعة المقطوعة — مش بوحدة العمل دي.
 * مستودعات الذاكرة (`MemoryUnitOfWork`) هي اللي بتثبت الشرط كامل في الاختبارات.
 */
class FirestoreUnitOfWork : UnitOfWork {
    override suspend fun <T> run(work: suspend () -> T): T = work()
}

/**
 * كل المستودعات الحقيقية لحساب واحد — التجميع يدوي من غير مكتبة حقن (CLAUDE.md #6)، زي `app/container.ts`.
 * الشاشات بتاخد حالات الاستخدام من التجميع ده، مش المستودعات نفسها.
 */
class FirestoreContainer(space: FirestoreSpace) {
    val uow = FirestoreUnitOfWork()
    val transactions = FirestoreTransactionRepository(space)
    val sourceRecords = FirestoreSourceRecordRepository(space)
    val importBatches = FirestoreImportBatchRepository(space)
    val wallets = FirestoreWalletRepository(space)
    val categories = FirestoreCategoryRepository(space)
    val rules = FirestoreRuleRepository(space)
    val merchants = FirestoreMerchantRepository(space)
    val people = FirestorePersonRepository(space)
    val obligations = FirestoreObligationRepository(space)
    val settlements = FirestoreSettlementRepository(space)
    val settlementWriter = FirestoreSettlementWriter(space)
    val allocations = FirestoreAllocationRepository(space)
    val tags = FirestoreTagRepository(space)
    val transactionTags = FirestoreTransactionTagRepository(space)
    val budgets = FirestoreBudgetRepository(space)
    val recurring = FirestoreRecurringRepository(space)
    val notificationReceipts = FirestoreNotificationReceiptRepository(space)
    val assets = FirestoreAssetRepository(space)
    val assetLots = FirestoreAssetLotRepository(space)
    val assetSales = FirestoreAssetSaleRepository(space)
    val assetPrices = FirestoreAssetPriceRepository(space)
    val projects = FirestoreProjectRepository(space)
    val projectRules = FirestoreProjectRuleRepository(space)
    val projectLinks = FirestoreProjectLinkRepository(space)
    val roscas = FirestoreRoscaRepository(space)
    val roscaEntries = FirestoreRoscaEntryRepository(space)
    val installmentPlans = FirestoreInstallmentPlanRepository(space)
    val installmentPayments = FirestoreInstallmentPaymentRepository(space)
    val debtTerms = FirestoreDebtTermsRepository(space)
    val fullBackup = FirestoreFullBackup(space)
}
