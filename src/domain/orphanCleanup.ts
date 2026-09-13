import { judgeCandidates } from './balanceChainCheck'
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
 *   - **رصيد الكشف بيأكد إنها زيادة**: رجوعها للسلسلة بيكسرها (domain/balanceChainCheck.ts).
 *     اتضاف بعد ما المعاينة على حساب المالك طلعت 18 كسر بدل 14 المتوقعين (HANDOVER §36).
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
  /** ليها توأم بس رصيد الكشف ما أكدش إنها زيادة — مش هتتمسح. */
  chainUnconfirmed: CleanupItem[]
  /** من الدفعة المتراجَع عنها بس من غير توأم كفاية — مش هتتمسح. */
  keptNoEvidence: number
  /** نفس اللي فوق بمعرّفاتهم — للتشخيص بس، **مش للحذف**. */
  unproven: CleanupItem[]
  /** حكم رصيد الكشف على اللي من غير توأم — أعداد بس، **ومحدش منهم بيتمسح**. */
  unprovenVerdict: { looksDuplicate: number; looksReal: number; unclear: number }
}

const NEAR_BATCH_MS = 10 * 60_000
/** حد أمان لتكرار تثبيت حكم السلسلة (بيثبت عادةً من أول أو تاني لفة). */
const MAX_CHAIN_ROUNDS = 5

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

  const twinned: CleanupItem[] = []
  const unproven: CleanupItem[] = []
  let keptLinked = 0
  for (const row of stored.transactions) {
    if (has(liveRefs, row.docId)) continue
    const created = Date.parse(String(row.data.createdAt ?? ''))
    if (Number.isNaN(created) || !revertedTimes.some((at) => Math.abs(created - at) <= NEAR_BATCH_MS)) continue
    if (has(linked, row.docId)) { keptLinked++; continue }
    const twin = twins.get(twinKey(row.data))
    const wallet = row.data.walletId as string | undefined
    const walletFits = twin?.wallets.some((w) => w === undefined || wallet === undefined || w === wallet)
    if (!twin || twin.available === 0 || !walletFits) { unproven.push({ group: 'transactions', docId: row.docId }); continue }
    twin.available--
    twinned.push({ group: 'transactions', docId: row.docId })
  }

  // رصيد الكشف: اللي بيتمسح لازم رجوعه للسلسلة يكسرها، والحكم بيتعاد لحد ما يثبت
  const twinnedIds = twinned.map((item) => item.docId)
  let removed = new Set(twinnedIds)
  for (let round = 0; round < MAX_CHAIN_ROUNDS; round++) {
    const next = new Set(judgeCandidates(stored.transactions, removed, twinnedIds).breaksChain)
    if (next.size === removed.size && [...next].every((id) => removed.has(id))) break
    removed = next
  }
  const transactions = twinned.filter((item) => removed.has(item.docId))
  const chainUnconfirmed = twinned.filter((item) => !removed.has(item.docId))

  const byMonth: Record<string, number> = {}
  const occurred = new Map(stored.transactions.map((row) => [row.docId, String(row.data.occurredAt ?? '')]))
  for (const item of transactions) {
    const month = (occurred.get(item.docId) ?? '').slice(0, 7)
    byMonth[month] = (byMonth[month] ?? 0) + 1
  }

  const verdict = judgeCandidates(stored.transactions, removed, unproven.map((item) => item.docId))
  return {
    transactions, records, byMonth, keptLinked, chainUnconfirmed,
    keptNoEvidence: unproven.length, unproven,
    unprovenVerdict: {
      looksDuplicate: verdict.breaksChain.length,
      looksReal: verdict.neededByChain.length,
      unclear: verdict.unclear.length,
    },
  }
}
