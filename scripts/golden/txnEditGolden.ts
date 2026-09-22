import { makeSetEconomicKind } from '../../src/application/useCases/setEconomicKind'
import { makeCategorizeTransactions } from '../../src/application/useCases/categorizeTransactions'
import { makeAddTransaction, type NewTransactionInput } from '../../src/application/useCases/addTransaction'
import { MemoryTransactionRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository, MemoryRuleRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryWalletRepository } from '../../src/infrastructure/memory/memoryWalletRepository'
import { MemoryUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import type { Category, ClassificationRule, Merchant, Transaction, Wallet } from '../../src/domain/entities/types'
import type { EconomicKind } from '../../src/domain/entities/economicKind'
import { recordAsync } from './goldenKit'

/**
 * تعديل العمليات: تحديد النوع الاقتصادي + التصنيف الجماعي + الإضافة اليدوية.
 * حالات مكتوبة بالإيد. ⚠️ بيانات وهمية بالكامل.
 * ملاحظة: حالة «المبلغ مش عدد صحيح» في الإضافة مش هنا — نظام أنواع كوتلن بيمنعها وقت الترجمة أصلًا.
 */

const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
  { id: 'shopping', parentId: null, name: 'تسوق', iconKey: 'shopping-basket', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 2, groupKey: 'personal' },
  { id: 'telecom', parentId: null, name: 'اتصالات', iconKey: 'wifi', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 3, groupKey: 'home' },
]
const merchants: Merchant[] = [
  { id: 'm-amazon', displayName: 'أمازون', normalizedName: 'امازون', aliases: ['amazon'], verifiedCategoryId: 'shopping' },
]
const rules: ClassificationRule[] = [
  { id: 'r-stc', priority: 1, matchText: 'stc', matchMode: 'contains', categoryId: 'telecom', enabled: true },
]

function txn(id: string, over: Partial<Transaction> = {}): Transaction {
  return {
    id, occurredAt: '2026-09-05', datePrecision: 'day', sourceOrder: 1,
    economicKind: 'unclassified', economicKindConfirmed: false,
    observedDirection: 'out', amountMinor: 10000, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
    isCashTagged: false, createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
    ...over,
  }
}

const wallets: Wallet[] = [
  { id: 'w-bank', name: 'البنك', currency: 'SAR', kind: 'bank', openingBalanceMinor: 0, openingAt: '2026-01-01' },
  { id: 'w-cash', name: 'الكاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 0, openingAt: '2026-01-01' },
  { id: 'w-usd', name: 'دولار', currency: 'USD', kind: 'bank', openingBalanceMinor: 0, openingAt: '2026-01-01' },
]

export async function txnEditGolden() {
  const kindCases = []
  const catCases = []
  const addCases = []

  /*
   * عمليات تحديد النوع: مؤكدة (بتتعد بس) · صادر بعمود «سحب نقدي» (قاطع) ·
   * صادر بتاجر ووصف (مرجّح غالبًا) · وارد من غير دليل (غامض).
   */
  const kindSeed: Transaction[] = [
    txn('t-confirmed', { economicKind: 'purchase', economicKindConfirmed: true }),
    txn('t-cashout', { sourceCategory: 'سحب نقدي' }),
    txn('t-merchant', { rawMerchantName: 'مطعم البيك', rawDescription: 'شراء نقاط البيع' }),
    txn('t-in', { observedDirection: 'in' }),
    txn('t-salaryish', { observedDirection: 'in', rawDescription: 'راتب شهر سبتمبر' }),
    // «تقسيط» بيطلّع اقتراح مرجّح (MEDIUM) — بيغطي فرع «محتاجة نظرة»
    txn('t-installment', { sourceCategory: 'تقسيط' }),
  ]

  async function runKind(seed: Transaction[], action: { kind: 'summarize' | 'setOne' | 'confirmBulk'; transactionId?: string; economicKind?: EconomicKind; selectedIds?: string[] }) {
    kindCases.push(await recordAsync({ seed, categories: cats, action }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed)
      const useCase = makeSetEconomicKind({
        txns,
        categories: new MemoryCategoryRepository(cats),
        uow: new MemoryUnitOfWork([txns]),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
      })
      const suggestionJson = (l: { transaction: Transaction; suggestion: { kind: EconomicKind | null; confidence: string; reason: string; alternatives: readonly EconomicKind[] } }) => ({
        transactionId: l.transaction.id,
        kind: l.suggestion.kind,
        confidence: l.suggestion.confidence,
        reason: l.suggestion.reason,
        alternatives: l.suggestion.alternatives,
      })
      if (action.kind === 'summarize') {
        const s = await useCase.summarize(seed)
        return {
          confirmable: s.confirmable.map(suggestionJson),
          needsLook: s.needsLook.map(suggestionJson),
          ambiguous: s.ambiguous.map(suggestionJson),
          alreadySet: s.alreadySet,
        }
      }
      if (action.kind === 'setOne') {
        await useCase.setOne(action.transactionId!, action.economicKind!)
        return { stored: txns.all() }
      }
      const result = await useCase.confirmBulk(seed, action.selectedIds!)
      return { result, stored: txns.all() }
    }))
  }

  await runKind(kindSeed, { kind: 'summarize' })
  await runKind(kindSeed, { kind: 'setOne', transactionId: 't-merchant', economicKind: 'purchase' })
  await runKind(kindSeed, { kind: 'setOne', transactionId: 't-ghost', economicKind: 'purchase' })
  // مرتب على حركة صادرة ⇒ تناقض مرفوض
  await runKind(kindSeed, { kind: 'setOne', transactionId: 't-merchant', economicKind: 'salary' })
  // التأكيد الجماعي: القاطع بس بيتطبق واللي مش قاطع بيتعد «متخطى»
  await runKind(kindSeed, { kind: 'confirmBulk', selectedIds: ['t-cashout', 't-merchant', 't-in'] })
  await runKind(kindSeed, { kind: 'confirmBulk', selectedIds: ['t-in'] })

  /*
   * التصنيف الجماعي: مؤكد بيتسكّت · تاجر مؤكد بيتغلب على القاعدة · قاعدة بتطابق ·
   * عمود ملف باسم تصنيف · نفس التصنيف الحالي (مفيش تغيير) · مفيش دليل ⇒ مراجعة.
   */
  const catSeed: Transaction[] = [
    txn('c-confirmed', { categoryId: 'food', categoryConfirmed: true, rawMerchantName: 'amazon' }),
    txn('c-verified', { rawMerchantName: 'أمازون' }),
    txn('c-rule', { rawDescription: 'فاتورة STC انترنت' }),
    txn('c-column', { sourceCategory: 'أكل' }),
    txn('c-same', { categoryId: 'telecom', rawDescription: 'stc' }),
    txn('c-none', { rawMerchantName: 'محل مجهول' }),
  ]

  async function runCat(seed: Transaction[], action: { kind: 'plan' | 'apply' | 'confirm'; transactionId?: string; categoryId?: string }) {
    catCases.push(await recordAsync({ seed, references: { merchants, categories: cats, rules }, action }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed)
      const useCase = makeCategorizeTransactions({
        txns,
        merchants: new MemoryMerchantRepository(merchants),
        categories: new MemoryCategoryRepository(cats),
        rules: new MemoryRuleRepository(rules),
        uow: new MemoryUnitOfWork([txns]),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
      })
      if (action.kind === 'plan') return { report: await useCase.plan(seed) }
      if (action.kind === 'apply') return { report: await useCase.apply(seed), stored: txns.all() }
      await useCase.confirm(action.transactionId!, action.categoryId!)
      return { stored: txns.all() }
    }))
  }

  await runCat(catSeed, { kind: 'plan' })
  await runCat(catSeed, { kind: 'apply' })
  await runCat(catSeed, { kind: 'confirm', transactionId: 'c-none', categoryId: 'shopping' })

  /* الإضافة اليدوية */
  async function runAdd(input: NewTransactionInput) {
    addCases.push(await recordAsync({ wallets, input }, async () => {
      const txns = new MemoryTransactionRepository()
      const add = makeAddTransaction({
        txns,
        wallets: new MemoryWalletRepository(wallets),
        ids: new SequentialIdGenerator(),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
      })
      const created = await add(input)
      return { created, stored: txns.all() }
    }))
  }

  const base: NewTransactionInput = {
    amountMinor: 2500, occurredAt: '2026-09-20', walletId: 'w-bank',
    economicKind: 'purchase', merchantName: 'قهوة الحي',
  }
  await runAdd(base)
  // محفظة كاش ⇒ وسم الكاش تلقائي، وتصنيف مختار ⇒ مؤكد
  await runAdd({ ...base, walletId: 'w-cash', categoryId: 'food', note: '  قهوة الصبح  ' })
  // تحويل داخلي بطرفين
  await runAdd({ ...base, economicKind: 'internal_transfer', transferToWalletId: 'w-cash', merchantName: '' })
  // أخطاء
  await runAdd({ ...base, amountMinor: 0 })
  await runAdd({ ...base, amountMinor: -100 })
  await runAdd({ ...base, occurredAt: '2026-13-40' })
  await runAdd({ ...base, walletId: 'w-ghost' })
  await runAdd({ ...base, economicKind: 'unclassified' })
  await runAdd({ ...base, economicKind: 'internal_transfer' })
  await runAdd({ ...base, economicKind: 'internal_transfer', transferToWalletId: 'w-bank' })
  await runAdd({ ...base, economicKind: 'internal_transfer', transferToWalletId: 'w-missing' })
  await runAdd({ ...base, economicKind: 'internal_transfer', transferToWalletId: 'w-usd' })
  await runAdd({ ...base, transferToWalletId: 'w-cash' })
  await runAdd({ ...base, merchantName: 'م'.repeat(121) })
  await runAdd({ ...base, note: 'ن'.repeat(1001) })

  return { setEconomicKind: kindCases, categorizeTransactions: catCases, addTransaction: addCases }
}
