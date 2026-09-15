import type { BackupRow, FullBackupData } from './fullBackup'

/**
 * معرّفات الميزانيات في النسخة الشاملة — اتكشف 2026-09-15 على حساب المالك.
 *
 * `setBudget` كان بيدي الميزانية معرّف عشوائي، بينما مستندها في Firestore متخزن بمفتاح الفترة،
 * والنسخة الشاملة بتشترط `id === periodKey` عشان الاستعادة ترجّع المستند بنفس المفتاح. النتيجة:
 * **أي حساب عمل سقف ما كانش يقدر يعمل نسخة شاملة**.
 *
 * التصليح هنا **في النسخة بس** (مفيش كتابة على الحساب): الميزانية القديمة بتاخد مفتاح فترتها كمعرّف،
 * وسطور سقوف التصنيفات بتتحوّل لنفس المعرّف. الميزانيات الجديدة أصلًا بمفتاح الفترة (`setBudget`).
 */
export function normalizeBudgetIds(data: FullBackupData): FullBackupData {
  const renamed = new Map<string, string>()
  const budgets = data.budgets.map((row) => {
    const periodKey = typeof row.periodKey === 'string' ? row.periodKey : null
    if (!periodKey || row.id === periodKey) return row
    if (typeof row.id === 'string') renamed.set(row.id, periodKey)
    return { ...row, id: periodKey }
  })
  if (renamed.size === 0) return data
  const categoryBudgets = data.categoryBudgets.map((row) =>
    typeof row.budgetId === 'string' && renamed.has(row.budgetId) ? { ...row, budgetId: renamed.get(row.budgetId) } : row,
  )
  return { ...data, budgets, categoryBudgets }
}

/**
 * قبل الكتابة على حساب ميزانيته لسه بالمعرّف العشوائي القديم: سطور سقوف التصنيفات المضافة
 * بترجع تشاور على **المعرّف الحقيقي المتخزن**، لأن التطبيق بيقرا السطور بـ`budget.id` — من غيرها
 * السقوف المستعادة كانت هتتحفظ ومش هتبان.
 */
export function pointLinesAtLiveBudgets(additions: readonly BackupRow[], liveBudgets: readonly BackupRow[]): BackupRow[] {
  const liveIdByPeriod = new Map<string, string>()
  for (const budget of liveBudgets) {
    if (typeof budget.periodKey === 'string' && typeof budget.id === 'string' && budget.id !== budget.periodKey) {
      liveIdByPeriod.set(budget.periodKey, budget.id)
    }
  }
  if (liveIdByPeriod.size === 0) return [...additions]
  return additions.map((row) =>
    typeof row.budgetId === 'string' && liveIdByPeriod.has(row.budgetId) ? { ...row, budgetId: liveIdByPeriod.get(row.budgetId) } : row,
  )
}
