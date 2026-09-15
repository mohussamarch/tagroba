import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import tree from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { loadReferences } from '../../src/infrastructure/import/referenceLoader'
import { makeRestoreDefaultReferences } from '../../src/application/useCases/restoreDefaultReferences'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { MemoryMerchantRepository, MemoryRuleRepository, FixedClock } from '../../src/infrastructure/memory/memoryRepositories'
import { emptyStoredData } from '../../src/domain/idRepair'

/** رجوع القواعد والتجار الافتراضيين لحساب — OVERRIDES §28.1 (معاينة ← نسخة ← إضافة ← اقتراح). */

const FIX = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')
const readJson = <T>(p: string): T => JSON.parse(readFileSync(p, 'utf8')) as T
const built = buildCategoryTree(tree)
const defaults = loadReferences(
  readJson(resolve(FIX, 'rule-reference.json')), readJson(resolve(FIX, 'merchant-reference.json')), built.categories, built,
)
const walletsId = built.categories.find((c) => c.name === 'محافظ رقمية')!.id

function system(options: { truncate?: boolean } = {}) {
  const data = emptyStoredData()
  data.categories = built.categories.map((c) => ({ docId: c.id, data: { ...c } }))
  const base = { amountMinor: 1000, occurredAt: '2026-09-01', categoryConfirmed: false, reviewState: 'needs_review' }
  data.transactions.push({ docId: 't1', data: { ...base, rawMerchantName: 'BARQ TRANSFER' } })
  data.transactions.push({ docId: 't2', data: { ...base, rawMerchantName: 'BARQ', categoryId: walletsId, categoryConfirmed: true, reviewState: 'confirmed' } })
  data.transactions.push({ docId: 't3', data: { ...base, rawMerchantName: 'محل مش معروف' } })
  const store = memoryIdRepair(data)
  const rules = new MemoryRuleRepository([])
  const merchants = new MemoryMerchantRepository([])
  const backup = memoryRepairBackup(options)
  const restore = makeRestoreDefaultReferences({ port: store, rules, merchants, backup, clock: new FixedClock('2026-09-15T11:00:00.000Z'), defaults })
  return { store, rules, merchants, backup, restore }
}

describe('رجوع القواعد والتجار الافتراضيين', () => {
  it('المعاينة ما بتكتبش حاجة', async () => {
    const s = system()
    const before = JSON.stringify(s.store.snapshot())
    const plan = await s.restore.preview()
    expect(plan.rules).toHaveLength(defaults.rules.length)
    expect(plan.suggestions.map((x) => x.transactionId)).toEqual(['t1'])
    expect(JSON.stringify(s.store.snapshot())).toBe(before)
    expect(await s.rules.listAll()).toEqual([])
    expect(s.backup.files.size).toBe(0)
  })

  it('التطبيق: نسخة ← إضافة ← اقتراح مش مؤكد، والمؤكد ما يتلمسش', async () => {
    const s = system()
    const outcome = await s.restore.apply(await s.restore.preview())
    expect(outcome).toMatchObject({ rules: defaults.rules.length, merchants: defaults.merchants.length, suggested: 1, skipped: 0 })
    expect(outcome.backup?.fileName).toMatch(/^masroufy-before-default-rules-/)
    expect(await s.rules.listAll()).toHaveLength(defaults.rules.length)
    const after = s.store.snapshot().transactions
    expect(after.find((r) => r.docId === 't1')!.data).toMatchObject({ categoryId: walletsId, reviewState: 'suggested', categoryConfirmed: false, amountMinor: 1000 })
    expect(after.find((r) => r.docId === 't2')!.data).toMatchObject({ reviewState: 'confirmed', categoryConfirmed: true })
    expect(after.find((r) => r.docId === 't3')!.data.categoryId).toBeUndefined()
  })

  it('نسخة ناقصة ⇒ ولا قاعدة ولا تاجر ولا اقتراح', async () => {
    const s = system({ truncate: true })
    const before = JSON.stringify(s.store.snapshot())
    await expect(s.restore.apply(await s.restore.preview())).rejects.toThrow('ناقصة')
    expect(JSON.stringify(s.store.snapshot())).toBe(before)
    expect(await s.rules.listAll()).toEqual([])
    expect(await s.merchants.listAll()).toEqual([])
  })

  it('عملية اتصنفت بعد المعاينة بتتخطى', async () => {
    const s = system()
    const plan = await s.restore.preview()
    await s.store.apply([{ group: 'transactions', docId: 't1', fields: { categoryId: built.categories[0].id } }])
    const outcome = await s.restore.apply(plan)
    expect(outcome).toMatchObject({ suggested: 0, skipped: 1 })
    expect(s.store.snapshot().transactions.find((r) => r.docId === 't1')!.data.categoryId).toBe(built.categories[0].id)
  })
})
