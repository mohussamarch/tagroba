import { describe, it, expect } from 'vitest'
import { makeManageProjects } from '../../src/application/useCases/manageProjects'
import { checkProjectRule, membershipChanges, planSyncLinks, summarizeProject, syncStart } from '../../src/domain/projects'
import { MemoryProjectLinkRepository, MemoryProjectRepository, MemoryProjectRuleRepository } from '../../src/infrastructure/memory/memoryProjectRepositories'
import { MemoryAllocationRepository, MemoryCategoryRepository, MemoryTransactionRepository, SequentialIdGenerator } from '../../src/infrastructure/memory/memoryRepositories'
import { MemorySyncCursor } from '../../src/infrastructure/memory/memorySharedMerchantCatalog'
import type { Transaction } from '../../src/domain/entities/types'
import type { ProjectRule } from '../../src/domain/entities/projectEntities'

/** المشاريع — OVERRIDES §34 (ردود المالك 2026-09-18). أسماء ومبالغ وهمية. */

const txn = (id: string, extra: Partial<Transaction> = {}): Transaction => ({
  id, occurredAt: '2026-09-10', datePrecision: 'day', sourceOrder: 1, economicKind: 'purchase', economicKindConfirmed: true,
  observedDirection: 'out', amountMinor: 10000, currency: 'SAR', categoryConfirmed: false, excludedFromBudget: false,
  reviewState: 'suggested', isCashTagged: false, createdAt: '2026-09-10T10:00:00Z', updatedAt: '2026-09-10T10:00:00Z',
  rawMerchantName: 'SHOP', ...extra,
})

function system(seed: Transaction[] = []) {
  const txns = new MemoryTransactionRepository()
  void txns.saveMany(seed)
  const allocations = new MemoryAllocationRepository()
  const links = new MemoryProjectLinkRepository()
  const clock = { now: '2026-09-15T12:00:00Z', nowIso() { return this.now } }
  const cursor = new MemorySyncCursor()
  const use = makeManageProjects({
    projects: new MemoryProjectRepository(), links, rules: new MemoryProjectRuleRepository(), txns, allocations,
    categories: new MemoryCategoryRepository(), ids: new SequentialIdGenerator(), clock, cursor,
  })
  return { use, txns, allocations, links, clock, cursor }
}

describe('حساب المشروع', () => {
  it('«صرفت» = نصيبك بس (زي الرئيسية) + المستبعد من الميزانية، و«جالك» = الدخل', async () => {
    const { use, allocations } = system([
      txn('dinner', { amountMinor: 10000 }),
      txn('hidden', { amountMinor: 2500, excludedFromBudget: true }),
      txn('sale', { observedDirection: 'in', economicKind: 'salary', amountMinor: 50000 }),
    ])
    await allocations.saveMany([{ id: 'a', transactionId: 'dinner', personId: 'friend', allocationKind: 'receivable', amountMinor: 4000, currency: 'SAR' }])
    const project = await use.create('ماكت الكلية')
    for (const id of ['dinner', 'hidden', 'sale']) await use.setMember(id, project.id, true)
    const { active } = await use.list()
    expect(active[0]!.summary).toMatchObject({ spentMinor: 6000 + 2500, receivedMinor: 50000, count: 3, estimatedCount: 0 })
  })

  it('العملية في مشروعين بتتحسب في كل واحد مرة، والنوع غير المؤكد بيتحسب تقريبي', async () => {
    const { use } = system([txn('shared'), txn('unsure', { economicKind: 'unclassified', economicKindConfirmed: false, amountMinor: 3000 })])
    const a = await use.create('أ'), b = await use.create('ب')
    await use.setMember('shared', a.id, true)
    await use.setMember('shared', b.id, true)
    await use.setMember('shared', a.id, true)
    await use.setMember('unsure', a.id, true)
    const rows = (await use.list()).active
    expect(rows.find((r) => r.project.id === a.id)!.summary).toMatchObject({ spentMinor: 13000, count: 2, estimatedCount: 1 })
    expect(rows.find((r) => r.project.id === b.id)!.summary).toMatchObject({ spentMinor: 10000, count: 1 })
  })
})

describe('القواعد', () => {
  it('القاعدة الجديدة بتقول عدد القديم ومش بتضيفه لوحدها — «ضيف القديم» بيضيفه', async () => {
    const { use } = system([txn('old1', { rawMerchantName: 'LIBRARY ONE' }), txn('old2', { rawMerchantName: 'OTHER' })])
    const project = await use.create('بحث التخرج')
    const { rule, oldMatches } = await use.addRule(project.id, { matchText: 'library', matchMode: 'contains', direction: 'out' })
    expect(oldMatches).toBe(1)
    expect((await use.detail(project.id)).transactions).toHaveLength(0)
    expect(await use.applyRuleToOld(rule.id)).toBe(1)
    expect((await use.detail(project.id)).transactions.map((t) => [t.transaction.id, t.source])).toEqual([['old1', 'rule']])
    expect(await use.applyRuleToOld(rule.id)).toBe(0)
  })

  it('الجديد بعد القاعدة بيتضاف لوحده بالاتجاه الصح، واللي اتشال ما بيرجعش', async () => {
    const { use, txns, clock } = system()
    const project = await use.create('المحل')
    await use.addRule(project.id, { matchText: 'SUPPLIER', matchMode: 'startsWith', direction: 'out' })
    clock.now = '2026-09-16T12:00:00Z'
    await txns.saveMany([
      txn('new-out', { rawMerchantName: 'SUPPLIER CO', createdAt: '2026-09-16T09:00:00Z' }),
      txn('new-in', { rawMerchantName: 'SUPPLIER CO', observedDirection: 'in', economicKind: 'personal_sale', createdAt: '2026-09-16T09:00:00Z' }),
    ])
    const detail = await use.detail(project.id)
    expect(detail.transactions.map((t) => t.transaction.id)).toEqual(['new-out'])
    await use.setMember('new-out', project.id, false)
    await use.setMember('new-out', project.id, false)
    expect((await use.detail(project.id)).transactions).toHaveLength(0)
    await use.setMember('new-out', project.id, true)
    expect((await use.detail(project.id)).transactions.map((t) => t.source)).toEqual(['manual'])
  })

  it('المراجعة بتبدأ من آخر مرة: مفيش قراية تانية للي اتراجع، ومفيش قواعد = مفيش قراية خالص', async () => {
    const { use, txns, cursor } = system()
    let reads = 0
    const original = txns.listCreatedAfter.bind(txns)
    txns.listCreatedAfter = async (iso: string) => { reads += 1; return original(iso) }
    const project = await use.create('س')
    await use.list()
    expect(reads).toBe(0)
    await use.addRule(project.id, { matchText: 'ABC', matchMode: 'contains', direction: 'any' })
    await txns.saveMany([txn('n', { rawMerchantName: 'ABC', createdAt: '2026-09-20T00:00:00Z' })])
    await use.list()
    expect(cursor.read()).toBe('2026-09-20T00:00:00Z')
    await use.list()
    expect(reads).toBe(2)
    expect((await use.detail(project.id)).transactions).toHaveLength(1)
  })

  it('قاعدة مقفولة ما بتضيفش، والقاعدة ما تضيفش القديم عليها حتى لو اتراجع مع قاعدة أحدث', () => {
    const early: ProjectRule = { id: 'r1', projectId: 'p', matchText: 'ABC', matchMode: 'contains', direction: 'any', enabled: true, createdAt: '2026-09-01T00:00:00Z' }
    const late: ProjectRule = { ...early, id: 'r2', projectId: 'q', createdAt: '2026-09-10T00:00:00Z' }
    const between = txn('between', { rawMerchantName: 'ABC', createdAt: '2026-09-05T00:00:00Z' })
    const links = planSyncLinks([early, late, { ...early, id: 'off', projectId: 'z', enabled: false }], [between], [], 'now')
    expect(links.map((l) => l.projectId)).toEqual(['p'])
    expect(syncStart([late, early], null)).toBe('2026-09-01T00:00:00Z')
    expect(syncStart([early], '2026-09-12T00:00:00Z')).toBe('2026-09-12T00:00:00Z')
    expect(syncStart([{ ...early, enabled: false }], null)).toBeNull()
  })
})

describe('الأسماء والأرشيف والتحقق', () => {
  it('الاسم ما يتكررش، والمؤرشف بيفضل بمجاميعه وبيظهر في «ضيف لمشروع» بس لو العملية فيه', async () => {
    const { use } = system([txn('t')])
    const a = await use.create('  رحلة   العمرة ')
    expect(a.name).toBe('رحلة العمرة')
    await expect(use.create('رحلة العمرة')).rejects.toThrow('نفس الاسم')
    const b = await use.create('قديم')
    await use.setMember('t', a.id, true)
    await use.setArchived(a.id, true)
    const list = await use.list()
    expect(list.archived.map((r) => [r.project.id, r.summary.count])).toEqual([[a.id, 1]])
    expect((await use.membership('t')).map((m) => [m.project.id, m.member])).toEqual([[a.id, true], [b.id, false]])
    await use.rename(b.id, 'جديد')
    await expect(use.rename(b.id, 'رحلة العمرة')).rejects.toThrow('نفس الاسم')
  })

  it('القاعدة لازم نص من حرفين، وطريقة واتجاه معروفين؛ والإضافة لمشروع مش موجود مرفوضة', async () => {
    expect(() => checkProjectRule({ matchText: ' a ', matchMode: 'contains', direction: 'out' })).toThrow('حرفين')
    // @ts-expect-error — قيمة غلط جاية من بيانات قديمة
    expect(() => checkProjectRule({ matchText: 'abc', matchMode: 'regex', direction: 'out' })).toThrow('المطابقة')
    const { use } = system([txn('t')])
    await expect(use.setMember('t', 'missing', true)).rejects.toThrow('مش موجود')
    expect(membershipChanges([], 'p', 't', false, 'now')).toEqual([])
    expect(summarizeProject([], [], new Map())).toEqual({ spentMinor: 0, receivedMinor: 0, count: 0, estimatedCount: 0, needsReviewCount: 0 })
  })
})
