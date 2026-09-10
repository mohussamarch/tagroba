import { makeLoadHomeHistory } from '../application/useCases/loadHomeHistory'
import { makeReadBankSms } from '../application/useCases/readBankSms'
import { makeFullBackup } from '../application/useCases/fullBackup'
import { makeRepairStoredIds } from '../application/useCases/repairStoredIds'
import { memoryIdRepair } from '../infrastructure/memory/idRepair'
import { sanitizeAccountNumbers } from '../infrastructure/firestore/firestoreRepositories'
import { snapshotFullBackup } from '../infrastructure/memory/snapshotFullBackup'
import { backupDigest } from '../infrastructure/backupDigest'
import type { BackupRow } from '../domain/fullBackup'
import type { Budget, CategoryBudget } from '../domain/entities/types'
import type { RecurringItem } from '../domain/entities/recurring'
import { makeManageSmsInbox } from '../application/useCases/manageSmsInbox'
import { memorySmsInbox } from '../infrastructure/memory/smsInbox'
import { parseBankSms } from '../infrastructure/import/bankSmsParser'
import { saveTextFile } from '../infrastructure/saveTextFile'
import { makeManageCategories } from '../application/useCases/manageCategories'
import { makeReviewHistory } from '../application/useCases/reviewHistory'
import { makeManageRecurring } from '../application/useCases/manageRecurring'
import { MemoryRecurringRepository } from '../infrastructure/memory/memoryRecurringRepository'
import {
  MemoryAllocationRepository,
  MemoryBudgetRepository,
  MemoryWalletRepository,
  MemoryPersonRepository,
  MemoryObligationRepository,
  MemorySettlementRepository,
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
import { makeReadPdfStatement } from '../application/useCases/readPdfStatement'
import { loadPriceFeed } from '../infrastructure/prices/loadPriceFeed'
import {
  MemoryAssetLotRepository,
  MemoryAssetPriceRepository,
  MemoryAssetRepository,
  MemoryAssetSaleRepository,
} from '../infrastructure/memory/memoryAssetRepositories'
import {
  MemoryTagRepository,
  MemoryTransactionTagRepository,
} from '../infrastructure/memory/memoryTagRepositories'
import { MemoryNotificationReceiptRepository } from '../infrastructure/memory/memoryNotificationRepository'
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
  const budgets = new MemoryBudgetRepository()
  const wallets = new MemoryWalletRepository()
  const people = new MemoryPersonRepository()
  const obligations = new MemoryObligationRepository()
  const settlements = new MemorySettlementRepository()

  const categoryList = buildCategories(tokens.categories)
  const refs = loadReferences(rawRules, rawMerchants, categoryList)
  const categories = new MemoryCategoryRepository(categoryList)
  const merchants = new MemoryMerchantRepository(refs.merchants)
  const rules = new MemoryRuleRepository(refs.rules)
  const tags = new MemoryTagRepository()
  const transactionTags = new MemoryTransactionTagRepository()
  const assets = new MemoryAssetRepository()
  const assetLots = new MemoryAssetLotRepository()
  const assetSales = new MemoryAssetSaleRepository()
  const assetPrices = new MemoryAssetPriceRepository()
  const notificationReceipts = new MemoryNotificationReceiptRepository()
  const recurringItems = new MemoryRecurringRepository()
  const uow = new PassthroughUnitOfWork()
  const ids = new SequentialIdGenerator()
  const clock = new FixedClock(new Date().toISOString())

  const seedSource = { categories: categoryList, rules: refs.rules, merchants: refs.merchants }

  const userContainer: UserContainer = {
    homeSnapshot: {read:async()=>null,save:async()=>{},clear:async()=>{}},
    loadHomeHistory: makeLoadHomeHistory({txns,allocations}),
    readBankSms: makeReadBankSms({ available: false, read: async () => ({messages:[],truncated:false}) }, parseBankSms),
    smsInbox: makeManageSmsInbox(memorySmsInbox([
      {id:'demo-sms-one',sender:'DemoBank',receivedAt:new Date().toISOString(),body:'شراء بمبلغ 25.50 SAR لدى DEMO ALBAIK في '+new Date().toISOString().slice(0,10)},
      {id:'demo-sms-two',sender:'DemoBank',receivedAt:new Date().toISOString(),body:'شراء بمبلغ 10 SAR لدى DEMO SHOP في '+new Date().toISOString().slice(0,10)},
      {id:'demo-sms-unknown',sender:'DemoBank',receivedAt:new Date().toISOString(),body:'شراء بمبلغ 20 SAR'},
    ],true), parseBankSms),
    saveTextFile,
    fullBackup: makeFullBackup(snapshotFullBackup({transactions:txns,sourceRecords:sources,importBatches:batches,wallets,categories,merchants,rules,people,obligations,allocations,settlements,tags,transactionTags,assets,assetLots,assetSales,assetPrices,notificationReceipts},{
      budgets:{read:async()=>budgets.snapshot().budgets as unknown as BackupRow[],write:async rows=>budgets.restore({...budgets.snapshot(),budgets:rows as unknown as Budget[]})},
      categoryBudgets:{read:async()=>budgets.snapshot().lines as unknown as BackupRow[],write:async rows=>budgets.restore({...budgets.snapshot(),lines:rows as unknown as CategoryBudget[]})},
      recurringItems:{read:async()=>await recurringItems.listAll() as unknown as BackupRow[],write:async rows=>{for(const row of rows)await recurringItems.save(row as unknown as RecurringItem)}},
    }),backupDigest),
    repairStoredIds: makeRepairStoredIds(memoryIdRepair(),sanitizeAccountNumbers),
    manageCategories: makeManageCategories({categories,ids}),
    reviewHistory: makeReviewHistory({txns,merchants,categories,rules,uow,clock}),
    manageRecurring: makeManageRecurring({items:recurringItems,txns,categories,ids}),
    // في المعاينة المستودعات مزروعة من البداية، فالزرع بيرجع «موجودة قبل كده»
    seedUserReferences: () => makeSeedUserReferences({ categories, rules, merchants, uow })(seedSource),
    loadHomeScreen: makeLoadHomeScreen({ txns, categories, allocations, budgets }),
    loadBudgetScreen: makeLoadBudgetScreen({ txns, categories, allocations, budgets }),
    managePeople: makeManagePeople({
      people, obligations, settlements, allocations, txns, uow, ids, clock,
    }),
    addTransaction: makeAddTransaction({ txns, wallets, ids, clock }),
    reconcileBalance: makeReconcileBalance({ txns, wallets }),
    exportBackup: makeExportBackup({
      txns, sources, batches, wallets, categories, rules, merchants, budgets,
    }),
    seedWallets: makeSeedWallets({ wallets }),
    wallets,
    setBudget: makeSetBudget({ budgets, uow, ids, clock }),
    setEconomicKind: makeSetEconomicKind({ txns, categories, uow, clock }),
    loadTransactionsScreen: makeLoadTransactionsScreen({ txns, categories, allocations, tags, transactionTags, merchants }),
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
      settlements,
      allocations,
      obligations,
      uow,
    }),
    resumeStagedBatch: makeResumeStagedBatch({ txns, sources, batches }),
    editTransaction: makeEditTransaction({ txns, categories, tags, transactionTags, uow, ids, clock }),
    manageRules: makeManageRules({ rules, merchants, categories, ids }),
    restoreBackup: makeRestoreBackup({ txns, wallets, categories, rules, merchants, budgets, uow }),
    manageAssets: makeManageAssets({
      assets, lots: assetLots, sales: assetSales, prices: assetPrices, ids, clock,
    }),
    syncAssetPrices: makeSyncAssetPrices({ assets, prices: assetPrices }),
    loadNotifications: makeLoadNotifications({ receipts: notificationReceipts, clock }),
    readPdfStatement: makeReadPdfStatement(),
    loadPriceFeed,
  }

  return {
    auth: new DemoAuth(),
    forUser: () => userContainer,
  }
}
