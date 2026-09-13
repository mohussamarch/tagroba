import { BACKUP_GROUPS } from '../../domain/fullBackup'
import { statementChainBreaks, type ChainScenario } from '../../domain/balanceChainCheck'
import { reportChainBreaks, type BreakGroup, type CleanupStatus } from '../../domain/chainBreakReport'
import type { IdPatch } from '../../domain/idRepair'
import { planOrphanCleanup, type CleanupItem, type OrphanCleanupPlan } from '../../domain/orphanCleanup'
import type { IdRepairPort } from '../ports/IdRepairPort'
import type { RemoveDocsPort } from '../ports/RemoveDocsPort'
import type { RepairBackupPort } from '../ports/RepairBackupPort'
import type { Clock } from '../ports/repositories'
import type { RepairProgress } from './repairStoredIds'
import { saveVerifiedBackup, type VerifiedBackup } from './verifiedBackup'

/** مستندات لكل حذف: الانقطاع يضيّع أقل شغل، وكل دفعة ذرية لوحدها. */
export const CLEANUP_CHUNK = 100

export interface CleanupOutcome {
  removed: number
  /** ربط اتنقل من بقايا لنسختها الحقيقية قبل المسح. */
  relinked: number
  skipped: number
  backup: VerifiedBackup | null
}

/** دليل سلسلة رصيد الكشف في تلات حالات — أعداد بس (domain/balanceChainCheck.ts). */
export interface OrphanCleanupPreview extends OrphanCleanupPlan {
  chain: {
    now: ChainScenario
    afterCleanup: ChainScenario
    /** افتراض بس: لو اللي من غير توأم اتشالوا كمان. **مش بيتمسحوا.** */
    ifUnprovenRemovedToo: ChainScenario
  }
  /** الكسور اللي هتفضل بعد التنظيف — جاية منين (قرار المالك «حقق فيهم»). أعداد بس. */
  remainingBreaks: BreakGroup[]
}

export interface CleanupDeps {
  port: IdRepairPort
  remover: RemoveDocsPort
  redact: (text: string) => string
  backup: RepairBackupPort
  clock: Clock
}

const keyOf = (item: CleanupItem) => `${item.group}/${item.docId}`
const relinkKey = (patch: IdPatch) => `${patch.group}/${patch.docId}/${JSON.stringify(patch.fields)}`

/** حالة كل عملية في خطة التنظيف — للتقرير بس. «هتتمسح» بتغلب أي حالة تانية. */
function statusFrom(plan: OrphanCleanupPlan): (docId: string) => CleanupStatus {
  const status = new Map<string, CleanupStatus>()
  for (const item of plan.linked) status.set(item.docId, 'linked')
  for (const item of plan.unproven) status.set(item.docId, 'noTwin')
  for (const item of plan.chainUnconfirmed) status.set(item.docId, 'chainUnconfirmed')
  for (const item of plan.transactions) status.set(item.docId, 'willDelete')
  return (docId) => status.get(docId) ?? 'notLeftover'
}

/**
 * CleanupOrphans — تنظيف بقايا دفعة متراجَع عنها (domain/orphanCleanup.ts).
 *
 * نفس بروتوكول إصلاح المعرّفات: معاينة بلا كتابة ← إعادة قراءة وقت التطبيق ←
 * **اللي ظهر في المعاينة بس** ← نسخة كاملة بحجم مؤكد (العمليات والسجلات اللي هتتمسح
 * **ومستندات الربط قبل تعديلها**) ← نقل الربط ← الحذف على دفعات.
 * الانقطاع آمن: لو الربط اتنقل والحذف ما حصلش، الفحص التاني بيلاقي البقية مش متربطة
 * وبيمسحها بقاعدة اليوم.
 */
export function makeCleanupOrphans({ port, remover, redact, backup, clock }: CleanupDeps) {
  async function preview(): Promise<OrphanCleanupPreview> {
    const stored = await port.readAll()
    const plan = planOrphanCleanup(stored, redact)
    const cleaned = new Set(plan.transactions.map((item) => item.docId))
    const alsoUnproven = new Set([...cleaned, ...plan.unproven.map((item) => item.docId)])
    return {
      ...plan,
      chain: {
        now: statementChainBreaks(stored.transactions, new Set()),
        afterCleanup: statementChainBreaks(stored.transactions, cleaned),
        ifUnprovenRemovedToo: statementChainBreaks(stored.transactions, alsoUnproven),
      },
      remainingBreaks: reportChainBreaks(stored, redact, cleaned, statusFrom(plan)),
    }
  }

  async function apply(previewed: OrphanCleanupPlan, onProgress?: (progress: RepairProgress) => void): Promise<CleanupOutcome> {
    const stored = await port.readAll()
    const fresh = planOrphanCleanup(stored, redact)
    const approvedRelinks = new Set(previewed.relinks.map(relinkKey))
    if (fresh.relinks.some((patch) => !approvedRelinks.has(relinkKey(patch)))) {
      throw new Error('ربط العمليات اتغيّر بعد المعاينة — ما اتكتبش ولا اتمسح حاجة. افحص تاني')
    }
    const relinks = fresh.relinks
    const approved = new Set([...previewed.transactions, ...previewed.records].map(keyOf))
    const candidates = [...fresh.transactions, ...fresh.records]
    const items = candidates.filter((item) => approved.has(keyOf(item)))
    const skipped = candidates.length - items.length
    if (items.length === 0 && relinks.length === 0) return { removed: 0, relinked: 0, skipped, backup: null }

    const wanted = new Set([...items.map(keyOf), ...relinks.map((patch) => `${patch.group}/${patch.docId}`)])
    const documents = BACKUP_GROUPS.flatMap((group) =>
      stored[group].filter((row) => wanted.has(`${group}/${row.docId}`)).map((row) => ({ group, ...row })))
    const saved = await saveVerifiedBackup(backup, clock, 'before-cleanup', documents)

    const total = relinks.length + items.length
    onProgress?.({ written: 0, total })
    if (relinks.length > 0) {
      try {
        await port.apply(relinks)
      } catch (error) {
        const cause = error instanceof Error ? error.message : String(error)
        throw new Error(`نقل الربط ما كملش (${cause}) — ما اتمسحش ولا عملية`)
      }
      onProgress?.({ written: relinks.length, total })
    }

    let removed = 0
    for (let i = 0; i < items.length; i += CLEANUP_CHUNK) {
      try {
        removed += await remover.remove(items.slice(i, i + CLEANUP_CHUNK))
      } catch (error) {
        const cause = error instanceof Error ? error.message : String(error)
        throw new Error(`اتمسح ${removed} من ${items.length} قبل الانقطاع (${cause})`)
      }
      onProgress?.({ written: relinks.length + removed, total })
    }
    return { removed, relinked: relinks.length, skipped, backup: saved }
  }

  return { preview, apply }
}
