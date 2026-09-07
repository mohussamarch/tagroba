import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryImportBatchRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemorySourceRecordRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../infrastructure/memory/memoryRepositories'
import { buildCategories, loadReferences } from '../infrastructure/import/referenceLoader'
import { makeImportStatement } from '../application/useCases/importStatement'
import { makeCategorizeTransactions } from '../application/useCases/categorizeTransactions'
import { makeRevertImportBatch } from '../application/useCases/revertImportBatch'
import { makeLoadTransactionsScreen } from '../application/useCases/loadTransactionsScreen'
import { makeResumeStagedBatch } from '../application/useCases/resumeStagedBatch'
import { makeSeedUserReferences } from '../application/useCases/seedUserReferences'
import { makeLoadHomeScreen } from '../application/useCases/loadHomeScreen'
import { makeSetEconomicKind } from '../application/useCases/setEconomicKind'
import type { AuthPort, AuthUser } from '../application/ports/AuthPort'
import type { Container, UserContainer } from './container'
import tokens from '../../design-source/masroofi-claude-code/design/tokens.json'
import rawRules from '../../design-source/masroofi-claude-code/fixtures/rule-reference.json'
import rawMerchants from '../../design-source/masroofi-claude-code/fixtures/merchant-reference.json'

/**
 * وضع المعاينة — **بيانات في الذاكرة فقط، بلا فايربيز وبلا حساب.**
 *
 * spec/01: «يمكن توفير وضع عرض منفصل قابل للمسح،
 *  و**لا تُخلط بياناته بالاستيراد الحقيقي**.»
 * العزل هنا كامل: لا شيء يُكتب خارج الذاكرة، ويختفي بإعادة التحميل.
 *
 * ⚠️ متاح في التطوير فقط — يُفعَّل بـ ?demo=1 ويُستبعد من بناء الإنتاج
 * بشرط import.meta.env.DEV في main.tsx.
 */

const DEMO_USER: AuthUser = {
  uid: 'demo',
  email: 'demo@masroufy.local',
  displayName: 'وضع المعاينة',
}

class DemoAuth implements AuthPort {
  observe(callback: (user: AuthUser | null) => void): () => void {
    queueMicrotask(() => callback(DEMO_USER))
    return () => {}
  }
  async signInWithGoogle(): Promise<AuthUser> {
    return DEMO_USER
  }
  async signInWithEmail(): Promise<AuthUser> {
    return DEMO_USER
  }
  async registerWithEmail(): Promise<AuthUser> {
    return DEMO_USER
  }
  async sendPasswordReset(): Promise<void> {}
  async signOut(): Promise<void> {
    location.search = ''
  }
}

export function createDemoContainer(): Container {
  const txns = new MemoryTransactionRepository()
  const sources = new MemorySourceRecordRepository()
  const batches = new MemoryImportBatchRepository()
  const allocations = new MemoryAllocationRepository()

  const categoryList = buildCategories(tokens.categories)
  const refs = loadReferences(rawRules, rawMerchants, categoryList)
  const categories = new MemoryCategoryRepository(categoryList)
  const merchants = new MemoryMerchantRepository(refs.merchants)
  const rules = new MemoryRuleRepository(refs.rules)
  const uow = new PassthroughUnitOfWork()
  const ids = new SequentialIdGenerator()
  const clock = new FixedClock(new Date().toISOString())

  const seedSource = { categories: categoryList, rules: refs.rules, merchants: refs.merchants }

  const userContainer: UserContainer = {
    // في المعاينة المستودعات مزروعة من البداية، فالزرع بيرجع «موجودة قبل كده»
    seedUserReferences: () => makeSeedUserReferences({ categories, rules, merchants, uow })(seedSource),
    loadHomeScreen: makeLoadHomeScreen({ txns, categories, allocations }),
    setEconomicKind: makeSetEconomicKind({ txns, categories, uow, clock }),
    loadTransactionsScreen: makeLoadTransactionsScreen({ txns, categories, allocations }),
    importStatement: makeImportStatement({
      txns, sources, batches, merchants, categories, rules, uow, ids, clock,
    }),
    categorizeTransactions: makeCategorizeTransactions({
      txns, merchants, categories, rules, uow, clock,
    }),
    revertImportBatch: makeRevertImportBatch({
      txns,
      sources,
      batches,
      settlements: {
        listByObligations: async () => [], listByTransactionIds: async () => [],
        saveMany: async () => {}, deleteMany: async () => {},
      },
      allocations,
      obligations: {
        listByPerson: async () => [], listByTransactionIds: async () => [],
        saveMany: async () => {}, deleteMany: async () => {},
      },
      uow,
    }),
    resumeStagedBatch: makeResumeStagedBatch({ txns, sources, batches }),
  }

  return {
    auth: new DemoAuth(),
    forUser: () => userContainer,
  }
}
