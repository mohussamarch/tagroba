import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { makeSeedUserReferences } from '../../src/application/useCases/seedUserReferences'
import { makeCategorizeTransactions } from '../../src/application/useCases/categorizeTransactions'
import {
  MemoryCategoryRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildCategories, loadReferences } from '../../src/infrastructure/import/referenceLoader'
import { parseMoney } from '../../src/domain/money'
import type { Transaction } from '../../src/domain/entities/types'

/**
 * spec/05: «ملفات القواعد مرجع أولي **قابل للتحرير**، وليست سياسة لا تتغير.»
 *
 * القابلية للتحرير تستلزم أمرين:
 *   ١. أن تُخزَّن، وإلا ضاع التعديل عند إعادة الفتح
 *   ٢. ألا يُعاد المرجع الأصلي فوقها، وإلا أُلغي التعديل بلا علم المستخدم
 */

const FIX = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')
const DESIGN = resolve(__dirname, '../../design-source/masroofi-claude-code/design')
const readJson = <T>(p: string): T => JSON.parse(readFileSync(p, 'utf8')) as T

const tokens = readJson<{ categories: { name: string; baseColor: string; icon: string }[] }>(
  resolve(DESIGN, 'tokens.json'),
)
const rawRules = readJson<{ word: string; cat: string }[]>(resolve(FIX, 'rule-reference.json'))
const rawMerchants = readJson<{ name: string; cat: string; confidence: string }[]>(
  resolve(FIX, 'merchant-reference.json'),
)

function makeSystem() {
  const categories = new MemoryCategoryRepository([]) // فاضي: مستخدم جديد
  const rules = new MemoryRuleRepository([])
  const merchants = new MemoryMerchantRepository([])
  const uow = new PassthroughUnitOfWork()

  const categoryList = buildCategories(tokens.categories)
  const refs = loadReferences(rawRules, rawMerchants, categoryList)
  const source = { categories: categoryList, rules: refs.rules, merchants: refs.merchants }

  return {
    categories,
    rules,
    merchants,
    source,
    seed: makeSeedUserReferences({ categories, rules, merchants, uow }),
  }
}

describe('زرع المراجع الأولية — ARCHITECTURE.md §10.6', () => {
  it('أول دخول: يزرع التصنيفات والقواعد والتجار', async () => {
    const sys = makeSystem()
    expect(await sys.categories.listAll()).toHaveLength(0)

    const outcome = await sys.seed(sys.source)

    expect(outcome.seeded).toBe(true)
    expect(outcome.categories).toBe(sys.source.categories.length)
    expect(outcome.rules).toBe(sys.source.rules.length)
    expect(outcome.rules).toBeGreaterThan(100)

    // المراجع بقت محفوظة فعلًا، فتقرأ من التخزين لا من الملف
    expect(await sys.categories.listAll()).toHaveLength(sys.source.categories.length)
    expect(await sys.rules.listAll()).toHaveLength(sys.source.rules.length)
  })

  it('الدخول الثاني: لا يزرع ولا يلمس شيئًا', async () => {
    const sys = makeSystem()
    await sys.seed(sys.source)

    const second = await sys.seed(sys.source)
    expect(second.seeded).toBe(false)
    expect(second.reason).toContain('مش هنكتب فوق تعديلاتك')
  })

  it('تعديل المستخدم لا يُدهس عند إعادة الفتح — شرط spec/05', async () => {
    const sys = makeSystem()
    await sys.seed(sys.source)

    // المستخدم عطّل قاعدة وأضاف واحدة خاصة به
    const original = await sys.rules.listAll()
    const edited = original
      .filter((r) => r.matchText !== 'BARQ')
      .concat({
        id: 'rule-user-1',
        priority: 0, // أعلى أولوية
        matchText: 'قهوتي المفضلة',
        matchMode: 'contains',
        categoryId: original[0].categoryId,
        enabled: true,
      })
    await sys.rules.saveMany(edited)

    // إعادة فتح التطبيق ⇒ الزرع يُستدعى مرة أخرى
    await sys.seed(sys.source)

    const after = await sys.rules.listAll()
    expect(after.some((r) => r.matchText === 'قهوتي المفضلة')).toBe(true) // إضافته باقية
    expect(after.some((r) => r.matchText === 'BARQ')).toBe(false) // حذفه محترم
    expect(after).toHaveLength(edited.length)
  })

  it('القواعد المزروعة تصنّف فعلًا — الزرع مش مجرد تخزين', async () => {
    const sys = makeSystem()
    await sys.seed(sys.source)

    const txns = new MemoryTransactionRepository()
    const transaction: Transaction = {
      id: 't1',
      occurredAt: '2026-09-01',
      datePrecision: 'day',
      sourceOrder: 1,
      economicKind: 'unclassified',
      economicKindConfirmed: false,
      observedDirection: 'out',
      amountMinor: parseMoney('120.00'),
      currency: 'SAR',
      categoryConfirmed: false,
      excludedFromBudget: false,
      reviewState: 'needs_review',
      isCashTagged: false,
      rawMerchantName: 'BARQ TRANSFER',
      createdAt: '',
      updatedAt: '',
    }
    await txns.saveMany([transaction])

    const categorizer = makeCategorizeTransactions({
      txns,
      merchants: sys.merchants,
      categories: sys.categories,
      rules: sys.rules,
      uow: new PassthroughUnitOfWork(),
      clock: new FixedClock('2026-09-07T00:00:00.000Z'),
    })

    const report = await categorizer.apply([transaction])
    expect(report.changed).toHaveLength(1)

    const categoryList = await sys.categories.listAll()
    const wallets = categoryList.find((c) => c.name === 'محافظ رقمية')!
    expect(report.changed[0].toCategoryId).toBe(wallets.id)
    expect(report.changed[0].source).toBe('rule')

    const stored = (await txns.findByIds(['t1']))[0]
    expect(stored.categoryId).toBe(wallets.id)
    expect(stored.reviewState).toBe('suggested') // مقترح لا مؤكد
  })

  it('التصنيف المؤكد من المستخدم لا يُكتب فوقه — spec/05', async () => {
    const sys = makeSystem()
    await sys.seed(sys.source)

    const txns = new MemoryTransactionRepository()
    const categoryList = await sys.categories.listAll()
    const chosen = categoryList.find((c) => c.name === 'ترفيه')!

    const transaction: Transaction = {
      id: 't1',
      occurredAt: '2026-09-01',
      datePrecision: 'day',
      sourceOrder: 1,
      economicKind: 'purchase',
      economicKindConfirmed: true,
      observedDirection: 'out',
      amountMinor: parseMoney('120.00'),
      currency: 'SAR',
      categoryId: chosen.id,
      categoryConfirmed: true, // ← المستخدم أكّده
      excludedFromBudget: false,
      reviewState: 'confirmed',
      isCashTagged: false,
      rawMerchantName: 'BARQ TRANSFER', // قاعدة BARQ تقول «محافظ رقمية»
      createdAt: '',
      updatedAt: '',
    }
    await txns.saveMany([transaction])

    const categorizer = makeCategorizeTransactions({
      txns,
      merchants: sys.merchants,
      categories: sys.categories,
      rules: sys.rules,
      uow: new PassthroughUnitOfWork(),
      clock: new FixedClock('2026-09-07T00:00:00.000Z'),
    })

    const report = await categorizer.apply([transaction])
    expect(report.changed).toHaveLength(0)
    expect(report.skippedConfirmed).toEqual(['t1'])

    const stored = (await txns.findByIds(['t1']))[0]
    expect(stored.categoryId).toBe(chosen.id) // «ترفيه» باقية رغم قاعدة BARQ
  })
})
