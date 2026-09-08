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
import { buildCategories, loadReferences } from '../infrastructure/import/referenceLoader'
import { makeImportStatement } from '../application/useCases/importStatement'
import { makeReadPdfStatement } from '../application/useCases/readPdfStatement'
import { makeCategorizeTransactions } from '../application/useCases/categorizeTransactions'
import { makeRevertImportBatch } from '../application/useCases/revertImportBatch'
import { makeLoadTransactionsScreen } from '../application/useCases/loadTransactionsScreen'
import { makeResumeStagedBatch } from '../application/useCases/resumeStagedBatch'
import { makeSeedUserReferences, type SeedOutcome } from '../application/useCases/seedUserReferences'
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
import { makeRestoreBackup } from '../application/useCases/restoreBackup'
import { makeManageAssets } from '../application/useCases/manageAssets'
import { makeSyncAssetPrices } from '../application/useCases/syncAssetPrices'
import { makeLoadNotifications } from '../application/useCases/loadNotifications'
import { loadPriceFeed } from '../infrastructure/prices/loadPriceFeed'
import type { AuthPort } from '../application/ports/AuthPort'
import type { Clock, WalletRepository } from '../application/ports/repositories'
import tokens from '../../design-source/masroofi-claude-code/design/tokens.json'
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
  manageCategories: ReturnType<typeof makeManageCategories>
  reviewHistory: ReturnType<typeof makeReviewHistory>
  manageRecurring: ReturnType<typeof makeManageRecurring>
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
      const txns = new FirestoreTransactionRepository(db, uid)
      const sources = new FirestoreSourceRecordRepository(db, uid)
      const batches = new FirestoreImportBatchRepository(db, uid)
      const uow = new FirestoreUnitOfWork()

      // المراجع محفوظة في تخزين المستخدم فتبقى **قابلة للتحرير** (spec/05)
      const categories = new FirestoreCategoryRepository(db, uid)
      const merchants = new FirestoreMerchantRepository(db, uid)
      const rules = new FirestoreRuleRepository(db, uid)
      // الأشخاص والديون محفوظون في تخزين المستخدم (المرحلة الخامسة)
      const allocations = new FirestoreAllocationRepository(db, uid)
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

      /** المرجع الأولي يُبنى من الملفات، ويُزرع مرة واحدة عند أول دخول. */
      const buildSeedSource = () => {
        const categoryList = buildCategories(tokens.categories)
        const refs = loadReferences(rawRules, rawMerchants, categoryList)
        return { categories: categoryList, rules: refs.rules, merchants: refs.merchants }
      }

      return {
        manageCategories: makeManageCategories({categories,ids:new RandomIdGenerator()}),
        reviewHistory: makeReviewHistory({txns,merchants,categories,rules,uow,clock:systemClock}),
        manageRecurring: makeManageRecurring({items:new FirestoreRecurringRepository(db,uid),txns,categories,ids:new RandomIdGenerator()}),
        seedUserReferences: () =>
          makeSeedUserReferences({ categories, rules, merchants, uow })(buildSeedSource()),
        loadHomeScreen: makeLoadHomeScreen({ txns, categories, allocations }),
        loadBudgetScreen: makeLoadBudgetScreen({ txns, categories, allocations, budgets }),
        managePeople: makeManagePeople({
          people, obligations, settlements, allocations, txns, uow,
          ids: new RandomIdGenerator(), clock: systemClock,
        }),
        manageAssets: makeManageAssets({
          assets,
          lots: assetLots,
          sales: assetSales,
          prices: assetPrices,
          ids: new RandomIdGenerator(),
          clock: systemClock,
        }),
        loadNotifications: makeLoadNotifications({
          receipts: notificationReceipts,
          clock: systemClock,
        }),
        syncAssetPrices: makeSyncAssetPrices({ assets, prices: assetPrices }),
        loadPriceFeed,
        editTransaction: makeEditTransaction({
          txns,
          categories,
          tags,
          transactionTags,
          uow,
          ids: new RandomIdGenerator(),
          clock: systemClock,
        }),
        manageRules: makeManageRules({
          rules,
          merchants,
          categories,
          ids: new RandomIdGenerator(),
        }),
        restoreBackup: makeRestoreBackup({
          txns, wallets, categories, rules, merchants, budgets, uow,
        }),
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
        importStatement: makeImportStatement({
          txns,
          sources,
          batches,
          merchants,
          categories,
          rules,
          uow,
          ids: new RandomIdGenerator(),
          clock: systemClock,
        }),
        categorizeTransactions: makeCategorizeTransactions({
          txns,
          merchants,
          categories,
          rules,
          uow,
          clock: systemClock,
        }),
        revertImportBatch: makeRevertImportBatch({
          txns,
          sources,
          batches,
          settlements,
          allocations,
          obligations,
          uow,
        }),
        resumeStagedBatch: makeResumeStagedBatch({ txns, sources, batches }),
      }
    },
  }
}
