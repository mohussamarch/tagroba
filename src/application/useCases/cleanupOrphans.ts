import { BACKUP_GROUPS } from '../../domain/fullBackup'
import { planOrphanCleanup, type CleanupItem, type OrphanCleanupPlan } from '../../domain/orphanCleanup'
import type { IdRepairPort } from '../ports/IdRepairPort'
import type { RemoveDocsPort } from '../ports/RemoveDocsPort'
import type { RepairBackupPort, SavedBackup } from '../ports/RepairBackupPort'
import type { Clock } from '../ports/repositories'
import type { RepairProgress } from './repairStoredIds'

/** مستندات لكل حذف: الانقطاع يضيّع أقل شغل، وكل دفعة ذرية لوحدها. */
export const CLEANUP_CHUNK = 100

export interface CleanupOutcome {
  removed: number
  skipped: number
  backup: (SavedBackup & { fileName: string; expectedBytes: number }) | null
}

export interface CleanupDeps {
  port: IdRepairPort
  remover: RemoveDocsPort
  redact: (text: string) => string
  backup: RepairBackupPort
  clock: Clock
}

const keyOf = (item: CleanupItem) => `${item.group}/${item.docId}`

/**
 * CleanupOrphans — تنظيف بقايا دفعة متراجَع عنها (domain/orphanCleanup.ts).
 *
 * نفس بروتوكول إصلاح المعرّفات: معاينة بلا كتابة ← إعادة قراءة وقت التطبيق ←
 * حذف **اللي ظهر في المعاينة بس** ← نسخة كاملة من المستندات دي بحجم مؤكد قبل أول حذف ←
 * حذف على دفعات. الانقطاع آمن: الفحص التاني بيلاقي الباقي بس.
 */
export function makeCleanupOrphans({ port, remover, redact, backup, clock }: CleanupDeps) {
  async function preview(): Promise<OrphanCleanupPlan> {
    return planOrphanCleanup(await port.readAll(), redact)
  }

  async function apply(previewed: OrphanCleanupPlan, onProgress?: (progress: RepairProgress) => void): Promise<CleanupOutcome> {
    const stored = await port.readAll()
    const fresh = planOrphanCleanup(stored, redact)
    const approved = new Set([...previewed.transactions, ...previewed.records].map(keyOf))
    const candidates = [...fresh.transactions, ...fresh.records]
    const items = candidates.filter((item) => approved.has(keyOf(item)))
    const skipped = candidates.length - items.length
    if (items.length === 0) return { removed: 0, skipped, backup: null }

    const wanted = new Set(items.map(keyOf))
    const documents = BACKUP_GROUPS.flatMap((group) =>
      stored[group].filter((row) => wanted.has(`${group}/${row.docId}`)).map((row) => ({ group, ...row })))
    const savedAt = clock.nowIso()
    const content = JSON.stringify({ app: 'masroufy', kind: 'before-cleanup', savedAt, documents }, null, 2)
    const fileName = `masroufy-before-cleanup-${savedAt.replace(/[:.]/g, '-')}.json`
    const expectedBytes = new TextEncoder().encode(content).length
    const saved = await backup.save(fileName, content)
    if (saved.bytes !== null && saved.bytes !== expectedBytes) {
      throw new Error(`النسخة الاحتياطية اتحفظت ناقصة (${saved.bytes} من ${expectedBytes} بايت) — ما اتمسحش ولا مستند`)
    }

    let removed = 0
    onProgress?.({ written: removed, total: items.length })
    for (let i = 0; i < items.length; i += CLEANUP_CHUNK) {
      try {
        removed += await remover.remove(items.slice(i, i + CLEANUP_CHUNK))
      } catch (error) {
        const cause = error instanceof Error ? error.message : String(error)
        throw new Error(`اتمسح ${removed} من ${items.length} قبل الانقطاع (${cause})`)
      }
      onProgress?.({ written: removed, total: items.length })
    }
    return { removed, skipped, backup: { ...saved, fileName, expectedBytes } }
  }

  return { preview, apply }
}
