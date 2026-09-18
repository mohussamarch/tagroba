import { judgeCandidates } from './balanceChainCheck'
import type { BackupGroup, BackupRow } from './fullBackup'
import type { IdPatch, StoredData } from './idRepair'

/**
 * تنظيف بقايا دفعة متراجَع عنها — قرار المالك 2026-09-13 («ابنِ التنظيف بمعاينة»).
 *
 * التراجع القديم كان بيحذف بحقل `id` المخزن، ولما كان مقصوصًا (HANDOVER §23) الحذف
 * راح لمسار مش موجود ونجح بصمت. فاضل حاجتين:
 *   1. سجلات مصدر تبع الدفعة المتراجَع عنها — عقد التراجع إنها «تُحذف دائمًا».
 *   2. عمليات من نفس الدفعة ما اتمسحتش.
 *
 * العملية بتتمسح بطريق من الاتنين، و**سلسلة رصيد الكشف لازم تأكد في الاتنين**
 * (رجوعها للسلسلة بيكسرها — domain/balanceChainCheck.ts):
 *   أ) **قاعدة اليوم:** مفيش سجل من دفعة سليمة بيشاور عليها، اتعملت في حدود 10 دقايق من
 *      دفعة متراجَع عنها، ليها توأم مسجّل بنفس اليوم والمبلغ والاتجاه (والعدد ما يزيدش عن
 *      التوائم، والمحفظة متوافقة)، ومفيش عليها ارتباط.
 *   ب) **نسخة مطابقة حتى في الرصيد** (OVERRIDES §22) للي قاعدة اليوم ما عدّتهاش: نفس اليوم
 *      والمبلغ والاتجاه **والرصيد المعلن** لسطر حقيقي من كشف مسجّل، واحدة قصاد واحدة.
 *      لو عليها ارتباط (شخص/التزام/تسوية/وسم/استثمار) **الربط بيتنقل لنسختها الحقيقية
 *      الأول** — إلا لو النسخة الحقيقية عليها ربط من نفس النوع (الدين كان هيتحسب مرتين)،
 *      ساعتها ما تتلمسش وتتعرض.
 * أي عملية ناقصها دليل بتتعد وتتعرض، ومش بتتمسح.
 */

export interface CleanupItem { group: BackupGroup; docId: string }

export interface OrphanCleanupPlan {
  /** العمليات أولًا — هي اللي بتأثر على الأرقام. */
  transactions: CleanupItem[]
  records: CleanupItem[]
  /** نقل ربط بقايا متربطة لنسختها الحقيقية — بيتكتب **قبل** مسح العملية. */
  relinks: IdPatch[]
  /** عدد العمليات اللي هتتمسح لكل شهر («YYYY-MM» من تاريخ العملية). أعداد بس. */
  byMonth: Record<string, number>
  /** من الدفعة المتراجَع عنها وعليها ارتباط ومش هتتمسح. */
  keptLinked: number
  /** كل البقايا المتربطة (حتى اللي ربطها هيتنقل) — للتشخيص بس. */
  linked: CleanupItem[]
  /** متربطة ليها نسخة حقيقية، بس النسخة عليها ربط من نفس النوع — مش هتتلمس. */
  relinkBlocked: CleanupItem[]
  /** ليها توأم بس رصيد الكشف ما أكدش إنها زيادة — مش هتتمسح. */
  chainUnconfirmed: CleanupItem[]
  /** من الدفعة المتراجَع عنها بس من غير دليل كفاية — مش هتتمسح. */
  keptNoEvidence: number
  /** نفس اللي فوق بمعرّفاتهم — للتشخيص بس، **مش للحذف**. */
  unproven: CleanupItem[]
  /** حكم رصيد الكشف على اللي من غير دليل — أعداد بس، **ومحدش منهم بيتمسح**. */
  unprovenVerdict: { looksDuplicate: number; looksReal: number; unclear: number }
}

const NEAR_BATCH_MS = 10 * 60_000
/** حد أمان لتكرار تثبيت حكم السلسلة (بيثبت عادةً من أول أو تاني لفة). */
const MAX_CHAIN_ROUNDS = 5

const DEPENDENTS: readonly [BackupGroup, string][] = [
  ['allocations', 'transactionId'], ['obligations', 'originTransactionId'], ['settlements', 'transactionId'],
  ['transactionTags', 'transactionId'], ['assetLots', 'transactionId'], ['assetSales', 'transactionId'],
  ['projectLinks', 'transactionId'],
]

interface Dependent { group: BackupGroup; docId: string; field: string }

/** بيثبّت حكم السلسلة على مرشحين وهما مشالين مع `alsoRemoved`. */
function confirmByChain(stored: StoredData, alsoRemoved: ReadonlySet<string>, candidates: readonly string[]): Set<string> {
  let confirmed = new Set(candidates)
  for (let round = 0; round < MAX_CHAIN_ROUNDS; round++) {
    const next = new Set(judgeCandidates(stored.transactions, new Set([...alsoRemoved, ...confirmed]), candidates).breaksChain)
    if (next.size === confirmed.size && [...next].every((id) => confirmed.has(id))) break
    confirmed = next
  }
  return confirmed
}

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

  const dependentsOf = new Map<string, Dependent[]>()
  for (const [group, field] of DEPENDENTS) {
    for (const row of stored[group]) {
      const value = row.data[field]
      if (typeof value === 'string') dependentsOf.set(value, [...(dependentsOf.get(value) ?? []), { group, docId: row.docId, field }])
    }
  }
  const has = (set: { has(key: string): boolean }, docId: string) => set.has(docId) || set.has(redact(docId))
  const depsOf = (docId: string) => [...(dependentsOf.get(docId) ?? []), ...(redact(docId) !== docId ? dependentsOf.get(redact(docId)) ?? [] : [])]
  const twinKey = (data: BackupRow) => `${data.occurredAt}|${data.amountMinor}|${data.observedDirection}`
  const exactKey = (data: BackupRow) => Number.isInteger(data.statedBalanceMinor) ? `${twinKey(data)}|${data.statedBalanceMinor}` : null

  // التوائم المسجّلة لكل مفتاح يوم: محافظها + كام واحد لسه متاح؛ وكمان أحواض النسخ المطابقة بالرصيد
  const twins = new Map<string, { wallets: (string | undefined)[]; available: number }>()
  const exactPool = new Map<string, string[]>()
  for (const row of stored.transactions) {
    if (!has(liveRefs, row.docId)) continue
    const entry = twins.get(twinKey(row.data)) ?? { wallets: [], available: 0 }
    entry.wallets.push(row.data.walletId as string | undefined)
    entry.available++
    twins.set(twinKey(row.data), entry)
    const exact = exactKey(row.data)
    if (exact) exactPool.set(exact, [...(exactPool.get(exact) ?? []), row.docId])
  }

  const twinned: CleanupItem[] = []
  const unprovenAll: CleanupItem[] = []
  const linkedItems: CleanupItem[] = []
  for (const row of stored.transactions) {
    if (has(liveRefs, row.docId)) continue
    const created = Date.parse(String(row.data.createdAt ?? ''))
    if (Number.isNaN(created) || !revertedTimes.some((at) => Math.abs(created - at) <= NEAR_BATCH_MS)) continue
    if (has(dependentsOf, row.docId)) { linkedItems.push({ group: 'transactions', docId: row.docId }); continue }
    const twin = twins.get(twinKey(row.data))
    const wallet = row.data.walletId as string | undefined
    const walletFits = twin?.wallets.some((w) => w === undefined || wallet === undefined || w === wallet)
    if (!twin || twin.available === 0 || !walletFits) { unprovenAll.push({ group: 'transactions', docId: row.docId }); continue }
    twin.available--
    twinned.push({ group: 'transactions', docId: row.docId })
  }

  // (أ) قاعدة اليوم + السلسلة
  const twinnedIds = twinned.map((item) => item.docId)
  const byDay = confirmByChain(stored, new Set(), twinnedIds)
  const rowById = new Map(stored.transactions.map((row) => [row.docId, row]))
  // اللي اتأكد بقاعدة اليوم بياخد نسخته المطابقة من الحوض، عشان نفس السطر الحقيقي ما يتحسبش توأم لاتنين
  for (const id of byDay) {
    const exact = exactKey(rowById.get(id)!.data)
    if (exact) exactPool.get(exact)?.shift()
  }

  // (ب) نسخة مطابقة حتى في الرصيد + السلسلة، للي من غير دليل وللمتربطة
  const exactTwinOf = new Map<string, string>()
  for (const item of [...unprovenAll, ...linkedItems]) {
    const exact = exactKey(rowById.get(item.docId)!.data)
    const pool = exact ? exactPool.get(exact) : undefined
    if (pool?.length) exactTwinOf.set(item.docId, pool.shift()!)
  }
  const byExact = confirmByChain(stored, byDay, [...exactTwinOf.keys()])

  const relinks: IdPatch[] = []
  const relinkBlocked: CleanupItem[] = []
  const deleteIds = new Set(byDay)
  for (const item of unprovenAll) if (byExact.has(item.docId)) deleteIds.add(item.docId)
  for (const item of linkedItems) {
    if (!byExact.has(item.docId)) continue
    const twinId = exactTwinOf.get(item.docId)!
    const moving = depsOf(item.docId)
    const twinGroups = new Set(depsOf(twinId).map((d) => d.group))
    if (moving.some((d) => twinGroups.has(d.group))) { relinkBlocked.push(item); continue }
    for (const d of moving) relinks.push({ group: d.group, docId: d.docId, fields: { [d.field]: twinId } })
    deleteIds.add(item.docId)
  }

  const transactions = stored.transactions.filter((row) => deleteIds.has(row.docId)).map((row) => ({ group: 'transactions' as const, docId: row.docId }))
  const chainUnconfirmed = twinned.filter((item) => !byDay.has(item.docId))
  const unproven = unprovenAll.filter((item) => !deleteIds.has(item.docId))
  const byMonth: Record<string, number> = {}
  for (const item of transactions) {
    const month = String(rowById.get(item.docId)?.data.occurredAt ?? '').slice(0, 7)
    byMonth[month] = (byMonth[month] ?? 0) + 1
  }

  const verdict = judgeCandidates(stored.transactions, deleteIds, unproven.map((item) => item.docId))
  return {
    transactions, records, relinks, byMonth,
    keptLinked: linkedItems.filter((item) => !deleteIds.has(item.docId)).length,
    linked: linkedItems, relinkBlocked, chainUnconfirmed,
    keptNoEvidence: unproven.length, unproven,
    unprovenVerdict: {
      looksDuplicate: verdict.breaksChain.length,
      looksReal: verdict.neededByChain.length,
      unclear: verdict.unclear.length,
    },
  }
}
