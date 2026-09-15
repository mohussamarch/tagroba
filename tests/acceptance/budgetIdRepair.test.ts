import { describe, it, expect } from 'vitest'
import { planBudgetIdRepair } from '../../src/domain/budgetIdRepair'
import { makeRepairBudgetIds } from '../../src/application/useCases/repairBudgetIds'
import { emptyStoredData } from '../../src/domain/idRepair'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { FixedClock } from '../../src/infrastructure/memory/memoryRepositories'

/** تصليح معرّف الميزانيات القديمة في الحساب — موافقة المالك 2026-09-15. */
function stored() {
  const data = emptyStoredData()
  data.budgets = [
    { docId: '2026-08', data: { id: 'budget-random-1', periodKey: '2026-08' } },
    { docId: '2026-07', data: { id: '2026-07', periodKey: '2026-07' } },
  ]
  data.categoryBudgets = [
    { docId: 'cb1', data: { id: 'cb1', budgetId: 'budget-random-1', categoryId: 'c' } },
    { docId: 'cb2', data: { id: 'cb2', budgetId: '2026-07', categoryId: 'c' } },
  ]
  return data
}

describe('تصليح معرّفات الميزانيات', () => {
  it('المعاينة: الميزانية القديمة بس وسطورها، والسليمة ما بتتلمسش', () => {
    const plan = planBudgetIdRepair(stored())
    expect(plan).toEqual({
      patches: [
        { group: 'budgets', docId: '2026-08', fields: { id: '2026-08' } },
        { group: 'categoryBudgets', docId: 'cb1', fields: { budgetId: '2026-08' } },
      ],
      budgets: 1,
      lines: 1,
    })
  })

  it('التطبيق: نسخة الأول، وبعدين الكتابة، والفحص التاني فاضي', async () => {
    const port = memoryIdRepair(stored())
    const backup = memoryRepairBackup()
    const repair = makeRepairBudgetIds({ port, backup, clock: new FixedClock('2026-09-15T00:00:00.000Z') })
    const plan = await repair.preview()
    const outcome = await repair.apply(plan)
    expect(outcome.written).toBe(2)
    expect(outcome.backup).not.toBeNull()
    const after = await port.readAll()
    expect(after.budgets.find((b) => b.docId === '2026-08')?.data.id).toBe('2026-08')
    expect(after.categoryBudgets.find((c) => c.docId === 'cb1')?.data.budgetId).toBe('2026-08')
    expect((await repair.preview()).patches).toEqual([])
  })
})
