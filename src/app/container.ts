import { db } from '../infrastructure/firestore/firebase'
import { FirebaseAuthAdapter } from '../infrastructure/firestore/FirebaseAuthAdapter'
import {
  FirestoreImportBatchRepository,
  FirestoreSourceRecordRepository,
  FirestoreTransactionRepository,
  FirestoreUnitOfWork,
} from '../infrastructure/firestore/firestoreRepositories'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
} from '../infrastructure/memory/memoryRepositories'
import { RandomIdGenerator } from '../infrastructure/firestore/randomIdGenerator'
import { buildCategories, loadReferences } from '../infrastructure/import/referenceLoader'
import { makeImportStatement } from '../application/useCases/importStatement'
import { makeCategorizeTransactions } from '../application/useCases/categorizeTransactions'
import { makeRevertImportBatch } from '../application/useCases/revertImportBatch'
import { makeLoadTransactionsScreen } from '../application/useCases/loadTransactionsScreen'
import type { AuthPort } from '../application/ports/AuthPort'
import type { Clock } from '../application/ports/repositories'
import tokens from '../../design-source/masroofi-claude-code/design/tokens.json'
import rawRules from '../../design-source/masroofi-claude-code/fixtures/rule-reference.json'
import rawMerchants from '../../design-source/masroofi-claude-code/fixtures/merchant-reference.json'

/**
 * نقطة التجميع الوحيدة (ARCHITECTURE.md §3).
 * هنا فقط تُربط الواجهات بتنفيذها. لا مكتبة حقن اعتماديات.
 *
 * ⚠️ حالة المرحلة الأولى الصريحة:
 * العمليات ودفعات الاستيراد وسجلات المصدر **على Firestore**.
 * التصنيفات والقواعد والتجار **في الذاكرة من المراجع الأولية** —
 * لأن كتابتها لفايربيز عملية هجرة أولى لكل مستخدم لم تُبنَ بعد.
 * الأثر: تعديل قاعدة أو تصنيف لا يُحفظ بين الجلسات. عمل الشريحة التالية.
 */

const systemClock: Clock = { nowIso: () => new Date().toISOString() }

export interface Container {
  auth: AuthPort
  /** يبني بقية القطع بعد معرفة هوية المستخدم. */
  forUser(uid: string): UserContainer
}

export interface UserContainer {
  loadTransactionsScreen: ReturnType<typeof makeLoadTransactionsScreen>
  importStatement: ReturnType<typeof makeImportStatement>
  categorizeTransactions: ReturnType<typeof makeCategorizeTransactions>
  revertImportBatch: ReturnType<typeof makeRevertImportBatch>
}

export function createContainer(): Container {
  return {
    auth: new FirebaseAuthAdapter(),

    forUser(uid: string): UserContainer {
      const txns = new FirestoreTransactionRepository(db, uid)
      const sources = new FirestoreSourceRecordRepository(db, uid)
      const batches = new FirestoreImportBatchRepository(db, uid)
      const uow = new FirestoreUnitOfWork()

      const categoryList = buildCategories(tokens.categories)
      const refs = loadReferences(rawRules, rawMerchants, categoryList)

      const categories = new MemoryCategoryRepository(categoryList)
      const merchants = new MemoryMerchantRepository(refs.merchants)
      const rules = new MemoryRuleRepository(refs.rules)
      const allocations = new MemoryAllocationRepository()

      return {
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
      }
    },
  }
}
