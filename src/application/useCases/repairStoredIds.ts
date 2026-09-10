import { BACKUP_GROUPS, type BackupGroup } from '../../domain/fullBackup'
import { planIdRepair, type IdRepairPlan, type StoredRow } from '../../domain/idRepair'
import type { IdRepairPort } from '../ports/IdRepairPort'

export interface IdRepairPreview {
  plan: IdRepairPlan
  /** نسخة المستندات المتأثرة **قبل** التعديل — تُحفظ على الجهاز قبل الكتابة. */
  before: (StoredRow & { group: BackupGroup })[]
  totalDocuments: number
}

/**
 * RepairStoredIds — إصلاح المعرّفات والروابط التي قصّها الحفظ القديم.
 *
 * بمعاينة بلا كتابة، ثم تطبيق **ما عُرض فقط**: يُعاد الفحص قبل الكتابة،
 * وأي تعديل لم يظهر في المعاينة بنفس القيم يُتخطى؛ فالنسخة المحفوظة
 * قبل الإصلاح تغطي كل ما يُكتب.
 */
export function makeRepairStoredIds(port: IdRepairPort, redact: (text: string) => string) {
  async function preview(): Promise<IdRepairPreview> {
    const stored = await port.readAll()
    const plan = planIdRepair(stored, redact)
    const touched = new Set(plan.patches.map((p) => `${p.group}/${p.docId}`))
    const before = BACKUP_GROUPS.flatMap((group) =>
      stored[group].filter((row) => touched.has(`${group}/${row.docId}`)).map((row) => ({ group, ...row })),
    )
    const totalDocuments = BACKUP_GROUPS.reduce((sum, group) => sum + stored[group].length, 0)
    return { plan, before, totalDocuments }
  }

  async function apply(previewed: IdRepairPlan): Promise<{ written: number; skipped: number }> {
    const fresh = planIdRepair(await port.readAll(), redact)
    const approved = new Map(previewed.patches.map((p) => [`${p.group}/${p.docId}`, JSON.stringify(p.fields)]))
    const patches = fresh.patches.filter((p) => approved.get(`${p.group}/${p.docId}`) === JSON.stringify(p.fields))
    const written = await port.apply(patches)
    return { written, skipped: fresh.patches.length - patches.length }
  }

  return { preview, apply }
}
