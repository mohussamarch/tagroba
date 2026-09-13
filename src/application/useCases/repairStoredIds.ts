import { BACKUP_GROUPS } from '../../domain/fullBackup'
import { planIdRepair, type IdPatch, type IdRepairPlan, type StoredData } from '../../domain/idRepair'
import { diagnoseLinks, type LinkDiagnosis } from '../../domain/idRepairDiagnosis'
import type { IdRepairPort } from '../ports/IdRepairPort'
import type { RepairBackupPort } from '../ports/RepairBackupPort'
import type { Clock } from '../ports/repositories'
import { saveVerifiedBackup, type VerifiedBackup } from './verifiedBackup'

export interface IdRepairPreview {
  plan: IdRepairPlan
  /** الروابط اللي ما تترجعش بيقين والعمليات من غير مصدر — أعداد بس. */
  diagnosis: LinkDiagnosis
  totalDocuments: number
}

export interface RepairProgress { written: number; total: number }

export interface RepairOutcome {
  written: number
  skipped: number
  backup: VerifiedBackup | null
}

/** مستندات لكل كتابة: صغيرة كفاية إن الانقطاع يضيّع أقل شغل، وكل دفعة ذرية لوحدها. */
export const REPAIR_CHUNK = 100

export interface RepairDeps {
  port: IdRepairPort
  redact: (text: string) => string
  backup: RepairBackupPort
  clock: Clock
}

/**
 * RepairStoredIds — إصلاح المعرّفات والروابط التي قصّها الحفظ القديم.
 *
 * بمعاينة بلا كتابة، ثم تطبيق **ما عُرض فقط**: يُعاد الفحص قبل الكتابة، وأي تعديل
 * لم يظهر في المعاينة بنفس القيم يُتخطى.
 *
 * الترتيب ملزم: نسخة المستندات اللي هتتكتب ← التأكد من حجمها على القرص ← الكتابة.
 * لو النسخة ناقصة ما يتكتبش ولا مستند.
 *
 * **قابل للاستئناف:** الكتابة على دفعات، وكل تعديل بيرجّع الحقل لقيمته الصحيحة، فلو
 * اتقطع في النص الفحص التاني بيلاقي الباقي بس — واللي اتكتب مش هيظهر تاني.
 */
export function makeRepairStoredIds({ port, redact, backup, clock }: RepairDeps) {
  async function preview(): Promise<IdRepairPreview> {
    const stored = await port.readAll()
    return {
      plan: planIdRepair(stored, redact),
      diagnosis: diagnoseLinks(stored, redact),
      totalDocuments: BACKUP_GROUPS.reduce((sum, group) => sum + stored[group].length, 0),
    }
  }

  async function apply(previewed: IdRepairPlan, onProgress?: (progress: RepairProgress) => void): Promise<RepairOutcome> {
    const stored = await port.readAll()
    const fresh = planIdRepair(stored, redact)
    const approved = new Map(previewed.patches.map((p) => [`${p.group}/${p.docId}`, JSON.stringify(p.fields)]))
    const patches = fresh.patches.filter((p) => approved.get(`${p.group}/${p.docId}`) === JSON.stringify(p.fields))
    const skipped = fresh.patches.length - patches.length
    if (patches.length === 0) return { written: 0, skipped, backup: null }

    const saved = await saveVerifiedBackup(backup, clock, 'before-repair', rowsBefore(stored, patches))

    let written = 0
    onProgress?.({ written, total: patches.length })
    for (let i = 0; i < patches.length; i += REPAIR_CHUNK) {
      try {
        written += await port.apply(patches.slice(i, i + REPAIR_CHUNK))
      } catch (error) {
        const cause = error instanceof Error ? error.message : String(error)
        throw new Error(`اتكتب ${written} من ${patches.length} قبل الانقطاع (${cause})`)
      }
      onProgress?.({ written, total: patches.length })
    }
    return { written, skipped, backup: saved }
  }

  return { preview, apply }
}

/** المستندات اللي هتتكتب، زي ما هي على الخادم لحظة التطبيق. */
function rowsBefore(stored: StoredData, patches: readonly IdPatch[]) {
  const touched = new Set(patches.map((p) => `${p.group}/${p.docId}`))
  return BACKUP_GROUPS.flatMap((group) =>
    stored[group].filter((row) => touched.has(`${group}/${row.docId}`)).map((row) => ({ group, ...row })))
}
