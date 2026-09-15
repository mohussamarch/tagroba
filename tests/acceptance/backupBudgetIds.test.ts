import { describe, it, expect } from 'vitest'
import { makeFullBackup } from '../../src/application/useCases/fullBackup'
import { makeSetBudget } from '../../src/application/useCases/setBudget'
import { memoryFullBackup } from '../../src/infrastructure/memory/fullBackup'
import { MemoryBudgetRepository, PassthroughUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memoryRepositories'
import { emptyBackupData } from '../../src/domain/fullBackup'
import { normalizeBudgetIds, pointLinesAtLiveBudgets } from '../../src/domain/backupBudgetIds'
import { backupDigest } from '../../src/infrastructure/backupDigest'
import { buildPeriod } from '../../src/domain/period'
import { parseMoney } from '../../src/domain/money'

/** معرّفات الميزانيات في النسخة الشاملة — اتكشف 2026-09-15: أي حساب عمل سقف ما كانش يقدر يعمل نسخة. */
const now = '2026-09-15T00:00:00Z'
function withOldBudget() {
  const data = emptyBackupData()
  data.categories = [{ id: 'c', parentId: null, name: 'مطاعم', iconKey: 'food', lightColor: '#fff', darkColor: '#000', active: true, order: 1 }]
  data.budgets = [{ id: 'budget-random-1', periodKey: '2026-08', periodStart: '2026-08-28', periodEnd: '2026-09-27', totalLimitMinor: null, thresholdPercent: null, createdAt: now, updatedAt: now }]
  data.categoryBudgets = [{ id: 'cb1', budgetId: 'budget-random-1', categoryId: 'c', limitMinor: 15000, notifyEnabled: false, thresholdPercent: null }]
  return data
}

describe('معرّفات الميزانيات', () => {
  it('الميزانية الجديدة معرّفها مفتاح الفترة', async () => {
    const budgets = new MemoryBudgetRepository()
    const set = makeSetBudget({ budgets, uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(), clock: new FixedClock(now) })
    const period = buildPeriod(2026, 8, 28)
    await set.setCategoryLimit(period, 'c', parseMoney('150.00'))
    const budget = await budgets.findByPeriod(period.key)
    expect(budget?.id).toBe('2026-08')
    expect((await budgets.listCategoryBudgets('2026-08')).map((l) => l.budgetId)).toEqual(['2026-08'])
  })

  it('حساب بميزانية قديمة بمعرّف عشوائي بيقدر يعمل نسخة شاملة، والنسخة فيها مفتاح الفترة', async () => {
    const file = await makeFullBackup(memoryFullBackup(withOldBudget()), backupDigest).create(now)
    expect(file.data.budgets[0].id).toBe('2026-08')
    expect(file.data.categoryBudgets[0].budgetId).toBe('2026-08')
    const target = memoryFullBackup()
    await makeFullBackup(target, backupDigest).apply(file)
    expect((await target.read()).budgets[0].id).toBe('2026-08')
  })

  it('الاستعادة على نفس الحساب القديم: السقف المضاف بيشاور على المعرّف الحقيقي المتخزن', async () => {
    const source = withOldBudget()
    source.categories.push({ id: 'd', parentId: null, name: 'بقالة', iconKey: 'cart', lightColor: '#fff', darkColor: '#000', active: true, order: 2 })
    source.categoryBudgets.push({ id: 'cb2', budgetId: 'budget-random-1', categoryId: 'd', limitMinor: 5000, notifyEnabled: false, thresholdPercent: null })
    const file = await makeFullBackup(memoryFullBackup(source), backupDigest).create(now)
    const target = memoryFullBackup(withOldBudget())
    const outcome = await makeFullBackup(target, backupDigest).apply(file)
    expect(outcome.added.categoryBudgets).toBe(1)
    const lines = (await target.read()).categoryBudgets
    expect(lines.find((l) => l.id === 'cb2')?.budgetId).toBe('budget-random-1')
  })

  it('الدوال نفسها: مفيش تغيير لو المعرّفات سليمة', () => {
    const clean = emptyBackupData()
    clean.budgets = [{ ...withOldBudget().budgets[0], id: '2026-08' }]
    expect(normalizeBudgetIds(clean)).toBe(clean)
    expect(pointLinesAtLiveBudgets([{ id: 'x', budgetId: '2026-08' }], clean.budgets)).toEqual([{ id: 'x', budgetId: '2026-08' }])
  })
})
