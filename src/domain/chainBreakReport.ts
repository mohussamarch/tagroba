import { isBreak, toLines, type Line } from './balanceChainCheck'
import type { StoredData } from './idRepair'

/**
 * الكسور اللي فضلت في سلسلة رصيد الكشف — جاية منين؟ (قرار المالك 2026-09-13: «حقق فيهم بعد المسح»)
 *
 * قراءة بس. لكل كسر بيوصف **السطر اللي انكسر** و**اللي قبله**: أصلهم (كشف مسجّل / بقايا
 * دفعة متراجَع عنها / من غير مصدر خالص زي اليدوي والرسائل)، وهل هما نفس اليوم، وهل للسطر
 * المكسور نسخة تانية بنفس اليوم والمبلغ والاتجاه **والرصيد** (بصمة مكرر كامل).
 *
 * لو اتبعتت `statusOf` (من خطة التنظيف): حالة السطر المكسور **ونسخته** في التنظيف —
 * عشان يبان ليه مكرر واضح ما اتمسحش (مثلًا متربط بشخص).
 * بيرجّع أعداد مجمّعة بس — مفيش مبالغ ولا أوصاف ولا معرّفات.
 */

export type LineOrigin = 'statement' | 'revertedLeftover' | 'noSource'

/** حالة السطر في خطة التنظيف. */
export type CleanupStatus = 'willDelete' | 'chainUnconfirmed' | 'noTwin' | 'linked' | 'notLeftover'

export interface BreakGroup {
  origin: LineOrigin
  previousOrigin: LineOrigin | null
  /** نوع ويوم الدفعة للسطر المكسور لو من كشف مسجّل. */
  batch: string | null
  sameDayAsPrevious: boolean
  hasExactTwin: boolean
  /** حالة السطر المكسور في التنظيف؛ null لو التقرير اتطلب من غير خطة. */
  cleanupStatus: CleanupStatus | null
  /** أصل وحالة أول نسخة مطابقة تانية؛ null لو مفيش. */
  twinOrigin: LineOrigin | null
  twinCleanupStatus: CleanupStatus | null
  count: number
}

const NEAR_BATCH_MS = 10 * 60_000

export function reportChainBreaks(
  stored: StoredData,
  redact: (text: string) => string,
  exclude: ReadonlySet<string>,
  statusOf?: (docId: string) => CleanupStatus,
): BreakGroup[] {
  const batches = new Map<string, { state: string; label: string }>()
  const revertedTimes: number[] = []
  for (const row of stored.importBatches) {
    const info = { state: String(row.data.state ?? ''), label: `${row.data.sourceType ?? ''} ${String(row.data.importedAt ?? '').slice(0, 10)}` }
    batches.set(row.docId, info)
    const damaged = redact(row.docId)
    if (damaged !== row.docId && !batches.has(damaged)) batches.set(damaged, info)
    const at = Date.parse(String(row.data.importedAt ?? ''))
    if (info.state === 'reverted' && !Number.isNaN(at)) revertedTimes.push(at)
  }

  // أول دفعة سليمة بتشاور على كل عملية
  const liveBatchOf = new Map<string, string>()
  for (const row of stored.sourceRecords) {
    const batch = typeof row.data.batchId === 'string' ? batches.get(row.data.batchId) : undefined
    const txnId = row.data.transactionId
    if (!batch || batch.state === 'reverted' || typeof txnId !== 'string' || liveBatchOf.has(txnId)) continue
    liveBatchOf.set(txnId, batch.label)
  }
  const createdAt = new Map(stored.transactions.map((row) => [row.docId, Date.parse(String(row.data.createdAt ?? ''))]))
  const origin = (docId: string): { origin: LineOrigin; batch: string | null } => {
    const batch = liveBatchOf.get(docId) ?? liveBatchOf.get(redact(docId))
    if (batch) return { origin: 'statement', batch }
    const created = createdAt.get(docId) ?? Number.NaN
    if (revertedTimes.some((at) => Math.abs(created - at) <= NEAR_BATCH_MS)) return { origin: 'revertedLeftover', batch: null }
    return { origin: 'noSource', batch: null }
  }

  const lines = toLines(stored.transactions).filter((line) => !exclude.has(line.docId))
  const signature = (line: Line) => `${line.date}|${line.amount}|${line.incoming}|${line.stated}`
  const bySignature = new Map<string, string[]>()
  for (const line of lines) bySignature.set(signature(line), [...(bySignature.get(signature(line)) ?? []), line.docId])

  const groups = new Map<string, BreakGroup>()
  for (let i = 1; i < lines.length; i++) {
    if (!isBreak(lines[i - 1], lines[i])) continue
    const line = lines[i]
    const here = origin(line.docId)
    const twin = (bySignature.get(signature(line)) ?? []).find((id) => id !== line.docId) ?? null
    const entry: BreakGroup = {
      origin: here.origin,
      previousOrigin: origin(lines[i - 1].docId).origin,
      batch: here.batch,
      sameDayAsPrevious: lines[i - 1].date === line.date,
      hasExactTwin: twin !== null,
      cleanupStatus: statusOf ? statusOf(line.docId) : null,
      twinOrigin: twin ? origin(twin).origin : null,
      twinCleanupStatus: twin && statusOf ? statusOf(twin) : null,
      count: 0,
    }
    const { count: _count, ...shape } = entry
    const key = JSON.stringify(shape)
    const found = groups.get(key) ?? entry
    found.count++
    groups.set(key, found)
  }
  return [...groups.values()].sort((a, b) => b.count - a.count)
}
