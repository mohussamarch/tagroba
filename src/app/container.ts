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
import { FirestoreBudgetRepository } from '../infrastructure/firestore/budgetRepository'
import { FirestoreWalletRepository } from '../infrastructure/firestore/walletRepository'
import { MemoryAllocationRepository } from '../infrastructure/memory/memoryRepositories'
import { RandomIdGenerator } from '../infrastructure/firestore/randomIdGenerator'
import { buildCategories, loadReferences } from '../infrastructure/import/referenceLoader'
import { makeImportStatement } from '../application/useCases/importStatement'
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
  /** يزرع المراجع الأولية عند أول دخول فقط — ARCHITECTURE.md §10.6. */
  seedUserReferences: () => Promise<SeedOutcome>
  loadHomeScreen: ReturnType<typeof makeLoadHomeScreen>
  loadBudgetScreen: ReturnType<typeof makeLoadBudgetScreen>
  addTransaction: ReturnType<typeof makeAddTransaction>
  reconcileBalance: ReturnType<typeof makeReconcileBalance>
  exportBackup: ReturnType<typeof makeExportBackup>
  seedWallets: ReturnType<typeof makeSeedWallets>
  wallets: WalletRepository
  setBudget: ReturnType<typeof makeSetBudget>
  setEconomicKind: ReturnType<typeof makeSetEconomicKind>
  loadTransactionsScreen: ReturnType<typeof makeLoadTransactionsScreen>
  importStatement: ReturnType<typeof makeImportStatement>
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
      const allocations = new MemoryAllocationRepository()
      const budgets = new FirestoreBudgetRepository(db, uid)
      const wallets = new FirestoreWalletRepository(db, uid)

      /** المرجع الأولي يُبنى من الملفات، ويُزرع مرة واحدة عند أول دخول. */
      const buildSeedSource = () => {
        const categoryList = buildCategories(tokens.categories)
        const refs = loadReferences(rawRules, rawMerchants, categoryList)
        return { categories: categoryList, rules: refs.rules, merchants: refs.merchants }
      }

      return {
        seedUserReferences: () =>
          makeSeedUserReferences({ categories, rules, merchants, uow })(buildSeedSource()),
        loadHomeScreen: makeLoadHomeScreen({ txns, categories, allocations }),
        loadBudgetScreen: makeLoadBudgetScreen({ txns, categories, allocations, budgets }),
        addTransaction: makeAddTransaction({ txns, wallets, ids: new RandomIdGenerator(), clock: systemClock }),
        reconcileBalance: makeReconcileBalance({ txns, wallets }),
        exportBackup: makeExportBackup({
          txns, sources, batches, wallets, categories, rules, merchants, budgets,
        }),
        seedWallets: makeSeedWallets({ wallets }),
        wallets,
        setBudget: makeSetBudget({ budgets, uow, ids: new RandomIdGenerator(), clock: systemClock }),
        setEconomicKind: makeSetEconomicKind({ txns, categories, uow, clock: systemClock }),
        loadTransactionsScreen: makeLoadTransactionsScreen({ txns, categories, allocations }),
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
          settlements: { listByObligations: async () => [], listByTransactionIds: async () => [], saveMany: async () => {}, deleteMany: async () => {} },
          allocations,
          obligations: { listByPerson: async () => [], listByTransactionIds: async () => [], saveMany: async () => {}, deleteMany: async () => {} },
          uow,
        }),
        resumeStagedBatch: makeResumeStagedBatch({ txns, sources, batches }),
      }
    },
  }
}
