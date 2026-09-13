import type { BackupGroup, BackupRow } from './fullBackup'
import type { StoredData } from './idRepair'

/**
 * تنظيف بقايا دفعة متراجَع عنها — قرار المالك 2026-09-13 («ابنِ التنظيف بمعاينة»).
 *
 * التراجع القديم كان بيحذف بحقل `id` المخزن، ولما كان مقصوصًا (HANDOVER §23) الحذف
 * راح لمسار مش موجود ونجح بصمت. فاضل حاجتين:
 *   1. سجلات مصدر تبع الدفعة المتراجَع عنها — عقد التراجع إنها «تُحذف دائمًا».
 *   2. عمليات من نفس الدفعة ما اتمسحتش.
 *
 * العملية ما تتمسحش إلا بالأدلة دي **كلها**:
 *   - مفيش ولا سجل مصدر من دفعة **غير** متراجَع عنها بيشاور عليها (بمعرّفها أو شكله المقصوص)
 *   - اتعملت في حدود 10 دقايق من وقت دفعة متراجَع عنها
 *   - ليها توأم (نفس اليوم والمبلغ والاتجاه، والمحفظة متوافقة) عليه مصدر من دفعة سليمة،
 *     وعدد اللي بيتمسح لكل توأم ما يزيدش عن عدد التوائم
 *   - مفيش عليها ارتباط: شخص، التزام، تسوية، وسم، أو استثمار
 * أي عملية ناقصها دليل بتتعد وتتعرض، ومش بتتمسح.
 */

export interface CleanupItem { group: BackupGroup; docId: string }

export interface OrphanCleanupPlan {
  /** العمليات أولًا — هي اللي بتأثر على الأرقام. */
  transactions: CleanupItem[]
  records: CleanupItem[]
  /** عدد العمليات اللي هتتمسح لكل شهر («YYYY-MM» من تاريخ العملية). أعداد بس. */
  byMonth: Record<string, number>
  /** من الدفعة المتراجَع عنها وعليها ارتباط — مش هتتمسح. */
  keptLinked: number
  /** من الدفعة المتراجَع عنها بس من غير توأم كفاية — مش هتتمسح. */
  keptNoEvidence: number
}

const NEAR_BATCH_MS = 10 * 60_000

const DEPENDENTS: readonly [BackupGroup, string][] = [
  ['allocations', 'transactionId'], ['obligations', 'originTransactionId'], ['settlements', 'transactionId'],
  ['transactionTags', 'transactionId'], ['assetLots', 'transactionId'], ['assetSales', 'transactionId'],
]

export function planOrphanCleanup(stored: StoredData, redact: (text: string) => string): OrphanCleanupPlan {
  const batchState = new Map<string, string>()
  const revertedTimes: number[] = []
  for (const row of stored.importBatches) {
    const state = String(row.data.state ?? '')
    batchState.set(row.docId, state)
    const damaged = redact(row.docId)
    if (damaged !== row.docId && !batchState.has(damaged)) batchState.set(damaged, state)
    const at = Date.parse(String(row.data.importedAt ?? ''))
    if (state === 'reverted' && !Number.isNaN(at)) revertedTimes.push(at)
  }

  const records: CleanupItem[] = []
  const liveRefs = new Set<string>()
  for (const row of stored.sourceRecords) {
    const batchId = row.data.batchId
    if (typeof batchId === 'string' && batchState.get(batchId) === 'reverted') {
      records.push({ group: 'sourceRecords', docId: row.docId })
    } else if (typeof row.data.transactionId === 'string') {
      liveRefs.add(row.data.transactionId)
    }
  }

  const linked = new Set<string>()
  for (const [group, field] of DEPENDENTS) {
    for (const row of stored[group]) {
      if (typeof row.data[field] === 'string') linked.add(row.data[field] as string)
    }
  }
  const has = (set: Set<string>, docId: string) => set.has(docId) || set.has(redact(docId))
  const twinKey = (data: BackupRow) => `${data.occurredAt}|${data.amountMinor}|${data.observedDirection}`

  // التوائم المسجّلة لكل مفتاح: محافظها + كام واحد لسه متاح يقابل عملية تتمسح
  const twins = new Map<string, { wallets: (string | undefined)[]; available: number }>()
  for (const row of stored.transactions) {
    if (!has(liveRefs, row.docId)) continue
    const entry = twins.get(twinKey(row.data)) ?? { wallets: [], available: 0 }
    entry.wallets.push(row.data.walletId as string | undefined)
    entry.available++
    twins.set(twinKey(row.data), entry)
  }

  const transactions: CleanupItem[] = []
  const byMonth: Record<string, number> = {}
  let keptLinked = 0
  let keptNoEvidence = 0
  for (const row of stored.transactions) {
    if (has(liveRefs, row.docId)) continue
    const created = Date.parse(String(row.data.createdAt ?? ''))
    if (Number.isNaN(created) || !revertedTimes.some((at) => Math.abs(created - at) <= NEAR_BATCH_MS)) continue
    if (has(linked, row.docId)) { keptLinked++; continue }
    const twin = twins.get(twinKey(row.data))
    const wallet = row.data.walletId as string | undefined
    const walletFits = twin?.wallets.some((w) => w === undefined || wallet === undefined || w === wallet)
    if (!twin || twin.available === 0 || !walletFits) { keptNoEvidence++; continue }
    twin.available--
    transactions.push({ group: 'transactions', docId: row.docId })
    const month = String(row.data.occurredAt ?? '').slice(0, 7)
    byMonth[month] = (byMonth[month] ?? 0) + 1
  }

  return { transactions, records, byMonth, keptLinked, keptNoEvidence }
}
