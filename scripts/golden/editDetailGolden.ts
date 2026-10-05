import { makeEditTransaction } from '../../src/application/useCases/editTransaction'
import { makeLoadCashSummary } from '../../src/application/useCases/loadCashSummary'
import { makeReconcileBalance } from '../../src/application/useCases/reconcileBalance'
import { MemoryTransactionRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryAllocationRepository, MemoryCategoryRepository, MemorySettlementRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryTagRepository, MemoryTransactionTagRepository } from '../../src/infrastructure/memory/memoryTagRepositories'
import { MemoryWalletRepository } from '../../src/infrastructure/memory/memoryWalletRepository'
import { MemoryUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import { buildPeriod } from '../../src/domain/period'
import type { Category, PersonAllocation, Settlement, Tag, Transaction, TransactionTag, Wallet } from '../../src/domain/entities/types'
import { recordAsync } from './goldenKit'

/**
 * تعديل العملية + ملخص الكاش + مطابقة الرصيد — حالات مكتوبة بالإيد. ⚠️ بيانات وهمية بالكامل.
 * مساهمة قاعدة التجار المشتركة (`onCategoryConfirmed`) مش هنا — نداء «ولّع وانسى» بيتوصّل
 * مع طبقة التخزين، وغيابه هنا = التطبيق الحالي من غير النداء بالظبط.
 */

const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
  { id: 'shopping', parentId: null, name: 'تسوق', iconKey: 'shopping-basket', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 2, groupKey: 'personal' },
]

function txn(id: string, over: Partial<Transaction> = {}): Transaction {
  return {
    id, occurredAt: '2026-09-05', datePrecision: 'day', sourceOrder: 1,
    economicKind: 'purchase', economicKindConfirmed: true,
    observedDirection: 'out', amountMinor: 20000, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
    isCashTagged: false, createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
    ...over,
  }
}

export async function editDetailGolden() {
  const editCases = []
  const cashCases = []
  const reconcileCases = []

  /* ─── تعديل العملية ─── */
  interface EditSeed {
    transactions: Transaction[]
    tags: Tag[]
    links: TransactionTag[]
    allocations: PersonAllocation[]
    settlements: Settlement[]
  }
  type EditAction =
    | { kind: 'load' | 'listTags'; transactionId?: string }
    | { kind: 'setCategory'; transactionId: string; categoryId: string | null }
    | { kind: 'setAmount'; transactionId: string; amountMinor: number }
    | { kind: 'setNote'; transactionId: string; note: string }
    | { kind: 'setCashTag'; transactionId: string; value: boolean }
    | { kind: 'setExcluded'; transactionId: string; value: boolean }
    | { kind: 'addTag'; transactionId: string; displayName: string }
    | { kind: 'removeTag'; transactionId: string; tagId: string }
    | { kind: 'tagsFor'; transactionIds: string[] }

  async function runEdit(seed: EditSeed, action: EditAction) {
    editCases.push(await recordAsync({ seed, categories: cats, action }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed.transactions)
      const tags = new MemoryTagRepository(seed.tags)
      const links = new MemoryTransactionTagRepository(seed.links)
      const allocations = new MemoryAllocationRepository()
      await allocations.saveMany(seed.allocations)
      const settlements = new MemorySettlementRepository()
      await settlements.saveMany(seed.settlements)
      const edit = makeEditTransaction({
        txns,
        categories: new MemoryCategoryRepository(cats),
        tags, transactionTags: links,
        uow: new MemoryUnitOfWork([txns]),
        ids: new SequentialIdGenerator(),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
        allocations, settlements,
      })
      const stored = async () => ({
        storedTransactions: txns.all(),
        storedTags: await tags.listAll(),
        storedLinks: (await links.listByTransactionIds(seed.transactions.map((t) => t.id))),
      })
      switch (action.kind) {
        case 'load': {
          const detail = await edit.load(action.transactionId!)
          return { transactionId: detail.transaction.id, tags: detail.tags }
        }
        case 'setCategory': await edit.setCategory(action.transactionId, action.categoryId); return await stored()
        case 'setAmount': await edit.setAmount(action.transactionId, action.amountMinor); return await stored()
        case 'setNote': await edit.setNote(action.transactionId, action.note); return await stored()
        case 'setCashTag': await edit.setCashTag(action.transactionId, action.value); return await stored()
        case 'setExcluded': await edit.setExcludedFromBudget(action.transactionId, action.value); return await stored()
        case 'addTag': return { created: await edit.addTag(action.transactionId, action.displayName), ...(await stored()) }
        case 'removeTag': await edit.removeTag(action.transactionId, action.tagId); return await stored()
        case 'listTags': return { tags: await edit.listTags() }
        case 'tagsFor': {
          const map = await edit.tagsFor(action.transactionIds)
          return { byTransaction: Object.fromEntries([...map].map(([k, v]) => [k, v.map((t) => t.id)])) }
        }
      }
    }))
  }

  const editSeed: EditSeed = {
    transactions: [
      txn('t-1', { categoryId: 'food', rawMerchantName: 'قهوة الحي' }),
      txn('t-2', { originalAmountMinor: 25000, amountMinor: 18000 }),
      txn('t-3'),
    ],
    tags: [
      { id: 'tag-b', normalizedName: 'شغل', displayName: 'شغل' },
      { id: 'tag-a', normalizedName: 'رحله', displayName: 'رحلة' },
    ],
    links: [
      { id: 'l-1', transactionId: 't-1', tagId: 'tag-a' },
      { id: 'l-2', transactionId: 't-1', tagId: 'tag-ghost' },
      { id: 'l-3', transactionId: 't-3', tagId: 'tag-b' },
    ],
    allocations: [{ id: 'al-1', transactionId: 't-3', personId: 'p-1', allocationKind: 'receivable', amountMinor: 15000, currency: 'SAR' }],
    settlements: [{ id: 'st-1', transactionId: 't-1', obligationId: 'ob-1', amountMinor: 5000 }],
  }

  await runEdit(editSeed, { kind: 'load', transactionId: 't-1' })
  await runEdit(editSeed, { kind: 'load', transactionId: 't-ghost' })
  await runEdit(editSeed, { kind: 'setCategory', transactionId: 't-3', categoryId: 'shopping' })
  await runEdit(editSeed, { kind: 'setCategory', transactionId: 't-1', categoryId: null })
  await runEdit(editSeed, { kind: 'setCategory', transactionId: 't-1', categoryId: 'c-ghost' })
  // أول تعديل مبلغ بيحفظ الأصلي مرة واحدة؛ التاني بيسيبه زي ما هو
  await runEdit(editSeed, { kind: 'setAmount', transactionId: 't-1', amountMinor: 17500 })
  await runEdit(editSeed, { kind: 'setAmount', transactionId: 't-2', amountMinor: 30000 })
  // أقل من المتوزع على أشخاص / من المتسوّى بيه
  await runEdit(editSeed, { kind: 'setAmount', transactionId: 't-3', amountMinor: 10000 })
  await runEdit(editSeed, { kind: 'setAmount', transactionId: 't-1', amountMinor: 4000 })
  await runEdit(editSeed, { kind: 'setNote', transactionId: 't-1', note: '  ملاحظة مهمة  ' })
  await runEdit(editSeed, { kind: 'setNote', transactionId: 't-1', note: '   ' })
  await runEdit(editSeed, { kind: 'setNote', transactionId: 't-1', note: 'ن'.repeat(1001) })
  await runEdit(editSeed, { kind: 'setCashTag', transactionId: 't-1', value: true })
  await runEdit(editSeed, { kind: 'setExcluded', transactionId: 't-1', value: true })
  // وسم جديد بيتعمل · موجود بالاسم المطبّع بيتعاد استعماله · مكرر على نفس العملية بيتساب
  await runEdit(editSeed, { kind: 'addTag', transactionId: 't-2', displayName: ' سفر ' })
  await runEdit(editSeed, { kind: 'addTag', transactionId: 't-2', displayName: 'رحله' })
  await runEdit(editSeed, { kind: 'addTag', transactionId: 't-1', displayName: 'رحلة' })
  await runEdit(editSeed, { kind: 'addTag', transactionId: 't-1', displayName: '  ' })
  await runEdit(editSeed, { kind: 'addTag', transactionId: 't-1', displayName: 'و'.repeat(41) })
  await runEdit(editSeed, { kind: 'removeTag', transactionId: 't-1', tagId: 'tag-a' })
  await runEdit(editSeed, { kind: 'removeTag', transactionId: 't-1', tagId: 'tag-b' })
  await runEdit(editSeed, { kind: 'listTags' })
  await runEdit(editSeed, { kind: 'tagsFor', transactionIds: ['t-1', 't-3', 't-ghost'] })

  /* ─── ملخص الكاش ─── */
  const wallets: Wallet[] = [
    { id: 'w-bank', name: 'البنك', currency: 'SAR', kind: 'bank', openingBalanceMinor: 100000, openingAt: '2026-01-01' },
    { id: 'w-cash', name: 'الكاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 50000, openingAt: '2026-08-15' },
  ]

  async function runCash(seed: { wallets: Wallet[]; transactions: Transaction[]; allocations: PersonAllocation[] }, options: { year: number; month: number; payday: number; today: string }) {
    cashCases.push(await recordAsync({ seed, categories: cats, options }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed.transactions)
      const allocations = new MemoryAllocationRepository()
      await allocations.saveMany(seed.allocations)
      const load = makeLoadCashSummary({
        wallets: new MemoryWalletRepository(seed.wallets),
        txns, allocations,
        categories: new MemoryCategoryRepository(cats),
      })
      const summary = await load({ period: buildPeriod(options.year, options.month, options.payday), today: options.today })
      if (!summary) return { summary: null }
      return {
        summary: {
          walletId: summary.wallet.id,
          balanceMinor: summary.balanceMinor,
          inSinceOpeningMinor: summary.inSinceOpeningMinor,
          outSinceOpeningMinor: summary.outSinceOpeningMinor,
          spentInPeriodMinor: summary.spentInPeriodMinor,
          periodTransactionIds: summary.periodTransactions.map((t) => t.id),
        },
      }
    }))
  }

  const cashTxns: Transaction[] = [
    txn('cash-1', { walletId: 'w-cash', occurredAt: '2026-08-20', amountMinor: 3000 }),
    txn('cash-2', { walletId: 'w-cash', occurredAt: '2026-09-10', amountMinor: 4500 }),
    txn('cash-in', { walletId: 'w-cash', occurredAt: '2026-09-12', observedDirection: 'in', economicKind: 'gift_received', amountMinor: 10000 }),
    // تحويل داخلي من البنك للكاش — الطرف الداخل بيزود الكاش
    txn('to-cash', { walletId: 'w-bank', occurredAt: '2026-09-15', economicKind: 'internal_transfer', transferToWalletId: 'w-cash', amountMinor: 20000 }),
    // عملية بنك موسومة كاش — بتظهر في تقارير الكاش
    txn('tagged', { walletId: 'w-bank', occurredAt: '2026-09-18', isCashTagged: true, amountMinor: 2500 }),
    // نوعها لسه ما اتحددش — بتتحسب بالنوع التقديري (OVERRIDES §18)
    txn('cash-est', { walletId: 'w-cash', occurredAt: '2026-09-20', economicKind: 'unclassified', economicKindConfirmed: false, amountMinor: 1500, categoryId: 'food' }),
  ]
  // يوم راتب 28 ⇒ فترة سبتمبر المالية تبدأ 28/9، فالعمليات دي كلها **قبل** الفترة (الرصيد بيحسبها والفترة لأ)
  await runCash({ wallets, transactions: cashTxns, allocations: [] }, { year: 2026, month: 9, payday: 28, today: '2026-09-22' })
  // يوم راتب 1 ⇒ نفس العمليات جوه الفترة: مصروف الفترة والقايمة بيتملوا
  await runCash({ wallets, transactions: cashTxns, allocations: [{ id: 'al-c', transactionId: 'cash-2', personId: 'p-1', allocationKind: 'receivable', amountMinor: 1000, currency: 'SAR' }] }, { year: 2026, month: 9, payday: 1, today: '2026-09-22' })
  await runCash({ wallets: [wallets[0]!], transactions: [], allocations: [] }, { year: 2026, month: 9, payday: 28, today: '2026-09-22' })

  /* ─── مطابقة الرصيد ─── */
  async function runReconcile(seed: { wallets: Wallet[]; transactions: Transaction[] }, options: { walletId: string; until: string; payday: number }) {
    reconcileCases.push(await recordAsync({ seed, options }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed.transactions)
      const run = makeReconcileBalance({ txns, wallets: new MemoryWalletRepository(seed.wallets) })
      const outcome = await run(options)
      return {
        walletId: outcome.wallet.id,
        result: outcome.result,
        withoutStatedBalance: outcome.withoutStatedBalance,
        unassignedCount: outcome.unassignedCount,
        periodsRead: outcome.periodsRead,
      }
    }))
  }

  const chainTxns: Transaction[] = [
    // سلسلة سليمة: 1000.00 افتتاحي − 96.47 = 903.53 ثم + 50.00 = 953.53
    txn('r-1', { walletId: 'w-bank', occurredAt: '2026-01-05', amountMinor: 9647, statedBalanceMinor: 90353, sourceOrder: 1 }),
    txn('r-2', { walletId: 'w-bank', occurredAt: '2026-02-10', observedDirection: 'in', economicKind: 'salary', amountMinor: 5000, statedBalanceMinor: 95353, sourceOrder: 1 }),
    // من غير رصيد معلن — ما بيتقارنش
    txn('r-3', { walletId: 'w-bank', occurredAt: '2026-03-01', amountMinor: 1000, sourceOrder: 1 }),
    // مش منسوبة لمحفظة — بتتعد ومش بتتنسب بالتخمين
    txn('r-4', { occurredAt: '2026-03-02', amountMinor: 500, sourceOrder: 2 }),
    // تحويل داخلي جاي للكاش — بيظهر في مطابقة الكاش كوارد
    txn('r-5', { walletId: 'w-bank', occurredAt: '2026-03-05', economicKind: 'internal_transfer', transferToWalletId: 'w-cash', amountMinor: 2000, sourceOrder: 3 }),
  ]
  const reconcileWallets: Wallet[] = [
    { id: 'w-bank', name: 'البنك', currency: 'SAR', kind: 'bank', openingBalanceMinor: 100000, openingAt: '2026-01-01' },
    { id: 'w-cash', name: 'الكاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 0, openingAt: '2026-01-01' },
  ]
  await runReconcile({ wallets: reconcileWallets, transactions: chainTxns }, { walletId: 'w-bank', until: '2026-09-22', payday: 28 })
  await runReconcile({ wallets: reconcileWallets, transactions: chainTxns }, { walletId: 'w-cash', until: '2026-09-22', payday: 28 })
  await runReconcile({ wallets: reconcileWallets, transactions: chainTxns }, { walletId: 'w-ghost', until: '2026-09-22', payday: 28 })
  // رصيد معلن غلط ⇒ فرق بيتسجل بمكانه، ويومه فيه أكتر من حركة
  const badTxns: Transaction[] = [
    txn('b-1', { walletId: 'w-bank', occurredAt: '2026-01-05', amountMinor: 9647, statedBalanceMinor: 90353, sourceOrder: 1 }),
    txn('b-2', { walletId: 'w-bank', occurredAt: '2026-01-05', amountMinor: 100, statedBalanceMinor: 88888, sourceOrder: 2 }),
  ]
  await runReconcile({ wallets: reconcileWallets, transactions: badTxns }, { walletId: 'w-bank', until: '2026-09-22', payday: 28 })

  return { editTransaction: editCases, loadCashSummary: cashCases, reconcileBalance: reconcileCases }
}
