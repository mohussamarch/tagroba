import { describe, expect, it } from 'vitest'
import {
  EditTransactionError,
  makeEditTransaction,
} from '../../src/application/useCases/editTransaction'
import { makeManageRules, RulesError } from '../../src/application/useCases/manageRules'
import {
  MemoryCategoryRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import {
  MemoryTagRepository,
  MemoryTransactionTagRepository,
} from '../../src/infrastructure/memory/memoryTagRepositories'
import { sumByTag } from '../../src/domain/ledger'
import type { Category, Merchant, Transaction } from '../../src/domain/entities/types'

/**
 * تعديل تفاصيل العملية والوسوم وإدارة القواعد.
 *
 * القواعد المختبَرة:
 * - `spec/04`: التصنيف والملاحظة ووسم الكاش بيتعدّلوا على عملية موجودة
 * - `spec/02`: «انضمام جدول الوسوم **لا يجوز أن يضاعف SUM**»
 * - `spec/05`: «المؤكد لا يُكتب فوقه» — التعديل اليدوي بيتحسب تأكيدًا
 */

const FOOD: Category = {
  id: 'c-food',
  parentId: null,
  name: 'مطاعم',
  iconKey: 'food',
  lightColor: '#f00',
  darkColor: '#f00',
  active: true,
  order: 1,
}
const TRANSPORT: Category = { ...FOOD, id: 'c-transport', name: 'مواصلات', order: 2 }

function txn(over: Partial<Transaction> & Pick<Transaction, 'id'>): Transaction {
  return {
    occurredAt: '2026-09-01',
    datePrecision: 'day',
    sourceOrder: 1,
    economicKind: 'purchase',
    economicKindConfirmed: true,
    observedDirection: 'out',
    amountMinor: 72000,
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'needs_review',
    isCashTagged: false,
    createdAt: '',
    updatedAt: '',
    ...over,
  }
}

async function build(txns: Transaction[] = [txn({ id: 't1' })]) {
  const deps = {
    txns: new MemoryTransactionRepository(),
    categories: new MemoryCategoryRepository([FOOD, TRANSPORT]),
    tags: new MemoryTagRepository(),
    transactionTags: new MemoryTransactionTagRepository(),
    uow: new PassthroughUnitOfWork(),
    ids: new SequentialIdGenerator(),
    clock: new FixedClock('2026-09-07T10:00:00.000Z'),
  }
  await deps.txns.saveMany(txns)
  return { deps, useCase: makeEditTransaction(deps) }
}

describe('تعديل التصنيف', () => {
  it('الاختيار اليدوي بيتحسب تأكيدًا فالقواعد ما تكتبش فوقه', async () => {
    const { useCase } = await build()
    await useCase.setCategory('t1', FOOD.id)

    const { transaction } = await useCase.load('t1')
    expect(transaction.categoryId).toBe(FOOD.id)
    expect(transaction.categoryConfirmed).toBe(true)
    expect(transaction.reviewState).toBe('confirmed')
  })

  it('شيل التصنيف بيرجّعها لمحتاجة مراجعة', async () => {
    const { useCase } = await build()
    await useCase.setCategory('t1', FOOD.id)
    await useCase.setCategory('t1', null)

    const { transaction } = await useCase.load('t1')
    expect(transaction.categoryId).toBeUndefined()
    expect(transaction.categoryConfirmed).toBe(false)
    expect(transaction.reviewState).toBe('needs_review')
  })

  it('تصنيف مش موجود بيترفض', async () => {
    const { useCase } = await build()
    await expect(useCase.setCategory('t1', 'مش-موجود')).rejects.toThrow(EditTransactionError)
  })

  it('عملية مش موجودة بترفض بتفسير', async () => {
    const { useCase } = await build()
    await expect(useCase.load('مش-موجودة')).rejects.toThrow(/مش موجودة/)
  })
})

describe('الملاحظة ووسم الكاش', () => {
  it('الملاحظة بتتحفظ وبتتشال لما تبقى فاضية', async () => {
    const { useCase } = await build()
    await useCase.setNote('t1', '  قهوة مع أحمد  ')
    expect((await useCase.load('t1')).transaction.note).toBe('قهوة مع أحمد')

    await useCase.setNote('t1', '   ')
    expect((await useCase.load('t1')).transaction.note).toBeUndefined()
  })

  it('الملاحظة الأطول من الحد بترفض', async () => {
    const { useCase } = await build()
    await expect(useCase.setNote('t1', 'ا'.repeat(1001))).rejects.toThrow(/1000/)
  })

  it('وسم الكاش بيتعلّم ومش بيغيّر المبلغ ولا المحفظة', async () => {
    const { useCase } = await build([txn({ id: 't1', walletId: 'w-bank', amountMinor: 72000 })])
    await useCase.setCashTag('t1', true)

    const { transaction } = await useCase.load('t1')
    expect(transaction.isCashTagged).toBe(true)
    // الوسم علامة مش تحويل — الخصم من المحفظة زي ما هو
    expect(transaction.walletId).toBe('w-bank')
    expect(transaction.amountMinor).toBe(72000)
  })

  it('الاستبعاد من الميزانية بيتعلّم', async () => {
    const { useCase } = await build()
    await useCase.setExcludedFromBudget('t1', true)
    expect((await useCase.load('t1')).transaction.excludedFromBudget).toBe(true)
  })
})

describe('الوسوم', () => {
  it('الوسم بيتعمل مرة واحدة ويتعاد استخدامه', async () => {
    const { deps, useCase } = await build([txn({ id: 't1' }), txn({ id: 't2' })])
    const a = await useCase.addTag('t1', 'سفر')
    const b = await useCase.addTag('t2', 'سفر')

    expect(a.id).toBe(b.id)
    expect(await deps.tags.listAll()).toHaveLength(1)
  })

  it('الاختلاف في المسافات مش وسم جديد', async () => {
    const { deps, useCase } = await build()
    await useCase.addTag('t1', 'سفر')
    await useCase.addTag('t1', '  سفر  ')
    expect(await deps.tags.listAll()).toHaveLength(1)
  })

  it('نفس الوسم على نفس العملية ما بيتضافش مرتين', async () => {
    const { deps, useCase } = await build()
    await useCase.addTag('t1', 'سفر')
    await useCase.addTag('t1', 'سفر')
    expect(await deps.transactionTags.listByTransactionIds(['t1'])).toHaveLength(1)
  })

  it('spec/02 — تلات وسوم على عملية واحدة ما بيضاعفوش المبلغ', async () => {
    const one = txn({ id: 't1', amountMinor: 72000 })
    const { useCase } = await build([one])
    for (const name of ['سفر', 'شغل', 'مهم']) await useCase.addTag('t1', name)

    const { tags } = await useCase.load('t1')
    expect(tags).toHaveLength(3)

    // الجمع بيجمع العمليات المميزة — 720.00 لا 2160.00
    expect(sumByTag([one], ['t1', 't1', 't1'])).toBe(72000)
  })

  it('شيل وسم من عملية ما بيشيلوش من غيرها', async () => {
    const { useCase } = await build([txn({ id: 't1' }), txn({ id: 't2' })])
    const tag = await useCase.addTag('t1', 'سفر')
    await useCase.addTag('t2', 'سفر')

    await useCase.removeTag('t1', tag.id)
    expect((await useCase.load('t1')).tags).toHaveLength(0)
    expect((await useCase.load('t2')).tags).toHaveLength(1)
    // الوسم نفسه لسه موجود
    expect(await useCase.listTags()).toHaveLength(1)
  })

  it('اسم وسم فاضي أو أطول من الحد بيترفض', async () => {
    const { useCase } = await build()
    await expect(useCase.addTag('t1', '  ')).rejects.toThrow(EditTransactionError)
    await expect(useCase.addTag('t1', 'ا'.repeat(41))).rejects.toThrow(/40/)
  })

  it('وسوم مجموعة عمليات باستعلام واحد', async () => {
    const { useCase } = await build([txn({ id: 't1' }), txn({ id: 't2' }), txn({ id: 't3' })])
    await useCase.addTag('t1', 'سفر')
    await useCase.addTag('t2', 'شغل')

    const map = await useCase.tagsFor(['t1', 't2', 't3'])
    expect(map.get('t1')?.[0].displayName).toBe('سفر')
    expect(map.get('t2')?.[0].displayName).toBe('شغل')
    expect(map.get('t3')).toBeUndefined()
  })
})

describe('إدارة القواعد والتجار', () => {
  const MERCHANT: Merchant = { id: 'm1', displayName: 'البيك', normalizedName: 'البيك' }

  function buildRules(merchants: Merchant[] = [MERCHANT]) {
    const deps: {
      rules: MemoryRuleRepository
      merchants: MemoryMerchantRepository
      categories: MemoryCategoryRepository
      ids: SequentialIdGenerator
    } = {
      rules: new MemoryRuleRepository(),
      merchants: new MemoryMerchantRepository(merchants),
      categories: new MemoryCategoryRepository([FOOD, TRANSPORT]),
      ids: new SequentialIdGenerator(),
    }
    return { deps, useCase: makeManageRules(deps) }
  }

  it('القاعدة الجديدة بتيجي في الآخر فما تسبقش الموجود', async () => {
    const { useCase } = buildRules()
    const first = await useCase.addRule({
      matchText: 'كافيه',
      matchMode: 'contains',
      categoryId: FOOD.id,
    })
    const second = await useCase.addRule({
      matchText: 'تاكسي',
      matchMode: 'contains',
      categoryId: TRANSPORT.id,
    })
    expect(second.priority).toBeGreaterThan(first.priority)

    const rows = await useCase.listRules()
    expect(rows.map((r) => r.rule.matchText)).toEqual(['كافيه', 'تاكسي'])
    expect(rows[0].categoryName).toBe('مطاعم')
  })

  it('قاعدة مكررة بنفس النص والطريقة بترفض', async () => {
    const { useCase } = buildRules()
    await useCase.addRule({ matchText: 'كافيه', matchMode: 'contains', categoryId: FOOD.id })
    await expect(
      useCase.addRule({ matchText: ' كافيه ', matchMode: 'contains', categoryId: FOOD.id }),
    ).rejects.toThrow(RulesError)

    // طريقة مطابقة مختلفة ⇒ قاعدة مختلفة، مسموحة
    await expect(
      useCase.addRule({ matchText: 'كافيه', matchMode: 'exact', categoryId: FOOD.id }),
    ).resolves.toBeTruthy()
  })

  it('القفل مش حذف — القاعدة بتفضل ظاهرة', async () => {
    const { useCase } = buildRules()
    const rule = await useCase.addRule({
      matchText: 'كافيه',
      matchMode: 'contains',
      categoryId: FOOD.id,
    })
    await useCase.setRuleEnabled(rule.id, false)

    const rows = await useCase.listRules()
    expect(rows).toHaveLength(1)
    expect(rows[0].rule.enabled).toBe(false)
  })

  it('تصنيف اتشال: القاعدة بتقول كده مش بتختفي', async () => {
    const { deps, useCase } = buildRules()
    await useCase.addRule({ matchText: 'كافيه', matchMode: 'contains', categoryId: FOOD.id })
    // التصنيف اختفى من المرجع — نبني مستودعًا بدونه
    deps.categories = new MemoryCategoryRepository([TRANSPORT])

    const rows = await useCase.listRules()
    expect(rows[0].categoryMissing).toBe(true)
    expect(rows[0].categoryName).toBe('تصنيف محذوف')
  })

  it('تصنيف التاجر الموثّق بيتثبّت وبيتشال', async () => {
    const { useCase } = buildRules()
    await useCase.setMerchantCategory('m1', FOOD.id)
    expect((await useCase.listMerchants())[0].verifiedCategoryName).toBe('مطاعم')

    await useCase.setMerchantCategory('m1', null)
    expect((await useCase.listMerchants())[0].verifiedCategoryName).toBeNull()
  })

  it('إعادة تسمية التاجر ما بتغيّرش مفتاح المطابقة', async () => {
    const { useCase } = buildRules()
    await useCase.renameMerchant('m1', 'البيك للمأكولات')

    const row = (await useCase.listMerchants())[0]
    expect(row.merchant.displayName).toBe('البيك للمأكولات')
    // المفتاح ثابت وإلا التاجر بينفصل عن عملياته القديمة
    expect(row.merchant.normalizedName).toBe('البيك')
  })

  it('نص قاعدة فاضي أو تصنيف مش موجود بيترفضوا', async () => {
    const { useCase } = buildRules()
    await expect(
      useCase.addRule({ matchText: '  ', matchMode: 'contains', categoryId: FOOD.id }),
    ).rejects.toThrow(RulesError)
    await expect(
      useCase.addRule({ matchText: 'كافيه', matchMode: 'contains', categoryId: 'مش-موجود' }),
    ).rejects.toThrow(RulesError)
  })
})
