import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import tree from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { buildCategories } from '../../src/infrastructure/import/referenceLoader'
import { makeMigrateCategories } from '../../src/application/useCases/migrateCategories'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { MemoryCategoryRepository, FixedClock } from '../../src/infrastructure/memory/memoryRepositories'
import { emptyStoredData } from '../../src/domain/idRepair'

/** نقل حساب قديم للشجرة — OVERRIDES §28 و§28.1 (نسخة ← إضافة ← تعديل ← إخفاء). */

const DESIGN = resolve(__dirname, '../../design-source/masroofi-claude-code/design')
const tokens = JSON.parse(readFileSync(resolve(DESIGN, 'tokens.json'), 'utf8')) as { categories: { name: string; baseColor: string; icon: string }[] }
const legacyNames = tokens.categories.map((c) => c.name)
const legacy = buildCategories(tokens.categories)
const built = buildCategoryTree(tree)
const idOf = (name: string) => legacy.find((c) => c.name === name)!.id
const homeId = built.categories.find((c) => c.name === 'المنزل' && !c.parentId)!.id

function system(options: { truncate?: boolean } = {}) {
  const data = emptyStoredData()
  data.categories = legacy.map((c) => ({ docId: c.id, data: { ...c } }))
  data.transactions.push({ docId: 't1', data: { id: 't1', categoryId: idOf('فواتير ومرافق'), categoryConfirmed: true, amountMinor: 1234 } })
  data.transactions.push({ docId: 't2', data: { id: 't2', categoryId: idOf('تأمين'), categoryConfirmed: false, amountMinor: 50 } })
  data.rules.push({ docId: 'r1', data: { id: 'r1', categoryId: idOf('خدمات منزلية'), matchText: 'LAUNDRY' } })
  const store = memoryIdRepair(data)
  const categories = new MemoryCategoryRepository(legacy)
  const backup = memoryRepairBackup(options)
  const migrate = makeMigrateCategories({
    port: store, categories, backup, clock: new FixedClock('2026-09-15T09:00:00.000Z'), tree: built, legacyNames,
  })
  return { store, categories, backup, migrate }
}

describe('نقل التصنيفات على الحساب', () => {
  it('المعاينة ما بتكتبش حاجة', async () => {
    const s = system()
    const before = JSON.stringify(s.store.snapshot())
    const plan = await s.migrate.preview()
    expect(plan.patches.length).toBeGreaterThan(0)
    expect(JSON.stringify(s.store.snapshot())).toBe(before)
    expect(s.backup.files.size).toBe(0)
  })

  it('التطبيق: نسخة ← إضافة الناقص ← تعديل حقل التصنيف بس ← إخفاء المدموج، و«تأمين» ما يتلمسش', async () => {
    const s = system()
    const plan = await s.migrate.preview()
    const outcome = await s.migrate.apply(plan)
    expect(outcome).toMatchObject({ created: plan.create.length, written: plan.patches.length, skipped: 0 })
    expect(outcome.backup?.fileName).toMatch(/^masroufy-before-category-move-/)

    const after = s.store.snapshot()
    expect(after.transactions.find((r) => r.docId === 't1')!.data).toEqual({ id: 't1', categoryId: homeId, categoryConfirmed: true, amountMinor: 1234 })
    expect(after.rules.find((r) => r.docId === 'r1')!.data.categoryId).toBe(homeId)
    expect(after.transactions.find((r) => r.docId === 't2')!.data.categoryId).toBe(idOf('تأمين'))

    const stored = await s.categories.listAll()
    expect(stored.find((c) => c.id === homeId)).toMatchObject({ name: 'المنزل', groupKey: 'home' })
    expect(stored.find((c) => c.id === idOf('فواتير ومرافق'))?.active).toBe(false)
    expect(stored.find((c) => c.id === idOf('تأمين'))?.active).toBe(true) // الغامض بيفضل ظاهر
    expect(stored.find((c) => c.id === idOf('مطاعم وقهوة'))?.active).toBe(true)
    expect(outcome.hidden).toBe(plan.moves.length)

    const backupContent = JSON.parse([...s.backup.files.values()][0])
    expect(backupContent.documents.some((d: { docId: string }) => d.docId === 't1')).toBe(true)
  })

  it('نسخة ناقصة ⇒ ولا تصنيف اتضاف ولا حقل اتعدل ولا حاجة اتخفت', async () => {
    const s = system({ truncate: true })
    const before = JSON.stringify(s.store.snapshot())
    const plan = await s.migrate.preview()
    await expect(s.migrate.apply(plan)).rejects.toThrow('ناقصة')
    expect(JSON.stringify(s.store.snapshot())).toBe(before)
    expect((await s.categories.listAll()).length).toBe(legacy.length)
    expect((await s.categories.listAll()).every((c) => c.active)).toBe(true)
  })

  it('تعديل اتغير بعد المعاينة بيتخطى، والتطبيق التاني ما بيعملش حاجة', async () => {
    const s = system()
    const plan = await s.migrate.preview()
    await s.store.apply([{ group: 'transactions', docId: 't1', fields: { categoryId: idOf('مطاعم وقهوة') } }])
    const outcome = await s.migrate.apply(plan)
    expect(outcome.skipped).toBe(0) // t1 خرجت من الخطة الجديدة أصلًا
    expect(s.store.snapshot().transactions.find((r) => r.docId === 't1')!.data.categoryId).toBe(idOf('مطاعم وقهوة'))

    // على الخادم القراءة الكاملة والتصنيفات نفس المجموعة؛ هنا بنبني الحالة المشتركة بعد التطبيق
    const shared = s.store.snapshot()
    shared.categories = (await s.categories.listAll()).map((c) => ({ docId: c.id, data: { ...c } }))
    const store = memoryIdRepair(shared)
    const migrate = makeMigrateCategories({
      port: store, categories: s.categories, backup: memoryRepairBackup(), clock: new FixedClock('2026-09-15T10:00:00.000Z'), tree: built, legacyNames,
    })
    const second = await migrate.preview()
    expect(second.create).toEqual([])
    expect(second.patches).toEqual([])
    expect(await migrate.apply(second)).toEqual({ created: 0, written: 0, hidden: 0, skipped: 0, backup: null })
  })
})
