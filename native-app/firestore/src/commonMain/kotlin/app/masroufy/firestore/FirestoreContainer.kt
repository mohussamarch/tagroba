package app.masroufy.firestore

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.port.MerchantRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.usecase.SpaceMerchantRepository

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
 * كل المستودعات الحقيقية لبلد واحدة في حساب — التجميع يدوي من غير مكتبة حقن (CLAUDE.md #6)، زي `app/container.ts`.
 * الشاشات بتاخد حالات الاستخدام من التجميع ده، مش المستودعات نفسها.
 *
 * **حساب لكل بلد (§41 · §64):** الملف والأشخاص والتجار والوسوم والمناسبات وسجل البلاد على [account]؛ الباقي كله على [space].
 * السعودية ([DEFAULT_SPACE_ID]) = نفس المكان `users/{uid}` (بيانات التطبيق الحالي) بكائنين منفصلين. في أي بلد تانية تصنيف التاجر
 * بيتخزن في البلد نفسها (`SpaceMerchantRepository`) ومش بيلمس التاجر المشترك.
 */
class FirestoreContainer(val accountRoot: FirestoreSpace, val spaceRoot: FirestoreSpace, val spaceId: String) {
    private val account = accountRoot
    private val space = spaceRoot

    /** حساب بمساحة واحدة (السعودية) في نفس المكان — زي ما كان قبل البلاد. */
    constructor(root: FirestoreSpace) : this(root, root, DEFAULT_SPACE_ID)

    val uow = FirestoreUnitOfWork()
    val transactions = FirestoreTransactionRepository(space)
    val sourceRecords = FirestoreSourceRecordRepository(space)
    val importBatches = FirestoreImportBatchRepository(space)
    val wallets = FirestoreWalletRepository(space)
    val categories = FirestoreCategoryRepository(space)
    val rules = FirestoreRuleRepository(space)
    /** التاجر المشترك على مستوى الحساب (§41). */
    val sharedMerchantRecords = FirestoreMerchantRepository(account)
    val merchantCategories = FirestoreMerchantCategoryRepository(space)
    val merchants: MerchantRepository =
        if (spaceId == DEFAULT_SPACE_ID) sharedMerchantRecords else SpaceMerchantRepository(sharedMerchantRecords, merchantCategories)
    val people = FirestorePersonRepository(account)
    val obligations = FirestoreObligationRepository(space)
    val settlements = FirestoreSettlementRepository(space)
    val settlementWriter = FirestoreSettlementWriter(space)
    val allocations = FirestoreAllocationRepository(space)
    val tags = FirestoreTagRepository(account)
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
    val transferParties = FirestoreTransferPartyRepository(space)
    val zakatFacts = FirestoreZakatFactRepository(space)
    val zakatYears = FirestoreZakatYearRepository(space)
    val zakatPayments = FirestoreZakatPaymentRepository(space)
    val lifeEvents = FirestoreLifeEventRepository(space)
    val eventLinks = FirestoreEventLinkRepository(space)
    val occasions = FirestoreOccasionRepository(account)
    val incomeSources = FirestoreIncomeSourceRepository(space)

    /** النسخة الشاملة للحساب كله (§41.1) — من `users/{uid}` (الحساب + السعودية). البلاد التانية في الإصدار 3. */
    val fullBackup = FirestoreFullBackup(account, if (spaceId == DEFAULT_SPACE_ID && space !== account) listOf(space) else emptyList())

    /** يوم المرتب واحد للحساب كله (رد المالك §64-١) ⇒ الملف على مستوى الحساب. */
    val profile = FirestoreProfileRepository(account)
    val referenceSeed = FirestoreReferenceSeed(space, merchantsAt = account)
    val sharedMerchants = FirestoreSharedMerchantCatalog(space.db)
    val spaces = FirestoreSpaceRegistry(account)

    /** أزواج التحويل لنفسك (على مستوى الحساب). الكتابة الذرّية (`FirestoreSpaceTransferWriter`) محتاجة كل البلاد ⇒ بتتعمل من الجلسة. */
    val spaceTransfers = FirestoreSpaceTransferRepository(account)
}
