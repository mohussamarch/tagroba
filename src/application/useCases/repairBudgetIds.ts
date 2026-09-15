import { planBudgetIdRepair, type BudgetIdRepairPlan } from '../../domain/budgetIdRepair'
import type { IdPatch } from '../../domain/idRepair'
import type { IdRepairPort } from '../ports/IdRepairPort'
import type { RepairBackupPort } from '../ports/RepairBackupPort'
import type { Clock } from '../ports/repositories'
import { saveVerifiedBackup, type VerifiedBackup } from './verifiedBackup'

export interface BudgetIdRepairOutcome {
  written: number
  /** تعديلات اتغيرت على الخادم بعد المعاينة فاتخطت. */
  skipped: number
  backup: VerifiedBackup | null
}

const patchKey = (p: IdPatch) => `${p.group}/${p.docId}`

/**
 * RepairBudgetIds — نفس نمط أدوات الإصلاح (`migrateCategories`): المعاينة قراءة بس، والتطبيق بيقرا تاني
 * ويكتب **المعروض بس**، بعد نسخة متأكدة الحجم من المستندات اللي هتتعدل. كل التعديلات في دفعة واحدة
 * (عددها صغير)، فالميزانية وسطورها بيتغيروا مع بعض.
 */
export function makeRepairBudgetIds(deps: { port: IdRepairPort; backup: RepairBackupPort; clock: Clock }) {
  async function preview(): Promise<BudgetIdRepairPlan> {
    return planBudgetIdRepair(await deps.port.readAll())
  }

  async function apply(previewed: BudgetIdRepairPlan): Promise<BudgetIdRepairOutcome> {
    const stored = await deps.port.readAll()
    const fresh = planBudgetIdRepair(stored)
    const approved = new Map(previewed.patches.map((p) => [patchKey(p), JSON.stringify(p.fields)]))
    const patches = fresh.patches.filter((p) => approved.get(patchKey(p)) === JSON.stringify(p.fields))
    const skipped = fresh.patches.length - patches.length
    if (patches.length === 0) return { written: 0, skipped, backup: null }

    const touched = new Set(patches.map(patchKey))
    const before = Object.entries(stored).flatMap(([group, rows]) =>
      rows.filter((row) => touched.has(`${group}/${row.docId}`)).map((row) => ({ group, ...row })))
    const saved = await saveVerifiedBackup(deps.backup, deps.clock, 'before-budget-ids', before)
    const written = await deps.port.apply(patches)
    return { written, skipped, backup: saved }
  }

  return { preview, apply }
}
