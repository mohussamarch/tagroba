package app.masroufy.wiring

import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AlertSettingsStore
import app.masroufy.port.AllocationRepository
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.BudgetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.FullBackupPort
import app.masroufy.port.SpacesBackupPort
import app.masroufy.port.GoalContributionRepository
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.InheritanceScenarioRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.NotificationReceiptRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.OccasionRepository
import app.masroufy.port.PersonProfileRepository
import app.masroufy.port.PersonRelationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.PrepItemRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.ProjectRuleRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.ReservationRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.RuleRepository
import app.masroufy.port.SavingsGoalRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SettlementWriter
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.SpaceRegistry
import app.masroufy.port.SpaceTransferRepository
import app.masroufy.port.TagRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransactionTagRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository
import app.masroufy.port.ZakatFactRepository
import app.masroufy.port.ZakatPaymentRepository
import app.masroufy.port.ZakatYearRepository
import app.masroufy.usecase.AssistantStores

/**
 * مستودعات بلد واحدة في حساب — **واجهات بس** (ports من `:app`)، بنفس أسماء `FirestoreContainer` عشان التحويل يبقى سطر بسطر.
 * المشترك على مستوى الحساب (الملف · الأشخاص · التجار · الوسوم · المناسبات · التنبيهات · الخطط · الورث · سجل البلاد) بييجي هنا برضه
 * (نفس الكائن في كل البلاد). `:androidApp` بيبنيه من `FirestoreContainer` (`FirestoreRepositories.kt`)، والاختبار من مستودعات الذاكرة.
 * **مستودع جديد محتاجه منطقة:** ضيفه هنا + في التحويل في `:androidApp` + في `memorySpaceRepositories` (الاختبار).
 */
data class SpaceRepositories(
    val uow: UnitOfWork,
    val transactions: TransactionRepository,
    val sourceRecords: SourceRecordRepository,
    val importBatches: ImportBatchRepository,
    val wallets: WalletRepository,
    val categories: CategoryRepository,
    val rules: RuleRepository,
    val merchants: MerchantRepository,
    val merchantCategories: MerchantCategoryRepository,
    val people: PersonRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val settlementWriter: SettlementWriter,
    val allocations: AllocationRepository,
    val tags: TagRepository,
    val transactionTags: TransactionTagRepository,
    val budgets: BudgetRepository,
    val recurring: RecurringRepository,
    val notificationReceipts: NotificationReceiptRepository,
    val assets: AssetRepository,
    val assetLots: AssetLotRepository,
    val assetSales: AssetSaleRepository,
    val assetPrices: AssetPriceRepository,
    val projects: ProjectRepository,
    val projectRules: ProjectRuleRepository,
    val projectLinks: ProjectLinkRepository,
    val roscas: RoscaRepository,
    val roscaEntries: RoscaEntryRepository,
    val installmentPlans: InstallmentPlanRepository,
    val installmentPayments: InstallmentPaymentRepository,
    val debtTerms: DebtTermsRepository,
    val transferParties: TransferPartyRepository,
    val zakatFacts: ZakatFactRepository,
    val zakatYears: ZakatYearRepository,
    val zakatPayments: ZakatPaymentRepository,
    val lifeEvents: LifeEventRepository,
    val eventLinks: EventLinkRepository,
    val occasions: OccasionRepository,
    val personProfiles: PersonProfileRepository,
    val personRelations: PersonRelationRepository,
    val alertSettings: AlertSettingsStore,
    val alertInbox: AlertInboxStore,
    val alertReceipts: AlertReceiptStore,
    val savingsGoals: SavingsGoalRepository,
    val goalContributions: GoalContributionRepository,
    val inheritanceScenarios: InheritanceScenarioRepository,
    val incomeSources: IncomeSourceRepository,
    val reservations: ReservationRepository,
    val eventPrep: PrepItemRepository,
    val profile: ProfileRepository,
    val spaces: SpaceRegistry,
    val spaceTransfers: SpaceTransferRepository,
    /** المساعد «مصروفي» (§78 · §79.2): المحادثات والرسايل والمواضيع والعلامات والأسئلة وإعدادات المستخدم والإشعارات الممسوحة — كلها على الحساب. */
    val assistant: AssistantStores,
    /** النسخة الشاملة للحساب كله (منطقة «المزيد» — `FullBackup`). null = مش متوصلة (اختبار من غيرها) ⇒ الشاشة بتقول «غير متاح بعد». */
    val fullBackup: FullBackupPort? = null,
    /** البلاد التانية في النسخة الشاملة (الإصدار 3). */
    val spacesBackup: SpacesBackupPort? = null,
)
