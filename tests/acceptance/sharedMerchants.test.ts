import { describe, it, expect } from 'vitest'
import {
  baselineCatalog,
  contributionFor,
  effectiveEntry,
  planAccountMerchantSync,
  sharedMerchantKey,
  type SharedMerchantEntry,
} from '../../src/domain/sharedMerchantCatalog'
import { makeSharedMerchants } from '../../src/application/useCases/sharedMerchants'
import { MemorySharedMerchantCatalog, MemorySyncCursor } from '../../src/infrastructure/memory/memorySharedMerchantCatalog'
import { MemoryMerchantRepository } from '../../src/infrastructure/memory/memoryRepositories'
import type { Merchant } from '../../src/domain/entities/types'

/** قاعدة التجار المشتركة — OVERRIDES §25 و§25.1. أسماء وهمية بس. */

const tree = new Set(['cat-food', 'cat-food--coffee', 'cat-home'])
const purchase = { economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'Cafe Nour  Riyadh' } as const
const entry = (over: Partial<SharedMerchantEntry> = {}): SharedMerchantEntry => ({
  normalizedName: 'CAFE NOUR RIYADH', displayName: 'Cafe Nour Riyadh', aliases: [], categoryId: 'cat-food--coffee', confirmed: false, ...over,
})

describe('المساهمة في القاعدة المشتركة', () => {
  it('شراء مؤكد بتصنيف من الشجرة ⇒ اسم وتصنيف مش مؤكدين، ومفيش مبلغ ولا تاريخ', () => {
    const out = contributionFor({ transaction: purchase, categoryId: 'cat-food--coffee', treeCategoryIds: tree, current: undefined })
    expect(out).toEqual({ normalizedName: 'CAFE NOUR RIYADH', displayName: 'Cafe Nour Riyadh', aliases: [], categoryId: 'cat-food--coffee', confirmed: false })
    expect(Object.keys(out!).sort()).toEqual(['aliases', 'categoryId', 'confirmed', 'displayName', 'normalizedName'])
  })

  it('التحويل والوارد ⇒ ما يتبعتش (أسماء أشخاص)', () => {
    const transfer = { ...purchase, economicKind: 'transfer_internal' } as never
    expect(contributionFor({ transaction: transfer, categoryId: 'cat-home', treeCategoryIds: tree, current: undefined })).toBeNull()
    expect(contributionFor({ transaction: { ...purchase, observedDirection: 'in' }, categoryId: 'cat-home', treeCategoryIds: tree, current: undefined })).toBeNull()
  })

  it('اسم فيه رقم طويل، أو تصنيف عمله المستخدم بنفسه ⇒ ما يتبعتش', () => {
    expect(contributionFor({ transaction: { ...purchase, rawMerchantName: 'POS 123456 SHOP' }, categoryId: 'cat-home', treeCategoryIds: tree, current: undefined })).toBeNull()
    expect(contributionFor({ transaction: purchase, categoryId: 'cat-my-own', treeCategoryIds: tree, current: undefined })).toBeNull()
  })

  it('قاعدة التعارض: تاجر مؤكد ⇒ تغيير المستخدم ليه هو بس، ونفس التصنيف ⇒ مفيش كتابة', () => {
    expect(contributionFor({ transaction: purchase, categoryId: 'cat-home', treeCategoryIds: tree, current: entry({ confirmed: true }) })).toBeNull()
    expect(contributionFor({ transaction: purchase, categoryId: 'cat-food--coffee', treeCategoryIds: tree, current: entry() })).toBeNull()
    expect(contributionFor({ transaction: purchase, categoryId: 'cat-home', treeCategoryIds: tree, current: entry() })?.categoryId).toBe('cat-home')
  })
})

describe('الأساس من المرجع قصاد القاعدة المشتركة', () => {
  const baseline = baselineCatalog([
    { id: 'm1', displayName: 'Cafe Nour Riyadh', normalizedName: 'CAFE NOUR RIYADH', verifiedCategoryId: 'cat-food--coffee' },
    { id: 'm2', displayName: 'Beit Store', normalizedName: 'BEIT STORE' },
  ])
  it('المؤكد في المرجع بيبدأ مؤكد، والباقي مش مؤكد', () => {
    expect(baseline.get('CAFE-NOUR-RIYADH')?.confirmed).toBe(true)
    expect(baseline.get('BEIT-STORE')).toMatchObject({ confirmed: false, categoryId: null })
  })
  it('تعديل مش مؤكد ما يغلبش مؤكد المرجع، والتأكيد من اللوحة يغلب', () => {
    const base = baseline.get('CAFE-NOUR-RIYADH')
    expect(effectiveEntry(base, entry({ categoryId: 'cat-home' }))).toBe(base)
    expect(effectiveEntry(base, entry({ categoryId: 'cat-home', confirmed: true }))?.categoryId).toBe('cat-home')
  })
})

describe('المزامنة لحساب المستخدم', () => {
  const account: Merchant[] = [
    { id: 'a1', displayName: 'Beit Store', normalizedName: 'BEIT STORE' },
    { id: 'a2', displayName: 'Mine', normalizedName: 'MINE SHOP', verifiedCategoryId: 'cat-home' },
  ]
  it('المؤكد بس بيوصل، والناقص بيتكمّل، وتصنيف المستخدم ما يتكتبش فوقه', () => {
    const plan = planAccountMerchantSync([
      entry({ confirmed: true }),
      entry({ normalizedName: 'BEIT STORE', categoryId: 'cat-home', confirmed: true }),
      entry({ normalizedName: 'MINE SHOP', categoryId: 'cat-food', confirmed: true }),
      entry({ normalizedName: 'NOT CONFIRMED', confirmed: false }),
      entry({ normalizedName: 'OUTSIDE TREE', categoryId: 'cat-x', confirmed: true }),
    ], account, tree)
    expect(plan.add.map((m) => [m.id, m.verifiedCategoryId])).toEqual([['merch-shared-CAFE-NOUR-RIYADH', 'cat-food--coffee']])
    expect(plan.fill).toEqual([{ ...account[0], verifiedCategoryId: 'cat-home' }])
  })

  it('من أول لآخر: مساهمة ← تأكيد من اللوحة ← مزامنة بتضيف مرة واحدة والمؤشر بيتقدم', async () => {
    const catalog = new MemorySharedMerchantCatalog()
    const merchants = new MemoryMerchantRepository()
    const cursor = new MemorySyncCursor()
    const shared = makeSharedMerchants({ catalog, merchants, baseline: [], treeCategoryIds: tree, cursor })

    expect(await shared.contribute(purchase, 'cat-food--coffee')).toBe('shared')
    expect(await shared.sync()).toEqual({ changes: 1, added: 0, filled: 0 }) // مش مؤكد ⇒ ما يوصلش الحساب
    expect(await merchants.listAll()).toEqual([])

    catalog.confirmFromConsole('CAFE NOUR RIYADH', 'cat-food--coffee', '2026-02-01T00:00:00.000Z')
    expect(await shared.sync()).toEqual({ changes: 1, added: 1, filled: 0 })
    expect(cursor.read()).toBe('2026-02-01T00:00:00.000Z')
    expect(await shared.sync()).toEqual({ changes: 0, added: 0, filled: 0 })
    expect((await merchants.listAll()).map((m) => m.id)).toEqual([`merch-shared-${sharedMerchantKey('CAFE NOUR RIYADH')}`])

    // بعد التأكيد: تغيير المستخدم ما بيتبعتش
    expect(await shared.contribute(purchase, 'cat-home')).toBe('skipped')
    expect((await catalog.get('CAFE NOUR RIYADH'))?.categoryId).toBe('cat-food--coffee')
  })

  it('تاجر مؤكد في المرجع ⇒ تغيير المستخدم ما بيتبعتش ولا بيقرا من السيرفر لغير الشراء', async () => {
    const catalog = new MemorySharedMerchantCatalog()
    let reads = 0
    const counted = { ...catalog, listChangedSince: catalog.listChangedSince.bind(catalog), save: catalog.save.bind(catalog), get: async (n: string) => { reads++; return catalog.get(n) } }
    const shared = makeSharedMerchants({
      catalog: counted, merchants: new MemoryMerchantRepository(), cursor: new MemorySyncCursor(), treeCategoryIds: tree,
      baseline: [{ id: 'm1', displayName: 'Cafe Nour Riyadh', normalizedName: 'CAFE NOUR RIYADH', verifiedCategoryId: 'cat-food--coffee' }],
    })
    expect(await shared.contribute(purchase, 'cat-home')).toBe('skipped')
    expect(await catalog.listChangedSince(null)).toEqual([])
    expect(await shared.contribute({ ...purchase, economicKind: 'fee' } as never, 'cat-home')).toBe('skipped')
    expect(reads).toBe(1)
  })
})
