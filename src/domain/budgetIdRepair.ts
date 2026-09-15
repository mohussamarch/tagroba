import type { IdPatch, StoredData } from './idRepair'

/**
 * تصليح معرّف الميزانيات القديمة **في الحساب نفسه** — موافقة المالك 2026-09-15 («صلّحه بنسخة احتياطية»).
 *
 * `setBudget` قديمًا كان بيدي الميزانية معرّف عشوائي جوه مستندها (`budgets/<مفتاح الفترة>`)،
 * وسطور سقوف التصنيفات بتشاور عليه. التصليح: `id` = مفتاح الفترة، و`budgetId` في السطور = نفس المفتاح.
 * المستند ما بيتنقلش (مساره أصلًا مفتاح الفترة)؛ الحقلين بس اللي بيتغيروا.
 */
export interface BudgetIdRepairPlan {
  patches: IdPatch[]
  budgets: number
  lines: number
}

export function planBudgetIdRepair(stored: StoredData): BudgetIdRepairPlan {
  const renamed = new Map<string, string>()
  const patches: IdPatch[] = []
  for (const row of stored.budgets) {
    const periodKey = typeof row.data.periodKey === 'string' ? row.data.periodKey : null
    // المستند لازم يكون متخزن بمفتاح فترته — غير كده مش الحالة دي وما نلمسوش
    if (!periodKey || row.docId !== periodKey || row.data.id === periodKey) continue
    if (typeof row.data.id === 'string') renamed.set(row.data.id, periodKey)
    patches.push({ group: 'budgets', docId: row.docId, fields: { id: periodKey } })
  }
  let lines = 0
  for (const row of stored.categoryBudgets) {
    const next = typeof row.data.budgetId === 'string' ? renamed.get(row.data.budgetId) : undefined
    if (!next) continue
    patches.push({ group: 'categoryBudgets', docId: row.docId, fields: { budgetId: next } })
    lines += 1
  }
  return { patches, budgets: patches.length - lines, lines }
}
