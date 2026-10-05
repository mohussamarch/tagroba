import { makeRevertImportBatch } from '../../src/application/useCases/revertImportBatch'
import { makeResumeStagedBatch } from '../../src/application/useCases/resumeStagedBatch'
import {
  MemoryTransactionRepository,
  MemorySourceRecordRepository,
  MemoryImportBatchRepository,
} from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryAllocationRepository, MemoryObligationRepository, MemorySettlementRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryUnitOfWork } from '../../src/infrastructure/memory/memorySupport'
import type { ImportBatch, Obligation, PersonAllocation, Settlement, SourceRecord, Transaction } from '../../src/domain/entities/types'
import { recordAsync } from './goldenKit'

/**
 * التراجع عن دفعة استيراد + تنظيف الدفعة المعلّقة — حالات مكتوبة بالإيد.
 * قيد spec/03: التراجع مش حذف أعمى — العملية اللي ليها سند تاني أو تسوية أو ربط بشخص بتفضل.
 * ⚠️ بيانات وهمية بالكامل. مسار «نسخة قبل الحذف» مش هنا (deps.backup غايبة) — بيتوصّل مع طبقة التخزين.
 */

function txn(id: string, date: string, amountMinor: number): Transaction {
  return {
    id, occurredAt: date, datePrecision: 'day', sourceOrder: 1,
    economicKind: 'unclassified', economicKindConfirmed: false,
    observedDirection: 'out', amountMinor, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
    isCashTagged: false, createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
  }
}

function rec(id: string, batchId: string, rowIndex: number, transactionId: string | null): SourceRecord {
  return {
    id, batchId, accountIdentity: 'حساب-تجريبي', sourceReference: `REF-${id}`,
    sourceHash: `hash-${id}`, originalRowIndex: rowIndex, rawLine: `raw-${id}`,
    transactionId, matchingState: 'new', reason: 'seed',
  }
}

function batch(id: string, importedAt: string, state: 'staged' | 'committed' | 'reverted', imported: number): ImportBatch {
  return {
    id, sourceType: 'csv_preview', fileHash: `fh-${id}`, fileName: `${id}.csv`, importedAt, state,
    counts: { total: imported, imported, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 },
  }
}

interface Seed {
  transactions: Transaction[]
  sourceRecords: SourceRecord[]
  batches: ImportBatch[]
  settlements: Settlement[]
  allocations: PersonAllocation[]
  obligations: Obligation[]
}

export async function revertFlowGolden() {
  const revertCases = []
  const resumeCases = []

  function repos(seed: Seed) {
    const txns = new MemoryTransactionRepository()
    const sources = new MemorySourceRecordRepository()
    const batches = new MemoryImportBatchRepository()
    return (async () => {
      await txns.saveMany(seed.transactions)
      await sources.saveMany(seed.sourceRecords)
      for (const b of seed.batches) await batches.save(b)
      return { txns, sources, batches }
    })()
  }

  async function runRevert(seed: Seed, action: { kind: 'plan' | 'execute' | 'history'; batchId?: string; limit?: number }) {
    revertCases.push(await recordAsync({ seed, action }, async () => {
      const { txns, sources, batches } = await repos(seed)
      // مستودعات الذاكرة في التطبيق الحالي بتتملى بالحفظ مش بالمنشئ
      const settlements = new MemorySettlementRepository()
      await settlements.saveMany(seed.settlements)
      const allocations = new MemoryAllocationRepository()
      await allocations.saveMany(seed.allocations)
      const obligations = new MemoryObligationRepository()
      await obligations.saveMany(seed.obligations)
      const revert = makeRevertImportBatch({
        txns, sources, batches, settlements, allocations, obligations,
        uow: new MemoryUnitOfWork([txns, sources, batches]),
      })
      if (action.kind === 'history') return { history: await revert.history(action.limit ?? 50) }
      if (action.kind === 'plan') return { plan: await revert.plan(action.batchId!) }
      const outcome = await revert.execute(action.batchId!)
      return {
        outcome,
        storedBatches: batches.all(),
        storedTransactionIds: txns.all().map((t) => t.id),
        storedRecordIds: sources.all().map((r) => r.id),
      }
    }))
  }

  async function runResume(seed: Seed, action: { kind: 'findStaged' | 'cleanup' | 'cleanupAll'; batchId?: string; scanLimit?: number }) {
    resumeCases.push(await recordAsync({ seed, action }, async () => {
      const { txns, sources, batches } = await repos(seed)
      const resume = makeResumeStagedBatch({ txns, sources, batches, scanLimit: action.scanLimit })
      if (action.kind === 'findStaged') return { staged: await resume.findStaged() }
      const result = action.kind === 'cleanup' ? { outcome: await resume.cleanup(action.batchId!) } : { outcomes: await resume.cleanupAll() }
      return {
        ...result,
        storedBatches: batches.all(),
        storedTransactionIds: txns.all().map((t) => t.id),
        storedRecordIds: sources.all().map((r) => r.id),
      }
    }))
  }

  const emptyRefs = { settlements: [], allocations: [], obligations: [] }

  /*
   * الدفعة b-mixed فيها أربع عمليات بأربع مصاير:
   *   t-del  ⇒ بتتمسح (مصدر واحد ومفيش روابط)
   *   t-set  ⇒ عليها تسوية
   *   t-obl  ⇒ أصل التزام لشخص
   *   t-two  ⇒ ليها سجل مصدر تاني من دفعة تانية
   */
  const mixedSeed: Seed = {
    transactions: [txn('t-del', '2026-09-05', 1000), txn('t-set', '2026-09-06', 2000), txn('t-obl', '2026-09-07', 3000), txn('t-two', '2026-09-08', 4000)],
    sourceRecords: [
      rec('s-1', 'b-mixed', 1, 't-del'),
      rec('s-2', 'b-mixed', 2, 't-set'),
      rec('s-3', 'b-mixed', 3, 't-obl'),
      rec('s-4', 'b-mixed', 4, 't-two'),
      rec('s-other', 'b-other', 1, 't-two'),
    ],
    batches: [batch('b-mixed', '2026-09-10T08:00:00.000Z', 'committed', 4), batch('b-other', '2026-09-11T08:00:00.000Z', 'committed', 1)],
    settlements: [{ id: 'st-1', transactionId: 't-set', obligationId: 'ob-x', amountMinor: 500 }],
    allocations: [],
    obligations: [{ id: 'ob-1', personId: 'p-1', originTransactionId: 't-obl', kind: 'receivable', originalMinor: 3000, currency: 'SAR' }],
  }

  await runRevert(mixedSeed, { kind: 'plan', batchId: 'b-mixed' })
  await runRevert(mixedSeed, { kind: 'execute', batchId: 'b-mixed' })
  await runRevert(mixedSeed, { kind: 'history' })

  // تخصيص شخص من غير التزام ⇒ kept_has_allocation برضه
  const allocationSeed: Seed = {
    ...mixedSeed,
    settlements: [],
    obligations: [],
    allocations: [{ id: 'al-1', transactionId: 't-set', personId: 'p-1', allocationKind: 'gift', amountMinor: 100, currency: 'SAR' }],
  }
  await runRevert(allocationSeed, { kind: 'plan', batchId: 'b-mixed' })

  // أخطاء: دفعة مش موجودة · متراجَع عنها قبل كده · محجوبة (عمليات متسجلة وولا سجل)
  await runRevert(mixedSeed, { kind: 'plan', batchId: 'b-ghost' })
  await runRevert(
    { ...mixedSeed, batches: [...mixedSeed.batches, batch('b-done', '2026-09-01T08:00:00.000Z', 'reverted', 1)], ...emptyRefs },
    { kind: 'plan', batchId: 'b-done' },
  )
  const blockedSeed: Seed = { transactions: [], sourceRecords: [], batches: [batch('b-blocked', '2026-09-12T08:00:00.000Z', 'committed', 3)], ...emptyRefs }
  await runRevert(blockedSeed, { kind: 'plan', batchId: 'b-blocked' })
  await runRevert(blockedSeed, { kind: 'execute', batchId: 'b-blocked' })

  // ─── تنظيف المعلّق ───
  const stagedSeed: Seed = {
    transactions: [txn('t-s1', '2026-09-05', 1000), txn('t-s2', '2026-09-06', 2000), txn('t-keep', '2026-09-07', 3000)],
    sourceRecords: [rec('s-a', 'b-staged', 1, 't-s1'), rec('s-b', 'b-staged', 2, 't-s2'), rec('s-c', 'b-ok', 1, 't-keep')],
    batches: [batch('b-staged', '2026-09-13T08:00:00.000Z', 'staged', 2), batch('b-ok', '2026-09-12T08:00:00.000Z', 'committed', 1)],
    ...emptyRefs,
  }
  await runResume(stagedSeed, { kind: 'findStaged' })
  await runResume(stagedSeed, { kind: 'cleanup', batchId: 'b-staged' })
  await runResume(stagedSeed, { kind: 'cleanup', batchId: 'b-ok' })
  await runResume(stagedSeed, { kind: 'cleanup', batchId: 'b-ghost' })
  await runResume(
    { ...stagedSeed, batches: [...stagedSeed.batches, batch('b-staged-2', '2026-09-14T08:00:00.000Z', 'staged', 0)] },
    { kind: 'cleanupAll' },
  )
  // حد الفحص بيقص الأقدم: الدفعة المعلّقة القديمة برا النافذة
  await runResume(
    { ...stagedSeed, batches: [...stagedSeed.batches, batch('b-old-staged', '2026-01-01T08:00:00.000Z', 'staged', 0)] },
    { kind: 'findStaged', scanLimit: 2 },
  )

  return { revertImportBatch: revertCases, resumeStagedBatch: resumeCases }
}
