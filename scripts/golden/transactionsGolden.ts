import { makeLoadTransactionsScreen } from '../../src/application/useCases/loadTransactionsScreen'
import { MemoryTransactionRepository, MemoryAllocationRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryTagRepository, MemoryTransactionTagRepository } from '../../src/infrastructure/memory/memoryTagRepositories'
import { buildPeriod } from '../../src/domain/period'
import { ALL_ECONOMIC_KINDS } from '../../src/domain/entities/economicKind'
import type { Category, Merchant, PersonAllocation, Tag, Transaction, TransactionTag } from '../../src/domain/entities/types'
import { recordAsync, seeded } from './goldenKit'

/**
 * شاشة العمليات — تاني حالة استخدام في ملفات المرجع (بعد الرئيسية).
 * المدخل فيه كل محتوى المستودعات، فكوتلن بتبني نفس المستودعات ولازم تطلع نفس الناتج.
 * ⚠️ بيانات وهمية بالكامل.
 *
 * الزيادة عن الرئيسية: أسماء الوسوم لكل عملية، وأسماء التاجر (الأساسي والبديلة) لكل عملية،
 * والمستودعات التلاتة دول **اختيارية** في التوقيع — فلازم حالات من غيرهم كمان.
 */
export async function transactionsGolden() {
  const rnd = seeded(131)
  const cats: Category[] = [
    { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
    { id: 'fuel', parentId: null, name: 'بنزين', iconKey: 'fuel', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'transport' },
    { id: 'shopping', parentId: null, name: 'تسوق', iconKey: 'shopping-basket', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 3, groupKey: 'personal' },
  ]

  const merchants: Merchant[] = [
    { id: 'm-amazon', displayName: 'أمازون', normalizedName: 'امازون', aliases: ['amazon', 'amazon.sa'] },
    { id: 'm-stc', displayName: 'STC', normalizedName: 'stc', aliases: ['اس تي سي'] },
    { id: 'm-cafe', displayName: 'قهوة الحي', normalizedName: 'قهوه الحي' },
  ]
  /** أسماء خام بتتطبع لتاجر معروف (أو لأ) — عشان مسار `rawMerchantName ⇒ merchantIndex` يتغطى. */
  const rawNames = ['AMAZON', 'امازون', 'اس تي سي', 'قهوة الحي', 'محل مجهول', null]

  const tags: Tag[] = [
    { id: 'tag-trip', normalizedName: 'رحله', displayName: 'رحلة' },
    { id: 'tag-work', normalizedName: 'شغل', displayName: 'شغل' },
  ]

  const days = [
    '2026-08-20', '2026-09-01', '2026-09-10', '2026-09-27', '2026-09-28',
    '2026-09-29', '2026-10-05', '2026-10-15', '2026-10-27', '2026-10-31',
  ]

  /** 'known' = نوعه معروف · 'unresolved' = غير محدد ومؤكد (ما بيتقدّرش) · 'estimated' = غير محدد وبيتقدّر. */
  type Kind = 'known' | 'unresolved' | 'estimated'

  const txn = (i: number, kind: Kind, periodStart: string): Transaction => {
    const unclassified = kind !== 'known'
    const raw = rnd.pick(rawNames)
    const t: Transaction = {
      id: `t-${i}`,
      occurredAt: unclassified ? periodStart : rnd.pick(days),
      datePrecision: 'day',
      sourceOrder: rnd.int(0, 4),
      economicKind: unclassified ? 'unclassified' : rnd.pick(ALL_ECONOMIC_KINDS),
      economicKindConfirmed: kind === 'unresolved' ? true : kind === 'estimated' ? false : rnd.next() < 0.7,
      observedDirection: unclassified ? 'in' : rnd.next() < 0.75 ? 'out' : 'in',
      amountMinor: rnd.int(1, 300_000),
      currency: 'SAR',
      categoryConfirmed: false,
      excludedFromBudget: rnd.next() < 0.12,
      reviewState: 'suggested',
      isCashTagged: false,
      createdAt: '2026-09-01T00:00:00.000Z',
      updatedAt: '2026-09-01T00:00:00.000Z',
    }
    if (raw) t.rawMerchantName = raw
    if (!unclassified && rnd.next() < 0.8) t.categoryId = rnd.pick(cats).id
    return t
  }

  const shapes: { known: number; unresolved: number; estimated: number }[] = [
    { known: 0, unresolved: 0, estimated: 0 },
    { known: 0, unresolved: 5, estimated: 0 },
    { known: 6, unresolved: 3, estimated: 0 },
    { known: 12, unresolved: 0, estimated: 0 },
    { known: 14, unresolved: 4, estimated: 7 },
    { known: 3, unresolved: 0, estimated: 4 },
  ]

  const cases = []
  for (let s = 0; s < 18; s++) {
    const shape = shapes[s % shapes.length]!
    const payday = rnd.pick([28, 1, 31])
    const period = buildPeriod(2026, 9, payday)
    const plan: Kind[] = [
      ...Array<Kind>(shape.known).fill('known'),
      ...Array<Kind>(shape.unresolved).fill('unresolved'),
      ...Array<Kind>(shape.estimated).fill('estimated'),
    ]
    const transactions = plan.map((kind, i) => txn(s * 100 + i, kind, period.start))
    const allocations: PersonAllocation[] = transactions
      .filter(() => rnd.next() < 0.25)
      .map((t, i) => ({
        id: `a-${s}-${i}`,
        transactionId: t.id,
        personId: 'p-1',
        allocationKind: rnd.next() < 0.5 ? 'receivable' : 'gift',
        amountMinor: rnd.int(0, t.amountMinor),
        currency: 'SAR',
      }))
    // روابط وسوم: منها المكرر (بيتشال بالاسم) ومنها اللي وسمه مش موجود (بيتساب)
    const links: TransactionTag[] = []
    for (const t of transactions) {
      if (rnd.next() < 0.35) links.push({ id: `l-${links.length}`, transactionId: t.id, tagId: rnd.pick(tags).id })
      if (rnd.next() < 0.1) links.push({ id: `l-${links.length}`, transactionId: t.id, tagId: rnd.pick([...tags.map((x) => x.id), 'tag-ghost']) })
    }
    // المستودعات الاختيارية: مرة موجودة ومرة لأ — التوقيع بيسمح بالاتنين
    const withOptional = s % 4 !== 3
    // مسارين لتحديد الفترة: كائن الفترة نفسه، أو اليوم (`periodForDate`)
    const usePeriod = s % 2 === 0
    const options = {
      year: 2026,
      month: 9,
      payday,
      today: rnd.pick(['2026-09-05', '2026-09-20', '2026-10-10', '2026-10-27']),
      usePeriod,
      withOptional,
    }

    const input = { transactions, categories: cats, allocations, merchants, tags, transactionTags: links, options }
    cases.push(await recordAsync(input, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(transactions)
      const allocationRepo = new MemoryAllocationRepository()
      await allocationRepo.saveMany(allocations)
      const load = makeLoadTransactionsScreen({
        txns,
        categories: new MemoryCategoryRepository(cats),
        allocations: allocationRepo,
        merchants: withOptional ? new MemoryMerchantRepository(merchants) : undefined,
        tags: withOptional ? new MemoryTagRepository(tags) : undefined,
        transactionTags: withOptional ? new MemoryTransactionTagRepository(links) : undefined,
      })
      const data = await load(
        usePeriod
          ? { period: buildPeriod(options.year, options.month, payday), today: options.today, payday }
          : { today: options.today, payday },
      )
      // العمليات والتصنيفات بتترجع زي ما هي من المستودع ⇒ المعرّفات تكفي (الترتيب هو المهم)
      return {
        periodKey: data.period.key,
        periodRange: data.periodRange,
        transactionIds: data.transactions.map((t) => t.id),
        categoryIds: data.categories.map((c) => c.id),
        tagNamesByTransaction: data.tagNamesByTransaction,
        merchantNamesByTransaction: data.merchantNamesByTransaction,
        incomeMinor: data.incomeMinor,
        expenseMinor: data.expenseMinor,
        remainingMinor: data.remainingMinor,
        savingsRatePercent: data.savingsRatePercent,
        unclassifiedCount: data.unclassifiedCount,
        estimatedCount: data.estimatedCount,
        needsReviewCount: data.needsReviewCount,
        totalCount: data.totalCount,
      }
    }))
  }

  return { loadTransactionsScreen: cases }
}
