import { makeLoadHomeHistory } from '../application/useCases/loadHomeHistory'
import { createHomeSnapshot } from '../infrastructure/homeSnapshot'
import { makeReadBankSms } from '../application/useCases/readBankSms'
import { makeFullBackup } from '../application/useCases/fullBackup'
import { makeRepairStoredIds } from '../application/useCases/repairStoredIds'
import { firestoreIdRepair } from '../infrastructure/firestore/idRepairRepository'
import { sanitizeAccountNumbers } from '../infrastructure/firestore/firestoreRepositories'
import { firestoreFullBackup } from '../infrastructure/firestore/fullBackupRepository'
import { backupDigest } from '../infrastructure/backupDigest'
import { makeManageSmsInbox } from '../application/useCases/manageSmsInbox'
import { androidSmsInbox } from '../infrastructure/androidSmsInbox'
import { androidBankSms } from '../infrastructure/androidBankSms'
import { parseBankSms } from '../infrastructure/import/bankSmsParser'
import { saveTextFile } from '../infrastructure/saveTextFile'
import { deviceRepairBackup } from '../infrastructure/repairBackup'
import { makeCleanupOrphans } from '../application/useCases/cleanupOrphans'
import { makeMigrateCategories } from '../application/useCases/migrateCategories'
import { makeRestoreDefaultReferences } from '../application/useCases/restoreDefaultReferences'
import { firestoreRemoveDocs } from '../infrastructure/firestore/removeDocs'
import { makeManageProfile } from '../application/useCases/manageProfile'
import { FirestoreProfileRepository } from '../infrastructure/firestore/firestoreProfileRepository'
import { firebaseAccount } from '../infrastructure/firestore/firebaseAccount'
import { makeOnboardAccount } from '../application/useCases/onboardAccount'
import { coalesceReads } from '../infrastructure/coalesceReads'
import { makeManageCategories } from '../application/useCases/manageCategories'
import { makeReviewHistory } from '../application/useCases/reviewHistory'
import { makeManageRecurring } from '../application/useCases/manageRecurring'
import { FirestoreRecurringRepository } from '../infrastructure/firestore/recurringRepository'
import { db } from '../infrastructure/firestore/firebase'
import { FirebaseAuthAdapter } from '../infrastructure/firestore/FirebaseAuthAdapter'
import {
  FirestoreImportBatchRepository,
  FirestoreSourceRecordRepository,
  FirestoreTransactionRepository,
  FirestoreUnitOfWork,
} from '../infrastructure/firestore/firestoreRepositories'
import {
  FirestoreCategoryRepository,
  FirestoreMerchantRepository,
  FirestoreRuleRepository,
} from '../infrastructure/firestore/referenceRepositories'
import {
  FirestoreAllocationRepository,
  FirestoreObligationRepository,
  FirestorePersonRepository,
  FirestoreSettlementRepository,
} from '../infrastructure/firestore/peopleRepositories'
import {
  FirestoreAssetLotRepository,
  FirestoreAssetPriceRepository,
  FirestoreAssetRepository,
  FirestoreAssetSaleRepository,
} from '../infrastructure/firestore/assetRepositories'
import { FirestoreNotificationReceiptRepository } from '../infrastructure/firestore/notificationRepository'
import {
  FirestoreTagRepository,
  FirestoreTransactionTagRepository,
} from '../infrastructure/firestore/tagRepositories'
import { FirestoreBudgetRepository } from '../infrastructure/firestore/budgetRepository'
import { FirestoreWalletRepository } from '../infrastructure/firestore/walletRepository'
import { RandomIdGenerator } from '../infrastructure/firestore/randomIdGenerator'
import { loadReferences } from '../infrastructure/import/referenceLoader'
import { buildCategoryTree } from '../infrastructure/import/categoryTreeLoader'
import { makeImportStatement } from '../application/useCases/importStatement'
import { makeReviewSmsInbox } from '../application/useCases/reviewSmsInbox'
import { makeReadPdfStatement } from '../application/useCases/readPdfStatement'
import { makeCategorizeTransactions } from '../application/useCases/categorizeTransactions'
import { makeRevertImportBatch } from '../application/useCases/revertImportBatch'
import { makeLoadTransactionsScreen } from '../application/useCases/loadTransactionsScreen'
import { makeResumeStagedBatch } from '../application/useCases/resumeStagedBatch'
import { makeSeedUserReferences, type SeedOutcome } from '../application/useCases/seedUserReferences'
import { firestoreReferenceSeed } from '../infrastructure/firestore/referenceSeed'
import { firestoreSettlementWriter } from '../infrastructure/firestore/settlementWriter'
import { makeLoadHomeScreen } from '../application/useCases/loadHomeScreen'
import { makeSetEconomicKind } from '../application/useCases/setEconomicKind'
import { makeLoadBudgetScreen } from '../application/useCases/loadBudgetScreen'
import { makeSetBudget } from '../application/useCases/setBudget'
import { makeReconcileBalance } from '../application/useCases/reconcileBalance'
import { makeExportBackup } from '../application/useCases/exportBackup'
import { makeSeedWallets } from '../application/useCases/seedWallets'
import { makeAddTransaction } from '../application/useCases/addTransaction'
import { makeManagePeople } from '../application/useCases/managePeople'
import { makeEditTransaction } from '../application/useCases/editTransaction'
import { makeManageRules } from '../application/useCases/manageRules'
import { makeSharedMerchants } from '../application/useCases/sharedMerchants'
import { FirestoreSharedMerchantCatalog } from '../infrastructure/firestore/sharedMerchantCatalogRepository'
import { localSyncCursor } from '../infrastructure/localSyncCursor'
import { firestoreProjects } from './projectsWiring'
import { createMerchantLogos } from '../infrastructure/logos/merchantLogos'
import { makeLoadCashSummary } from '../application/useCases/loadCashSummary'
import { makeRepairBudgetIds } from '../application/useCases/repairBudgetIds'
import { makeRestoreBackup } from '../application/useCases/restoreBackup'
import { makeManageAssets } from '../application/useCases/manageAssets'
import { makeSyncAssetPrices } from '../application/useCases/syncAssetPrices'
import { makeLoadNotifications } from '../application/useCases/loadNotifications'
import { loadPriceFeed } from '../infrastructure/prices/loadPriceFeed'
import type { AuthPort } from '../application/ports/AuthPort'
import type { Clock, WalletRepository } from '../application/ports/repositories'
import categoryTreeData from '../infrastructure/import/categoryTree.json'
import { LEGACY_CATEGORY_NAMES } from '../infrastructure/import/legacyCategoryNames'
import rawRules from '../../design-source/masroofi-claude-code/fixtures/rule-reference.json'
import rawMerchants from '../../design-source/masroofi-claude-code/fixtures/merchant-reference.json'

/**
 * نقطة التجميع الوحيدة (ARCHITECTURE.md §3).
 * هنا فقط تُربط الواجهات بتنفيذها. لا مكتبة حقن اعتماديات.
 *
 * كل ما يخص المستخدم على Firestore تحت users/{uid}: العمليات والدفعات
 * وسجلات المصدر والتصنيفات والقواعد والتجار.
 *
 * المراجع الأولية (rule-reference.json و merchant-reference.json) تُقرأ
 * من الملفات **مرة واحدة عند أول دخول** ثم تُزرع في تخزين المستخدم،
 * فتبقى «قابلة للتحرير» كما ينص spec/05 ولا يضيع التعديل عند إعادة الفتح.
 *
 * ⚠️ الباقي في الذاكرة: `allocations` — تخصيصات الأشخاص لم تُبنَ شاشتها بعد،
 * وهي عمل شريحة الأشخاص والديون (المرحلة الخامسة).
 */
const systemClock: Clock = { nowIso: () => new Date().toISOString() }
export interface Container {
  auth: AuthPort
  /** يبني بقية القطع بعد معرفة هوية المستخدم. */
  forUser(uid: string): UserContainer
}
export interface UserContainer {
  fullBackup: ReturnType<typeof makeFullBackup>
  repairStoredIds: ReturnType<typeof makeRepairStoredIds>
  /** بقايا استيراد متراجَع عنه — HANDOVER §36. */
  cleanupOrphans: ReturnType<typeof makeCleanupOrphans>
  /** نقل الحساب القديم لشجرة التصنيفات — OVERRIDES §28.1. */ migrateCategories: ReturnType<typeof makeMigrateCategories>
  /** رجوع القواعد والتجار الافتراضيين بمعاينة — OVERRIDES §28.1. */ restoreDefaultReferences: ReturnType<typeof makeRestoreDefaultReferences>
  /** قاعدة التجار المشتركة — OVERRIDES §25. */ sharedMerchants: ReturnType<typeof makeSharedMerchants>
  /** شعارات التجار — OVERRIDES §25.1. */ merchantLogos: ReturnType<typeof createMerchantLogos>
  /** كارت الكاش وتفاصيله — OVERRIDES §32. */ loadCashSummary: ReturnType<typeof makeLoadCashSummary>
  /** تصليح معرّف الميزانيات القديمة بنسخة — موافقة المالك 2026-09-15. */ repairBudgetIds: ReturnType<typeof makeRepairBudgetIds>
  /** قسم الحساب وأسئلة البداية — OVERRIDES §26. */
  manageProfile: ReturnType<typeof makeManageProfile>
  onboarding: ReturnType<typeof makeOnboardAccount>
  homeSnapshot: ReturnType<typeof createHomeSnapshot>
  loadHomeHistory: ReturnType<typeof makeLoadHomeHistory>
  readBankSms: ReturnType<typeof makeReadBankSms>
  smsInbox: ReturnType<typeof makeManageSmsInbox>
  /** شاشة رسايل البنك — OVERRIDES §36. */ smsReview: ReturnType<typeof makeReviewSmsInbox>
  saveTextFile: typeof saveTextFile
  manageCategories: ReturnType<typeof makeManageCategories>
  reviewHistory: ReturnType<typeof makeReviewHistory>
  manageRecurring: ReturnType<typeof makeManageRecurring>
  manageProjects: ReturnType<typeof firestoreProjects>
  /** يزرع المراجع الأولية عند أول دخول فقط — ARCHITECTURE.md §10.6. */
  seedUserReferences: () => Promise<SeedOutcome>
  loadHomeScreen: ReturnType<typeof makeLoadHomeScreen>
  loadBudgetScreen: ReturnType<typeof makeLoadBudgetScreen>
  addTransaction: ReturnType<typeof makeAddTransaction>
  managePeople: ReturnType<typeof makeManagePeople>
  editTransaction: ReturnType<typeof makeEditTransaction>
  manageRules: ReturnType<typeof makeManageRules>
  restoreBackup: ReturnType<typeof makeRestoreBackup>
  manageAssets: ReturnType<typeof makeManageAssets>
  syncAssetPrices: ReturnType<typeof makeSyncAssetPrices>
  loadNotifications: ReturnType<typeof makeLoadNotifications>
  /** يجيب ملف الأسعار — الشبكة هنا فقط، والشاشة ما تعرفش مكانه. */
  loadPriceFeed: typeof loadPriceFeed
  reconcileBalance: ReturnType<typeof makeReconcileBalance>
  exportBackup: ReturnType<typeof makeExportBackup>
  seedWallets: ReturnType<typeof makeSeedWallets>
  wallets: WalletRepository
  setBudget: ReturnType<typeof makeSetBudget>
  setEconomicKind: ReturnType<typeof makeSetEconomicKind>
  loadTransactionsScreen: ReturnType<typeof makeLoadTransactionsScreen>
  importStatement: ReturnType<typeof makeImportStatement>
  /** يقرأ كشف الراجحي PDF لصفوف موحّدة — ARCHITECTURE §19. */
  readPdfStatement: ReturnType<typeof makeReadPdfStatement>
  categorizeTransactions: ReturnType<typeof makeCategorizeTransactions>
  revertImportBatch: ReturnType<typeof makeRevertImportBatch>
  /** ينظّف الدفعات المعلّقة عند فتح التطبيق — ARCHITECTURE.md §10.5. */
  resumeStagedBatch: ReturnType<typeof makeResumeStagedBatch>
}
export function createContainer(): Container {
  return {
    auth: new FirebaseAuthAdapter(),

    forUser(uid: string): UserContainer {
      const txns = coalesceReads(new FirestoreTransactionRepository(db, uid))
      const sources = new FirestoreSourceRecordRepository(db, uid)
      const batches = new FirestoreImportBatchRepository(db, uid)
      const uow = new FirestoreUnitOfWork()

      // المراجع محفوظة في تخزين المستخدم فتبقى **قابلة للتحرير** (spec/05)
      const categories = coalesceReads(new FirestoreCategoryRepository(db, uid))
      const merchants = new FirestoreMerchantRepository(db, uid)
      const rules = new FirestoreRuleRepository(db, uid)
      // الأشخاص والديون محفوظون في تخزين المستخدم (المرحلة الخامسة)
      const allocations = coalesceReads(new FirestoreAllocationRepository(db, uid))
      const people = new FirestorePersonRepository(db, uid)
      const obligations = new FirestoreObligationRepository(db, uid)
      const settlements = new FirestoreSettlementRepository(db, uid)
      const budgets = new FirestoreBudgetRepository(db, uid)
      const wallets = new FirestoreWalletRepository(db, uid)
      // الوسوم وروابطها (spec/02 — الوسم مبيضاعفش المبلغ)
      const tags = new FirestoreTagRepository(db, uid)
      const transactionTags = new FirestoreTransactionTagRepository(db, uid)
      // الاستثمار (المرحلة السابعة) — أصول ودفعات ومبيعات وأسعار
      const notificationReceipts = new FirestoreNotificationReceiptRepository(db, uid)
      const assets = new FirestoreAssetRepository(db, uid)
      const assetLots = new FirestoreAssetLotRepository(db, uid)
      const assetSales = new FirestoreAssetSaleRepository(db, uid)
      const assetPrices = new FirestoreAssetPriceRepository(db, uid)

      /** المرجع الأولي من الملفات (شجرة التصنيفات OVERRIDES §28.1): يُزرع مرة واحدة عند أول دخول، ويتنقل ليه الحساب القديم. */
      const categoryTree = buildCategoryTree(categoryTreeData)
      const seedRefs = loadReferences(rawRules, rawMerchants, categoryTree.categories, categoryTree)
      const buildSeedSource = () => ({ categories: categoryTree.categories, rules: seedRefs.rules, merchants: seedRefs.merchants })

      // مبنيين مرة واحدة لأن أسئلة البداية بتستعملهم كمان
      const manageProfile = makeManageProfile({profiles:new FirestoreProfileRepository(db,uid),account:firebaseAccount,clock:systemClock})
      const managePeople = makeManagePeople({ people, obligations, settlements, settlementWriter: firestoreSettlementWriter(db, uid), allocations, txns, uow, ids: new RandomIdGenerator(), clock: systemClock })
      const sharedMerchants = makeSharedMerchants({ catalog: new FirestoreSharedMerchantCatalog(db), merchants, baseline: seedRefs.merchants, treeCategoryIds: new Set(categoryTree.categories.map((c) => c.id)), cursor: localSyncCursor(uid), confirmedCursor: localSyncCursor(uid, 'shared-merchants-confirmed'), clock: systemClock })

      const importStatement = makeImportStatement({ txns, sources, batches, merchants, categories, rules, uow, ids: new RandomIdGenerator(), clock: systemClock })
      const smsInbox = makeManageSmsInbox(androidSmsInbox(uid), parseBankSms)
      return {
        readBankSms: makeReadBankSms(androidBankSms, parseBankSms),
        smsInbox,
        smsReview: makeReviewSmsInbox({ inbox: smsInbox, importer: importStatement, merchants, categories, ids: new RandomIdGenerator(), contribute: sharedMerchants.contribute }),
        homeSnapshot: createHomeSnapshot(uid),
        loadHomeHistory: makeLoadHomeHistory({txns,allocations,categories}),
        saveTextFile,
        fullBackup: makeFullBackup(firestoreFullBackup(db,uid),backupDigest),
        repairStoredIds: makeRepairStoredIds({port:firestoreIdRepair(db,uid),redact:sanitizeAccountNumbers,backup:deviceRepairBackup,clock:systemClock}),
        cleanupOrphans: makeCleanupOrphans({port:firestoreIdRepair(db,uid),remover:firestoreRemoveDocs(db,uid),redact:sanitizeAccountNumbers,backup:deviceRepairBackup,clock:systemClock}),
        migrateCategories: makeMigrateCategories({port:firestoreIdRepair(db,uid),categories,backup:deviceRepairBackup,clock:systemClock,tree:categoryTree,legacyNames:LEGACY_CATEGORY_NAMES}),
        restoreDefaultReferences: makeRestoreDefaultReferences({port:firestoreIdRepair(db,uid),rules,merchants,backup:deviceRepairBackup,clock:systemClock,defaults:seedRefs}),
        manageProfile,
        onboarding: makeOnboardAccount({ profile: manageProfile, people: managePeople, wallets, clock: systemClock }),
        manageCategories: makeManageCategories({categories,ids:new RandomIdGenerator()}),
        reviewHistory: makeReviewHistory({txns,merchants,categories,rules,uow,clock:systemClock}),
        manageRecurring: makeManageRecurring({items:new FirestoreRecurringRepository(db,uid),txns,categories,ids:new RandomIdGenerator()}),
        seedUserReferences: () =>
          makeSeedUserReferences({ categories, rules, merchants, progress: firestoreReferenceSeed(db, uid) })(buildSeedSource()),
        loadHomeScreen: makeLoadHomeScreen({ txns, categories, allocations, budgets }),
        loadBudgetScreen: makeLoadBudgetScreen({ txns, categories, allocations, budgets }),
        managePeople,
        manageAssets: makeManageAssets({ assets, lots: assetLots, sales: assetSales, prices: assetPrices, ids: new RandomIdGenerator(), clock: systemClock }),
        manageProjects: firestoreProjects(db, uid, { txns, allocations, categories }),
        loadNotifications: makeLoadNotifications({
          receipts: notificationReceipts,
          clock: systemClock,
        }),
        syncAssetPrices: makeSyncAssetPrices({ assets, prices: assetPrices }),
        loadPriceFeed,
        sharedMerchants,
        editTransaction: makeEditTransaction({ txns, categories, tags, transactionTags, uow, ids: new RandomIdGenerator(), clock: systemClock, onCategoryConfirmed: sharedMerchants.contribute, allocations, settlements }),
        manageRules: makeManageRules({
          rules,
          merchants,
          categories,
          ids: new RandomIdGenerator(),
        }),
        restoreBackup: makeRestoreBackup({ txns, wallets, categories, rules, merchants, budgets, uow }),
        merchantLogos: createMerchantLogos({ clientId: import.meta.env.VITE_BRANDFETCH_CLIENT_ID as string | undefined }),
        loadCashSummary: makeLoadCashSummary({ wallets, txns, allocations, categories }),
        repairBudgetIds: makeRepairBudgetIds({ port: firestoreIdRepair(db, uid), backup: deviceRepairBackup, clock: systemClock }),
        addTransaction: makeAddTransaction({ txns, wallets, ids: new RandomIdGenerator(), clock: systemClock }),
        reconcileBalance: makeReconcileBalance({ txns, wallets }),
        exportBackup: makeExportBackup({
          txns, sources, batches, wallets, categories, rules, merchants, budgets,
        }),
        seedWallets: makeSeedWallets({ wallets }),
        wallets,
        setBudget: makeSetBudget({ budgets, uow, ids: new RandomIdGenerator(), clock: systemClock }),
        setEconomicKind: makeSetEconomicKind({ txns, categories, uow, clock: systemClock }),
        loadTransactionsScreen: makeLoadTransactionsScreen({ txns, categories, allocations, tags, transactionTags, merchants }),
        readPdfStatement: makeReadPdfStatement(),
        importStatement,
        categorizeTransactions: makeCategorizeTransactions({ txns, merchants, categories, rules, uow, clock: systemClock }),
        revertImportBatch: makeRevertImportBatch({
          txns,
          sources,
          batches,
          settlements,
          allocations,
          obligations,
          uow,
          // نسخة مؤكدة الحجم قبل الحذف — HANDOVER §36
          backup: deviceRepairBackup,
          clock: systemClock,
        }),
        resumeStagedBatch: makeResumeStagedBatch({ txns, sources, batches }),
      }
    },
  }
}
